package com.example.bookworm.services;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import android.content.ContentResolver;
import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.io.StringReader;
import java.util.Map;
import java.util.HashMap;
import com.example.bookworm.models.TocItem;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.util.Pair;
import java.util.Collections;
import java.util.Comparator;

public class BookFileReader {
    private static final String TAG = "BookFileReader";
    private static final int CHARS_PER_PAGE = 800; // Количество символов на страницу внутри главы
    private static final int LINES_PER_PAGE = 15; // Примерное количество строк на экране

    public interface BookContentCallback {
        void onContentReady(List<String> pages);
        void onError(String error);
    }

    /**
     * Интерфейс для получения оглавления книги
     */
    public interface TocCallback {
        void onTocReady(List<TocItem> tocItems);
        void onError(String error);
    }
    
    /**
     * Читает EPUB-файл и возвращает его содержимое
     * Извлекает все HTML/XHTML файлы из EPUB и форматирует их для отображения
     */
    private static void readEpub(Context context, Uri fileUri, BookContentCallback callback) {
        Log.d(TAG, "Reading EPUB file: " + fileUri);
        
        try {
            InputStream inputStream = null;
            
            // Проверяем схему URI и выбираем подходящий способ открытия файла
            String scheme = fileUri.getScheme();
            if ("content".equals(scheme)) {
                // Для URI с content:// схемой используем ContentResolver
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else if ("file".equals(scheme) || scheme == null) {
                // Для URI с file:// схемой или локального пути файла используем прямое открытие
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            } else {
                Log.e(TAG, "Unsupported URI scheme: " + scheme);
                callback.onError("Неподдерживаемая схема URI: " + scheme);
                return;
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open stream for: " + fileUri);
                callback.onError("Не удалось открыть файл: " + fileUri);
                return;
            }

            List<String> pages = new ArrayList<>();
            
            // Обрабатываем EPUB как ZIP-архив
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            
            // Ищем OPF-файл
            String opfFilePath = null;
            byte[] buffer = new byte[4096];
            
            // Сначала ищем container.xml, чтобы найти путь к OPF-файлу
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("META-INF/container.xml".equals(zipEntry.getName())) {
                    StringBuilder content = new StringBuilder();
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        content.append(new String(buffer, 0, len));
                    }
                    
                    opfFilePath = extractOpfPathFromContainer(content.toString());
                    break;
                }
            }
            
            // Если OPF-файл не найден, попробуем найти любой HTML/XHTML-файл
            if (opfFilePath == null) {
                Log.d(TAG, "OPF file not found, searching for HTML/XHTML files directly");
                inputStream.close();
                
                // Повторно открываем ZIP-файл
                if ("content".equals(scheme)) {
                    inputStream = context.getContentResolver().openInputStream(fileUri);
                } else {
                    String path = fileUri.getPath();
                    if (path != null) {
                        inputStream = new java.io.FileInputStream(new java.io.File(path));
                    }
                }
                
                if (inputStream == null) {
                    Log.e(TAG, "Failed to reopen stream for: " + fileUri);
                    callback.onError("Не удалось повторно открыть файл: " + fileUri);
                    return;
                }
                
                zipInputStream = new ZipInputStream(inputStream);
                
                // Собираем все HTML/XHTML-файлы
                List<String> htmlFiles = new ArrayList<>();
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String name = zipEntry.getName().toLowerCase();
                    if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) {
                        StringBuilder content = new StringBuilder();
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            content.append(new String(buffer, 0, len));
                        }
                        
                        String processedContent = fixUnclosedTags(content.toString());
                        pages.addAll(splitContentIntoPages(processedContent));
                        htmlFiles.add(name);
                    }
                }
                
                if (htmlFiles.isEmpty()) {
                    Log.e(TAG, "No HTML/XHTML files found in EPUB");
                    callback.onError("В EPUB не найдены HTML/XHTML файлы");
                    return;
                }
                
                Log.d(TAG, "Processed " + htmlFiles.size() + " HTML files");
            } else {
                // Закрываем входной поток
                inputStream.close();
                
                // Повторно открываем ZIP-файл
                if ("content".equals(scheme)) {
                    inputStream = context.getContentResolver().openInputStream(fileUri);
                } else {
                    String path = fileUri.getPath();
                    if (path != null) {
                        inputStream = new java.io.FileInputStream(new java.io.File(path));
                    }
                }
                
                if (inputStream == null) {
                    Log.e(TAG, "Failed to reopen stream for: " + fileUri);
                    callback.onError("Не удалось повторно открыть файл: " + fileUri);
                    return;
                }
                
                zipInputStream = new ZipInputStream(inputStream);
                
                // Обрабатываем OPF-файл, чтобы получить упорядоченный список файлов контента
                String opfDir = "";
                int lastSlash = opfFilePath.lastIndexOf('/');
                if (lastSlash != -1) {
                    opfDir = opfFilePath.substring(0, lastSlash + 1);
                }
                
                List<String> contentFiles = new ArrayList<>();
                
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    if (zipEntry.getName().equals(opfFilePath)) {
                        StringBuilder content = new StringBuilder();
                        int len;
                        while ((len = zipInputStream.read(buffer)) > 0) {
                            content.append(new String(buffer, 0, len));
                        }
                        
                        contentFiles = extractFilesFromOpf(content.toString(), opfDir);
                        break;
                    }
                }
                
                // Закрываем входной поток
                inputStream.close();
                
                // Повторно открываем ZIP-файл для чтения контента
                if ("content".equals(scheme)) {
                    inputStream = context.getContentResolver().openInputStream(fileUri);
                } else {
                    String path = fileUri.getPath();
                    if (path != null) {
                        inputStream = new java.io.FileInputStream(new java.io.File(path));
                    }
                }
                
                if (inputStream == null) {
                    Log.e(TAG, "Failed to reopen stream for: " + fileUri);
                    callback.onError("Не удалось повторно открыть файл: " + fileUri);
                    return;
                }
                
                zipInputStream = new ZipInputStream(inputStream);
                
                // Читаем контент из файлов
                for (String contentFile : contentFiles) {
                    final String entryName = contentFile;
                    boolean found = false;
                    
                    // Сбрасываем входной поток для поиска конкретной записи
                    inputStream.close();
                    if ("content".equals(scheme)) {
                        inputStream = context.getContentResolver().openInputStream(fileUri);
                    } else {
                        String path = fileUri.getPath();
                        if (path != null) {
                            inputStream = new java.io.FileInputStream(new java.io.File(path));
                        }
                    }
                    
                    if (inputStream == null) {
                        Log.e(TAG, "Failed to reopen stream for: " + fileUri);
                        continue;
                    }
                    
                    zipInputStream = new ZipInputStream(inputStream);
                    
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        if (zipEntry.getName().equals(entryName)) {
                            found = true;
            StringBuilder content = new StringBuilder();
                            int len;
                            while ((len = zipInputStream.read(buffer)) > 0) {
                                content.append(new String(buffer, 0, len));
                            }
                            
                            String processedContent = fixUnclosedTags(content.toString());
                            pages.addAll(splitContentIntoPages(processedContent));
                            break;
                        }
                    }
                    
                    if (!found) {
                        Log.w(TAG, "Content file not found: " + entryName);
                    }
                }
            }
            
            // Закрываем входной поток
            zipInputStream.close();
            
            // Проверяем, что у нас есть хотя бы одна страница
            if (pages.isEmpty()) {
                Log.w(TAG, "No content extracted from EPUB");
                pages.add("<p>В книге не найден текстовый контент</p>");
            }
            
            Log.d(TAG, "EPUB processed successfully. Total pages: " + pages.size());
            callback.onContentReady(pages);
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading EPUB file: " + e.getMessage(), e);
            callback.onError("Ошибка при чтении файла: " + e.getMessage());
        }
    }
    
    /**
     * Исправляет незакрытые HTML-теги в документе
     * @param html HTML-контент для исправления
     * @return исправленный HTML
     */
    private static String fixUnclosedTags(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        
        Log.d(TAG, "Fixing unclosed tags in HTML content");
        
        // Создаем стек для отслеживания открытых тегов
        Stack<String> openTags = new Stack<>();
        StringBuilder result = new StringBuilder();
        
        Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9:_-]*)([^>]*)(/?)>");
        Matcher matcher = tagPattern.matcher(html);
        
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Добавляем текст между тегами
            result.append(html.substring(lastEnd, matcher.start()));
            
            boolean isClosingTag = !matcher.group(1).isEmpty();
            boolean isSelfClosingTag = !matcher.group(4).isEmpty();
            String tagName = matcher.group(2).toLowerCase();
            
            // Добавляем тег в результат
            result.append(matcher.group(0));
            
            // Обрабатываем открывающие и закрывающие теги
            if (!isSelfClosingTag) {
                if (isClosingTag) {
                    // Это закрывающий тег
                    if (!openTags.isEmpty()) {
                        String lastOpenTag = openTags.peek();
                        
                        if (lastOpenTag.equals(tagName)) {
                            // Правильное закрытие, удаляем тег из стека
                            openTags.pop();
                        } else {
                            // Тег не соответствует последнему открытому
                            boolean foundInStack = false;
                            
                            // Проверяем, есть ли этот тег глубже в стеке
                            for (int i = openTags.size() - 1; i >= 0; i--) {
                                if (openTags.get(i).equals(tagName)) {
                                    foundInStack = true;
                                    
                                    // Закрываем все промежуточные теги
                                    for (int j = openTags.size() - 1; j >= i; j--) {
                                        String intermediate = openTags.pop();
                                        if (j > i) {
                                            // Добавляем закрывающий тег для промежуточного тега
                                            result.append("</").append(intermediate).append(">");
                                            Log.d(TAG, "Added missing closing tag: " + intermediate);
                                        }
                                    }
                                    break;
                                }
                            }
                            
                            if (!foundInStack) {
                                // Закрывающий тег, для которого нет открывающего - игнорируем
                                Log.d(TAG, "Ignoring closing tag without matching opening tag: " + tagName);
                            }
                        }
                    }
                } else if (!isVoidElement(tagName)) {
                    // Это открывающий тег для элемента, который должен иметь закрывающий тег
                    openTags.push(tagName);
                }
            }
            
            lastEnd = matcher.end();
        }
        
        // Добавляем оставшийся текст
        result.append(html.substring(lastEnd));
        
        // Закрываем все оставшиеся открытые теги
        while (!openTags.isEmpty()) {
            String tagName = openTags.pop();
            result.append("</").append(tagName).append(">");
            Log.d(TAG, "Added closing tag for unclosed tag: " + tagName);
        }
        
        // Проверяем наличие обязательных тегов html, head и body
        String resultString = result.toString();
        
        if (!resultString.contains("<html")) {
            resultString = "<html>" + resultString + "</html>";
        }
        if (!resultString.contains("<head")) {
            resultString = resultString.replace("<html>", "<html><head></head>");
        }
        if (!resultString.contains("<body")) {
            int headEndIndex = resultString.indexOf("</head>");
            if (headEndIndex != -1) {
                resultString = resultString.substring(0, headEndIndex + 7) + "<body>" + 
                       resultString.substring(headEndIndex + 7);
            } else {
                resultString = resultString.replace("</head>", "</head><body>");
            }
        }
        if (!resultString.contains("</body>")) {
            resultString = resultString.replace("</html>", "</body></html>");
        }
        
        return resultString;
    }
    
    /**
     * Проверяет, является ли HTML-элемент пустым (void element)
     * Пустые элементы - это теги, которые не требуют закрывающего тега
     */
    private static boolean isVoidElement(String tagName) {
        String[] voidElements = {
            "area", "base", "br", "col", "embed", "hr", "img", "input", 
            "link", "meta", "param", "source", "track", "wbr"
        };
        
        for (String element : voidElements) {
            if (element.equals(tagName)) {
                return true;
            }
        }
        
        return false;
    }

    private static void readFb2(Context context, Uri fileUri, BookContentCallback callback) {
        try {
            Log.d(TAG, "Opening FB2 file: " + fileUri);
            InputStream inputStream = null;
            
            // Открываем поток согласно схеме URI
            String scheme = fileUri.getScheme();
            String path = fileUri.getPath();
            
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else if ("file".equals(scheme) || scheme == null) {
                // Для прямого файлового пути
                inputStream = new java.io.FileInputStream(path);
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                callback.onError("Не удалось открыть файл");
                return;
            }

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, null);

            List<String> pages = new ArrayList<>();
            StringBuilder currentChapter = new StringBuilder();
            StringBuilder currentContent = new StringBuilder();
            String title = "";
            boolean inBody = false;
            boolean inSection = false;
            boolean inTitle = false;
            boolean inParagraph = false;
            boolean inEmphasis = false;
            boolean inStrong = false;
            int depth = 0;
            int sectionDepth = -1;
            int paraCount = 0;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        String tagName = parser.getName();
                        depth++;
                        
                        if (tagName.equals("body")) {
                    inBody = true;
                        } else if (inBody && tagName.equals("section")) {
                            inSection = true;
                            if (sectionDepth == -1) {
                                sectionDepth = depth;
                            }
                            
                            // Если это новая секция того же уровня, обрабатываем предыдущую
                            if (depth == sectionDepth && currentChapter.length() > 0) {
                                splitHtmlIntoPages(currentChapter.toString(), pages);
                                currentChapter = new StringBuilder();
                            }
                        } else if (inBody && inSection && tagName.equals("title")) {
                            inTitle = true;
                            currentContent.append("<h2>");
                        } else if (inBody && inSection && tagName.equals("p")) {
                            inParagraph = true;
                            currentContent.append("<p>");
                            paraCount++;
                        } else if (inBody && inSection && tagName.equals("emphasis")) {
                            inEmphasis = true;
                            currentContent.append("<em>");
                        } else if (inBody && inSection && tagName.equals("strong")) {
                            inStrong = true;
                            currentContent.append("<strong>");
                        } else if (inBody && inSection && tagName.equals("image")) {
                            // Обработка изображений
                            String href = "";
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                if (parser.getAttributeName(i).endsWith("href")) {
                                    href = parser.getAttributeValue(i);
                                    if (href.startsWith("#")) {
                                        href = href.substring(1);
                                    }
                                    break;
                                }
                            }
                            if (!href.isEmpty()) {
                                currentContent.append("<img src=\"" + href + "\" alt=\"image\" />");
                            }
                        } else if (inTitle && tagName.equals("p")) {
                            // Параграф внутри заголовка
                            inParagraph = true;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (inBody && inSection) {
                            String text = parser.getText().trim();
                            if (!text.isEmpty()) {
                                currentContent.append(text);
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTagName = parser.getName();
                        depth--;
                        
                        if (endTagName.equals("body")) {
                            inBody = false;
                        } else if (endTagName.equals("section")) {
                            if (depth < sectionDepth) {
                                inSection = false;
                                sectionDepth = -1;
                            }
                            
                            // Если секция закончилась, добавляем её содержимое в текущую главу
                            if (currentContent.length() > 0) {
                                currentChapter.append(currentContent);
                                currentContent = new StringBuilder();
                            }
                            
                            // Если накопилось много параграфов, разбиваем на страницы
                            if (paraCount > 20 || currentChapter.length() > CHARS_PER_PAGE * 2) {
                                splitHtmlIntoPages(currentChapter.toString(), pages);
                                currentChapter = new StringBuilder();
                                paraCount = 0;
                            }
                        } else if (endTagName.equals("title")) {
                            inTitle = false;
                            currentContent.append("</h2>");
                        } else if (endTagName.equals("p")) {
                            inParagraph = false;
                            if (inTitle) {
                                // Не закрываем параграф внутри заголовка
                            } else {
                                currentContent.append("</p>");
                            }
                        } else if (endTagName.equals("emphasis")) {
                            inEmphasis = false;
                            currentContent.append("</em>");
                        } else if (endTagName.equals("strong")) {
                            inStrong = false;
                            currentContent.append("</strong>");
                        }
                    break;
                }
                eventType = parser.next();
            }

            // Обрабатываем оставшийся контент
            if (currentContent.length() > 0) {
                currentChapter.append(currentContent);
            }
            
            // Обрабатываем последнюю главу, если она не пуста
            if (currentChapter.length() > 0) {
                splitHtmlIntoPages(currentChapter.toString(), pages);
            }

            inputStream.close();
            
            // Добавляем базовые стили для FB2
            List<String> styledPages = new ArrayList<>();
            for (String pageContent : pages) {
                styledPages.add("<style>" + 
                    "body { font-family: sans-serif; line-height: 3; }" +
                    "h2 { text-align: center; margin: 10px 0; }" +
                    "p { margin: 5px 0; text-indent: 20px; }" +
                    "em { font-style: italic; }" +
                    "strong { font-weight: bold; }" +
                    "img { max-width: 100%; height: auto; display: block; margin: 10px auto; }" +
                    "</style>" + pageContent);
            }
            
            Log.d(TAG, "FB2 processed successfully, pages: " + styledPages.size());
            callback.onContentReady(styledPages);
        } catch (Exception e) {
            Log.e(TAG, "Error reading FB2", e);
            callback.onError("Ошибка при чтении FB2 файла: " + e.getMessage());
        }
    }

    private static void readTxt(Context context, Uri fileUri, BookContentCallback callback) {
        try {
            Log.d(TAG, "Opening TXT file: " + fileUri);
            InputStream inputStream = null;
            
            // Открываем поток согласно схеме URI
            String scheme = fileUri.getScheme();
            String path = fileUri.getPath();
            
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else if ("file".equals(scheme) || scheme == null) {
                // Для прямого файлового пути
                inputStream = new java.io.FileInputStream(path);
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream");
                callback.onError("Не удалось открыть файл");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Разбиваем текст на страницы
            List<String> pages = new ArrayList<>();
            String[] paragraphs = content.toString().split("\n\n");
            StringBuilder currentPage = new StringBuilder();
            int charCount = 0;

            for (String paragraph : paragraphs) {
                // Если параграф слишком длинный, разбиваем его
                if (paragraph.length() > CHARS_PER_PAGE) {
                    String[] parts = paragraph.split("\\. ");
                    for (String part : parts) {
                        if (charCount + part.length() + 2 > CHARS_PER_PAGE) {
                            pages.add(currentPage.toString());
                            currentPage = new StringBuilder();
                            charCount = 0;
                        }
                        currentPage.append(part).append(". ");
                        charCount += part.length() + 2;
                    }
                } else {
                    if (charCount + paragraph.length() + 2 > CHARS_PER_PAGE) {
                        pages.add(currentPage.toString());
                        currentPage = new StringBuilder();
                        charCount = 0;
                    }
                    currentPage.append(paragraph).append("\n\n");
                    charCount += paragraph.length() + 2;
                }
            }

            // Добавляем последнюю страницу, если она не пуста
            if (currentPage.length() > 0) {
                pages.add(currentPage.toString());
            }

            Log.d(TAG, "TXT processed successfully, pages: " + pages.size());
            callback.onContentReady(pages);
        } catch (Exception e) {
            Log.e(TAG, "Error reading TXT", e);
            callback.onError("Ошибка при чтении TXT файла: " + e.getMessage());
        }
    }

    private static void splitChapterIntoPages(String chapterContent, List<String> pages) {
        StringBuilder currentPage = new StringBuilder();
        int charCount = 0;
        String[] paragraphs = chapterContent.split("\n");

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }

            // Проверяем, является ли параграф заголовком
            boolean isHeader = paragraph.contains("<h1") || paragraph.contains("<h2") || paragraph.contains("<h3") ||
                             paragraph.contains("class=\"chapter\"") || paragraph.contains("class=\"section\"");

            // Если это заголовок, обрабатываем его отдельно
            if (isHeader) {
                // Если текущая страница не пуста, сохраняем её
                if (currentPage.length() > 0) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                    charCount = 0;
                }

                // Извлекаем текст заголовка, сохраняя HTML-теги
                String headerText = paragraph.trim();
                // Проверяем, не превышает ли заголовок лимит символов
                if (headerText.length() >= CHARS_PER_PAGE) {
                    // Если заголовок слишком длинный, разбиваем его
                    String[] headerWords = headerText.split("\\s+");
                    StringBuilder currentHeaderPart = new StringBuilder();
                    int headerCharCount = 0;

                    for (String word : headerWords) {
                        if (headerCharCount + word.length() >= CHARS_PER_PAGE) {
                            if (currentHeaderPart.length() > 0) {
                                pages.add(currentHeaderPart.toString());
                                currentHeaderPart = new StringBuilder();
                                headerCharCount = 0;
                            }
                        }
                        currentHeaderPart.append(word).append(" ");
                        headerCharCount += word.length() + 1;
                    }
                    if (currentHeaderPart.length() > 0) {
                        pages.add(currentHeaderPart.toString());
                    }
                } else {
                    // Если заголовок короткий, просто добавляем его
                    currentPage.append(headerText);
                    pages.add(currentPage.toString());
                }
                currentPage = new StringBuilder();
                charCount = 0;
                continue;
            }

            // Для обычного текста разбиваем на слова
            String[] words = paragraph.trim().split("\\s+");
            for (String word : words) {
                // Если добавление слова превысит лимит символов, создаем новую страницу
                if (charCount + word.length() >= CHARS_PER_PAGE) {
                    if (currentPage.length() > 0) {
                        currentPage.append("</p>");
                        pages.add(currentPage.toString());
                        currentPage = new StringBuilder();
                        charCount = 0;
                    }
                }

                // Добавляем слово в текущую страницу
                if (currentPage.length() == 0) {
                    currentPage.append("<p>");
                }
                currentPage.append(word).append(" ");
                charCount += word.length() + 1; // +1 для пробела
            }

            // Закрываем параграф
            if (currentPage.length() > 0) {
                currentPage.append("</p>");
            }
        }

        // Добавляем последнюю страницу главы, если она не пуста
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
    }

    /**
     * Разделяет HTML-контент на страницы, сохраняя структуру HTML
     * @param htmlContent HTML-контент для разделения
     * @param pages список, в который будут добавлены страницы
     */
    private static void splitHtmlIntoPages(String htmlContent, List<String> pages) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Splitting HTML content of length " + htmlContent.length() + " into pages");
        
        // Максимальное количество символов на странице (примерное)
        final int MAX_PAGE_SIZE = CHARS_PER_PAGE;
        
        // Pattern для поиска HTML-элементов (тегов и их содержимого)
        // Это регулярное выражение ищет:
        // 1. HTML-теги с их атрибутами
        // 2. Текстовые узлы между тегами
        Pattern pattern = Pattern.compile("<[^>]+>|[^<]+");
        Matcher matcher = pattern.matcher(htmlContent);
        
        StringBuilder currentPage = new StringBuilder();
        int currentPageSize = 0;
        boolean isInHeadSection = false;
        boolean hasAddedHeadSection = false;
        boolean isInBodySection = false;
        
        // Храним открытые теги для закрытия страницы
        List<String> openTags = new ArrayList<>();
        
        while (matcher.find()) {
            String element = matcher.group();
            
            // Проверяем, является ли элемент тегом
            if (element.startsWith("<")) {
                // Проверим, открывающий или закрывающий тег
                boolean isStartTag = !element.startsWith("</") && !element.endsWith("/>");
                
                // Отслеживаем секции head и body
                if (element.contains("<head")) {
                    isInHeadSection = true;
                } else if (element.contains("</head>")) {
                    isInHeadSection = false;
                    hasAddedHeadSection = true;
                } else if (element.contains("<body")) {
                    isInBodySection = true;
                } else if (element.contains("</body>")) {
                    isInBodySection = false;
                }
                
                // Для открывающих тегов (не самозакрывающихся) добавляем их в стек
                if (isStartTag) {
                    String tagName = element.substring(1, element.contains(" ") ? 
                                    element.indexOf(" ") : element.indexOf(">"));
                    
                    // Добавляем только значимые структурные теги
                    if (!tagName.equals("br") && !tagName.equals("img") && 
                        !tagName.equals("input") && !tagName.equals("hr")) {
                        openTags.add(tagName);
                    }
                } 
                // Для закрывающих тегов удаляем из стека
                else if (element.startsWith("</")) {
                    String tagName = element.substring(2, element.indexOf(">"));
                    
                    // Удаляем тег из списка открытых
                    if (!openTags.isEmpty() && openTags.get(openTags.size() - 1).equals(tagName)) {
                        openTags.remove(openTags.size() - 1);
                    }
                }
                
                // HTML теги всегда добавляем к текущей странице
                currentPage.append(element);
            } 
            // Текстовый узел (не тег)
            else {
                // Игнорируем текст в секции head
                if (isInHeadSection) {
                    currentPage.append(element);
                    continue;
                }
                
                // Если текстовый элемент слишком большой, разделим его
                if (element.length() > MAX_PAGE_SIZE && isInBodySection) {
                    splitLargeElement(element, openTags, currentPage, pages, MAX_PAGE_SIZE);
                    currentPageSize = 0; // Начинаем новую страницу
                    currentPage = new StringBuilder();
                    
                    // Добавляем базовую HTML структуру
                    addHtmlStructure(currentPage, hasAddedHeadSection);
                    
                    // Открываем все незакрытые теги на новой странице
                    for (String tag : openTags) {
                        currentPage.append("<").append(tag).append(">");
                    }
                } 
                // Если текущая страница становится слишком большой, создаем новую
                else if (currentPageSize + element.length() > MAX_PAGE_SIZE && isInBodySection) {
                    // Закрываем все открытые теги перед завершением страницы
                    for (int i = openTags.size() - 1; i >= 0; i--) {
                        currentPage.append("</").append(openTags.get(i)).append(">");
                    }
                    
                    // Закрываем HTML-структуру
                    if (isInBodySection) {
                        currentPage.append("</body></html>");
                    }
                    
                    // Добавляем страницу
                    pages.add(currentPage.toString());
                    
                    // Начинаем новую страницу
                    currentPage = new StringBuilder();
                    currentPageSize = element.length();
                    
                    // Добавляем базовую HTML структуру
                    addHtmlStructure(currentPage, hasAddedHeadSection);
                    
                    // Открываем все теги на новой странице
                    for (String tag : openTags) {
                        currentPage.append("<").append(tag).append(">");
                    }
                    
                    // Добавляем текущий элемент
                    currentPage.append(element);
                } 
                else {
                    // Просто добавляем элемент к текущей странице
                    currentPage.append(element);
                    currentPageSize += element.length();
                }
            }
        }
        
        // Добавляем последнюю страницу, если она не пуста
        if (currentPage.length() > 0) {
            // Закрываем все незакрытые теги
            for (int i = openTags.size() - 1; i >= 0; i--) {
                currentPage.append("</").append(openTags.get(i)).append(">");
            }
            
            // Закрываем HTML-структуру
            if (!currentPage.toString().contains("</body>")) {
                currentPage.append("</body></html>");
            }
            
            pages.add(currentPage.toString());
        }
        
        Log.d(TAG, "HTML content split into " + pages.size() + " pages");
    }
    
    /**
     * Разделяет большой текстовый элемент на несколько страниц
     */
    private static void splitLargeElement(String element, List<String> openTags, 
                                         StringBuilder currentPage, List<String> pages, 
                                         int maxPageSize) {
        // Закрываем все открытые теги перед завершением страницы
        for (int i = openTags.size() - 1; i >= 0; i--) {
            currentPage.append("</").append(openTags.get(i)).append(">");
        }
        
        // Закрываем HTML-структуру
        currentPage.append("</body></html>");
        
        // Добавляем текущую страницу
        pages.add(currentPage.toString());
        
        // Разделяем большой элемент на части
        int remaining = element.length();
        int offset = 0;
        
        while (remaining > 0) {
            // Создаем новую страницу
            StringBuilder newPage = new StringBuilder();
            
            // Добавляем базовую HTML структуру
            addHtmlStructure(newPage, true);
            
            // Открываем все теги
            for (String tag : openTags) {
                newPage.append("<").append(tag).append(">");
            }
            
            // Определяем размер части
            int partSize = Math.min(remaining, maxPageSize);
            
            // Пытаемся найти подходящую точку для разделения (конец предложения или пробел)
            if (partSize < remaining) {
                int lastPeriod = element.substring(offset, offset + partSize).lastIndexOf(". ");
                int lastSpace = element.substring(offset, offset + partSize).lastIndexOf(" ");
                
                if (lastPeriod > 0 && lastPeriod > partSize - 100) {
                    partSize = lastPeriod + 2; // +2 чтобы включить точку и пробел
                } else if (lastSpace > 0 && lastSpace > partSize - 50) {
                    partSize = lastSpace + 1; // +1 чтобы включить пробел
                }
            }
            
            // Добавляем часть элемента
            newPage.append(element.substring(offset, offset + partSize));
            
            // Закрываем все теги
            for (int i = openTags.size() - 1; i >= 0; i--) {
                newPage.append("</").append(openTags.get(i)).append(">");
            }
            
            // Закрываем HTML-структуру
            newPage.append("</body></html>");
            
            // Добавляем страницу
            pages.add(newPage.toString());
            
            // Обновляем счетчики
            offset += partSize;
            remaining -= partSize;
        }
    }
    
    /**
     * Добавляет базовую HTML структуру к странице
     */
    private static void addHtmlStructure(StringBuilder page, boolean hasStyle) {
        page.append("<!DOCTYPE html><html><head>");
        
        // Добавляем стили, если нужно
        if (!hasStyle) {
            page.append("<style>")
                .append("body { font-family: 'sans-serif'; line-height: 1.5; margin: 8px; }")
                .append("h1, h2, h3, h4, h5, h6 { text-align: center; margin: 12px 0; }")
                .append("p { text-indent: 1.5em; margin: 0.5em 0; text-align: justify; }")
                .append("img { max-width: 100%; height: auto; display: block; margin: 1em auto; }")
                .append("</style>");
        }
        
        page.append("</head><body>");
    }

    /**
     * Извлекает путь к OPF файлу из container.xml
     */
    private static String extractOpfPathFromContainer(String containerXml) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(containerXml));
            
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "rootfile".equals(parser.getName())) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if ("full-path".equals(parser.getAttributeName(i))) {
                            String opfPath = parser.getAttributeValue(i);
                            Log.d(TAG, "Found OPF path: " + opfPath);
                            return opfPath;
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing container.xml: " + e.getMessage(), e);
        }
        
        Log.w(TAG, "No OPF path found in container.xml");
        return null;
    }
    
    /**
     * Извлекает список файлов контента из OPF файла
     */
    private static List<String> extractFilesFromOpf(String opfContent, String opfDir) {
        List<String> contentFiles = new ArrayList<>();
        
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(opfContent));
            
            String spine = null;
            Map<String, String> idToHref = new HashMap<>();
            
            // Track parent element manually
            boolean inSpine = false;
            
            // Сначала проходим по manifest и собираем id -> href маппинг
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    
                    if ("item".equals(tagName)) {
                        String id = null;
                        String href = null;
                        String mediaType = null;
                        
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            String attrName = parser.getAttributeName(i);
                            String attrValue = parser.getAttributeValue(i);
                            
                            if ("id".equals(attrName)) {
                                id = attrValue;
                            } else if ("href".equals(attrName)) {
                                href = attrValue;
                            } else if ("media-type".equals(attrName)) {
                                mediaType = attrValue;
                            }
                        }
                        
                        if (id != null && href != null && 
                            (mediaType != null && (mediaType.contains("html") || mediaType.contains("xhtml")))) {
                            idToHref.put(id, href);
                            Log.d(TAG, "Found content item: " + id + " -> " + href);
                        }
                    } else if ("spine".equals(tagName)) {
                        // Mark that we're now inside spine element
                        inSpine = true;
                        
                        // Получаем toc атрибут для spine, если есть
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if ("toc".equals(parser.getAttributeName(i))) {
                                spine = parser.getAttributeValue(i);
                                break;
                            }
                        }
                    } else if ("itemref".equals(tagName) && inSpine) {
                        // Получаем idref атрибут для itemref в spine
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if ("idref".equals(parser.getAttributeName(i))) {
                                String idref = parser.getAttributeValue(i);
                                String href = idToHref.get(idref);
                                
                                if (href != null) {
                                    contentFiles.add(opfDir + href);
                                    Log.d(TAG, "Added content file from spine: " + opfDir + href);
                                }
                                break;
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    // When we exit the spine element, update our tracking flag
                    if ("spine".equals(parser.getName())) {
                        inSpine = false;
                    }
                }
                
                eventType = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing OPF file: " + e.getMessage(), e);
        }
        
        if (contentFiles.isEmpty()) {
            Log.w(TAG, "No content files found in OPF");
        } else {
            Log.d(TAG, "Found " + contentFiles.size() + " content files in OPF");
        }
        
        return contentFiles;
    }

    /**
     * Разбивает HTML-контент на страницы
     */
    private static List<String> splitContentIntoPages(String content) {
        List<String> pages = new ArrayList<>();
        
        // Максимальное количество символов на страницу
        final int CHARS_PER_PAGE = 2000;
        
        // Простейший HTML-парсер для обнаружения заголовков и разделов
        int contentLength = content.length();
        int currentPosition = 0;
        StringBuilder currentPage = new StringBuilder();
        
        // Добавляем начальный тег страницы
        currentPage.append("<div class='page'>");
        
        while (currentPosition < contentLength) {
            // Ищем позицию следующего заголовка или абзаца
            int nextHeaderPos = findNextTag(content, currentPosition, "h1", "h2", "h3", "h4", "h5", "h6");
            int nextParagraphPos = findNextTag(content, currentPosition, "p");
            int nextDivPos = findNextTag(content, currentPosition, "div");
            
            // Находим ближайший следующий элемент
            int nextElementPos = Math.min(
                nextHeaderPos == -1 ? Integer.MAX_VALUE : nextHeaderPos,
                Math.min(
                    nextParagraphPos == -1 ? Integer.MAX_VALUE : nextParagraphPos,
                    nextDivPos == -1 ? Integer.MAX_VALUE : nextDivPos
                )
            );
            
            // Если следующий элемент не найден, берем весь оставшийся контент
            if (nextElementPos == Integer.MAX_VALUE) {
                if (currentPosition < contentLength) {
                    // Добавляем оставшийся контент к текущей странице
                    currentPage.append(content.substring(currentPosition));
                }
                break;
            }
            
            // Находим конец текущего элемента
            int elementEndPos;
            if (nextHeaderPos == nextElementPos) {
                elementEndPos = findTagEnd(content, nextElementPos, "h");
            } else if (nextParagraphPos == nextElementPos) {
                elementEndPos = findTagEnd(content, nextElementPos, "p");
            } else {
                elementEndPos = findTagEnd(content, nextElementPos, "div");
            }
            
            if (elementEndPos == -1) {
                // Если конец тега не найден, берем следующие 100 символов или до конца
                elementEndPos = Math.min(nextElementPos + 100, contentLength);
            }
            
            String element = content.substring(nextElementPos, elementEndPos);
            
            // Проверяем, не превысит ли длину страницы добавление этого элемента
            if (currentPage.length() + element.length() > CHARS_PER_PAGE) {
                // Если элемент - заголовок, начинаем новую страницу и добавляем заголовок к ней
                if (nextHeaderPos == nextElementPos) {
                    // Завершаем текущую страницу
                    currentPage.append("</div>");
                    pages.add(currentPage.toString());
                    
                    // Начинаем новую страницу с заголовком
                    currentPage = new StringBuilder("<div class='page'>");
                    currentPage.append(element);
                    
                    // Обновляем текущую позицию
                    currentPosition = elementEndPos;
                } else {
                    // Если это абзац, разделяем его
                    // Завершаем текущую страницу
                    currentPage.append("</div>");
                    pages.add(currentPage.toString());
                    
                    // Начинаем новую страницу
                    currentPage = new StringBuilder("<div class='page'>");
                    currentPage.append(element);
                    
                    // Обновляем текущую позицию
                    currentPosition = elementEndPos;
                }
            } else {
                // Добавляем элемент к текущей странице
                currentPage.append(element);
                currentPosition = elementEndPos;
            }
            
            // Если текущая страница достаточно большая, завершаем ее
            if (currentPage.length() >= CHARS_PER_PAGE) {
                currentPage.append("</div>");
                pages.add(currentPage.toString());
                currentPage = new StringBuilder("<div class='page'>");
            }
        }
        
        // Добавляем последнюю страницу, если она не пуста
        if (currentPage.length() > "<div class='page'>".length()) {
            currentPage.append("</div>");
            pages.add(currentPage.toString());
        }
        
        // Если нет страниц (например, из-за пустого контента), добавляем пустую страницу
        if (pages.isEmpty()) {
            pages.add("<div class='page'><p>Пустая страница</p></div>");
        }
        
        Log.d(TAG, "Split content into " + pages.size() + " pages");
        return pages;
    }
    
    /**
     * Находит позицию следующего тега из списка
     */
    private static int findNextTag(String content, int startPos, String... tagNames) {
        int minPos = -1;
        
        for (String tagName : tagNames) {
            String openTag = "<" + tagName;
            int pos = content.indexOf(openTag, startPos);
            
            if (pos != -1 && (minPos == -1 || pos < minPos)) {
                // Проверяем, что это действительно начало тега (за ним следует пробел или >)
                if (pos + openTag.length() < content.length()) {
                    char nextChar = content.charAt(pos + openTag.length());
                    if (nextChar == ' ' || nextChar == '>' || nextChar == '\n' || nextChar == '\r' || nextChar == '\t') {
                        minPos = pos;
                    }
                }
            }
        }
        
        return minPos;
    }
    
    /**
     * Находит конец тега
     */
    private static int findTagEnd(String content, int tagStartPos, String tagNamePrefix) {
        // Находим открывающую скобку
        int openTagStartPos = content.indexOf("<", tagStartPos);
        if (openTagStartPos == -1) return -1;
        
        // Находим закрывающую скобку открывающего тега
        int openTagEndPos = content.indexOf(">", openTagStartPos);
        if (openTagEndPos == -1) return -1;
        
        // Проверяем, это самозакрывающийся тег?
        if (content.charAt(openTagEndPos - 1) == '/') {
            return openTagEndPos + 1; // Самозакрывающийся тег
        }
        
        // Извлекаем имя тега
        String tagContent = content.substring(openTagStartPos + 1, openTagEndPos);
        String tagName = tagContent.split("\\s")[0]; // Берем только имя тега, без атрибутов
        
        // Проверяем, начинается ли тег с указанного префикса
        if (!tagName.startsWith(tagNamePrefix)) {
            return openTagEndPos + 1; // Не наш тег, возвращаем конец открывающего тега
        }
        
        // Находим закрывающий тег
        String closeTag = "</" + tagName + ">";
        int closeTagPos = content.indexOf(closeTag, openTagEndPos);
        
        if (closeTagPos == -1) {
            // Закрывающий тег не найден, возвращаем конец открывающего тега
            return openTagEndPos + 1;
        }
        
        // Возвращаем позицию конца закрывающего тега
        return closeTagPos + closeTag.length();
    }

    /**
     * Выполняет поиск по тексту книги
     * @param pages Страницы книги
     * @param query Поисковый запрос
     * @return Список индексов страниц, содержащих запрос
     */
    public static List<Integer> searchInBook(List<String> pages, String query) {
        List<Integer> results = new ArrayList<>();
        
        if (pages == null || query == null || query.isEmpty()) {
            return results;
        }
        
        String lowercaseQuery = query.toLowerCase();
        
        for (int i = 0; i < pages.size(); i++) {
            String pageContent = pages.get(i);
            
            // Удаляем HTML-теги для более точного поиска
            String plainText = pageContent.replaceAll("<[^>]*>", "");
            
            if (plainText.toLowerCase().contains(lowercaseQuery)) {
                results.add(i);
            }
        }
        
        return results;
    }
    
    /**
     * Подсвечивает найденный текст на странице
     * @param pageContent Исходное содержимое страницы
     * @param query Поисковый запрос
     * @return Содержимое с подсвеченным текстом
     */
    public static String highlightSearchResults(String pageContent, String query) {
        if (pageContent == null || query == null || query.isEmpty()) {
            return pageContent;
        }
        
        // Создаем регулярное выражение для поиска, игнорируя регистр
        Pattern pattern = Pattern.compile("(" + Pattern.quote(query) + ")", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(pageContent);
        
        // Заменяем найденный текст на подсвеченный
        return matcher.replaceAll("<span style='background-color:yellow;'>$1</span>");
    }

    public static void readBookContent(Context context, Uri fileUri, BookContentCallback callback) {
        // Check if this is a network URL and needs to be downloaded
        String scheme = fileUri.getScheme();
        if ("https".equals(scheme) || "http".equals(scheme)) {
            // Run download in background thread
            new Thread(() -> {
                try {
                    // Download the file (existing download code)
                    java.io.File tempDir = new java.io.File(context.getFilesDir(), "temp_books");
                    if (!tempDir.exists()) {
                        tempDir.mkdirs();
                    }

                    String fileName = "temp_" + System.currentTimeMillis();
                    String fileExtension = getFileExtension(fileUri.toString());
                    if (fileExtension != null && !fileExtension.isEmpty()) {
                        fileName += "." + fileExtension;
                    }

                    java.io.File tempFile = new java.io.File(tempDir, fileName);

                    java.net.URL url = new java.net.URL(fileUri.toString());
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();

                    if (connection.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        try (java.io.InputStream inputStream = connection.getInputStream();
                             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }

                        // Process the downloaded file on main thread
                        Uri localUri = android.net.Uri.fromFile(tempFile);
                        processLocalFile(context, localUri, callback);
                    } else {
                        callback.onError("Failed to download file: " + connection.getResponseCode());
                    }
                } catch (Exception e) {
                    callback.onError("Error downloading file: " + e.getMessage());
                }
            }).start();
        } else {
            // Local file - process directly
            processLocalFile(context, fileUri, callback);
        }
    }

    private static void processLocalFile(Context context, Uri fileUri, BookContentCallback callback) {
        String mimeType = null;

        // Get MIME type based on URI scheme
        String scheme = fileUri.getScheme();
        if ("content".equals(scheme)) {
            try {
                mimeType = context.getContentResolver().getType(fileUri);
            } catch (Exception e) {
                Log.e(TAG, "Error getting mime type", e);
            }
        }

        // If MIME type still not determined, try by file extension
        if (mimeType == null) {
            String path = fileUri.getPath();
            if (path != null) {
                if (path.toLowerCase().endsWith(".epub")) {
                    mimeType = "application/epub+zip";
                } else if (path.toLowerCase().endsWith(".fb2")) {
                    mimeType = "application/x-fictionbook+xml";
                } else if (path.toLowerCase().endsWith(".txt")) {
                    mimeType = "text/plain";
                }
            }
        }

        try {
            if (mimeType == null) {
                callback.onError("Could not determine file type");
                return;
            }

            switch (mimeType) {
                case "application/epub+zip":
                    readEpub(context, fileUri, callback);
                    break;
                case "application/x-fictionbook+xml":
                    readFb2(context, fileUri, callback);
                    break;
                case "text/plain":
                    readTxt(context, fileUri, callback);
                    break;
                case "application/pdf":
                    callback.onError("PDF reading not supported yet");
                    break;
                default:
                    callback.onError("Unsupported file type: " + mimeType);
            }
        } catch (Exception e) {
            callback.onError("Error reading file: " + e.getMessage());
        }
    }
    /**
     * Генерирует оглавление для книжного файла
     * @param context Контекст для доступа к файлам
     * @param fileUri URI к файлу книги
     * @param callback Колбэк для возврата оглавления
     */
    public static void generateTableOfContents(Context context, Uri fileUri, TocCallback callback) {
        Log.d(TAG, "Generating table of contents for: " + fileUri);
        
        String fileExtension = getFileExtension(fileUri.toString()).toLowerCase();
        
        try {
            if (fileExtension.equals("epub")) {
                extractTocFromEpub(context, fileUri, callback);
            } else if (fileExtension.equals("fb2")) {
                extractTocFromFb2(context, fileUri, callback);
            } else if (fileExtension.equals("txt")) {
                extractTocFromTxt(context, fileUri, callback);
            } else {
                callback.onError("Неподдерживаемый формат файла для извлечения оглавления: " + fileExtension);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating table of contents: " + e.getMessage(), e);
            callback.onError("Ошибка при генерации оглавления: " + e.getMessage());
        }
    }
    
    /**
     * Извлекает оглавление из EPUB-файла
     */
    private static void extractTocFromEpub(Context context, Uri fileUri, TocCallback callback) {
        Log.d(TAG, "Extracting TOC from EPUB: " + fileUri);
        
        try {
            InputStream inputStream = null;
            
            // Проверяем схему URI и выбираем подходящий способ открытия файла
            String scheme = fileUri.getScheme();
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else if ("file".equals(scheme) || scheme == null) {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            } else {
                Log.e(TAG, "Unsupported URI scheme: " + scheme);
                callback.onError("Неподдерживаемая схема URI: " + scheme);
                return;
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open stream for TOC extraction: " + fileUri);
                callback.onError("Не удалось открыть файл для извлечения оглавления: " + fileUri);
                return;
            }

            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            
            // Сначала найдем OPF-файл через container.xml
            String opfFilePath = null;
            byte[] buffer = new byte[4096];
            
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("META-INF/container.xml".equals(zipEntry.getName())) {
            StringBuilder content = new StringBuilder();
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        content.append(new String(buffer, 0, len));
                    }
                    
                    opfFilePath = extractOpfPathFromContainer(content.toString());
                    break;
                }
            }
            
            if (opfFilePath == null) {
                Log.e(TAG, "OPF file not found in EPUB for TOC extraction");
                callback.onError("OPF-файл не найден в EPUB для извлечения оглавления");
                return;
            }
            
            // Перезапускаем поток для чтения OPF
            inputStream.close();
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to reopen stream for OPF reading: " + fileUri);
                callback.onError("Не удалось повторно открыть файл для чтения OPF: " + fileUri);
                return;
            }
            
            zipInputStream = new ZipInputStream(inputStream);
            
            // Обрабатываем OPF-файл для получения пути к TOC
            String tocPath = null;
            String opfDir = "";
            int lastSlash = opfFilePath.lastIndexOf('/');
            if (lastSlash != -1) {
                opfDir = opfFilePath.substring(0, lastSlash + 1);
            }
            
            HashMap<String, String> idToHref = new HashMap<>();
            
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(opfFilePath)) {
                    StringBuilder content = new StringBuilder();
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        content.append(new String(buffer, 0, len));
                    }
                    
                    // Ищем ncx-файл в opf
                    try {
                        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                        XmlPullParser parser = factory.newPullParser();
                        parser.setInput(new StringReader(content.toString()));
                        
                        String tocId = null;
                        
                        int eventType = parser.getEventType();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                String name = parser.getName();
                                
                                // Ищем spine с toc атрибутом
                                if ("spine".equals(name)) {
                                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                                        if ("toc".equals(parser.getAttributeName(i))) {
                                            tocId = parser.getAttributeValue(i);
                                            break;
                                        }
                                    }
                                }
                                
                                // Собираем все item из manifest для последующего поиска
                                if ("item".equals(name)) {
                                    String id = null;
                                    String href = null;
                                    
                                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                                        if ("id".equals(parser.getAttributeName(i))) {
                                            id = parser.getAttributeValue(i);
                                        } else if ("href".equals(parser.getAttributeName(i))) {
                                            href = parser.getAttributeValue(i);
                                        }
                                    }
                                    
                                    if (id != null && href != null) {
                                        idToHref.put(id, href);
                                    }
                                }
                            }
                            eventType = parser.next();
                        }
                        
                        // Если нашли tocId, получаем путь к NCX-файлу
                        if (tocId != null && idToHref.containsKey(tocId)) {
                            tocPath = opfDir + idToHref.get(tocId);
                            Log.d(TAG, "Found TOC path from spine: " + tocPath);
                        }
                        
                        // Если не нашли через spine, ищем напрямую NCX в manifest
                        if (tocPath == null) {
                            for (Map.Entry<String, String> entry : idToHref.entrySet()) {
                                if (entry.getValue().toLowerCase().endsWith(".ncx")) {
                                    tocPath = opfDir + entry.getValue();
                                    Log.d(TAG, "Found TOC path by extension: " + tocPath);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing OPF for TOC: " + e.getMessage(), e);
                    }
                    
                    break;
                }
            }
            
            if (tocPath == null) {
                Log.e(TAG, "TOC file (NCX) not found in EPUB");
                callback.onError("Файл оглавления (NCX) не найден в EPUB");
                return;
            }
            
            // Перезапускаем поток для чтения NCX
            inputStream.close();
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to reopen stream for NCX reading: " + fileUri);
                callback.onError("Не удалось повторно открыть файл для чтения NCX: " + fileUri);
                return;
            }
            
            zipInputStream = new ZipInputStream(inputStream);
            
            // Получаем список HTML файлов в правильном порядке для расчета номеров страниц
            List<String> orderedContentFiles = new ArrayList<>();
            inputStream.close();
            
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            }
            
            zipInputStream = new ZipInputStream(inputStream);
            
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(opfFilePath)) {
                    StringBuilder content = new StringBuilder();
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        content.append(new String(buffer, 0, len));
                    }
                    
                    orderedContentFiles = extractFilesFromOpf(content.toString(), opfDir);
                    break;
                }
            }
            
            // Заново открываем поток для чтения NCX
            inputStream.close();
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            }
            
            zipInputStream = new ZipInputStream(inputStream);
            
            // Строим карту соответствия файлов и номеров страниц
            HashMap<String, Integer> fileToPage = new HashMap<>();
            int currentPage = 1;  // Страницы начинаются с 1
            
            for (String contentFile : orderedContentFiles) {
                fileToPage.put(contentFile, currentPage);
                
                // Открываем новый поток для подсчета страниц в файле
                InputStream pageCountStream = null;
                if ("content".equals(scheme)) {
                    pageCountStream = context.getContentResolver().openInputStream(fileUri);
                } else {
                    String path = fileUri.getPath();
                    if (path != null) {
                        pageCountStream = new java.io.FileInputStream(new java.io.File(path));
                    }
                }
                
                if (pageCountStream != null) {
                    ZipInputStream pageZip = new ZipInputStream(pageCountStream);
                    ZipEntry pageEntry;
                    
                    while ((pageEntry = pageZip.getNextEntry()) != null) {
                        if (pageEntry.getName().equals(contentFile)) {
                            StringBuilder content = new StringBuilder();
                            int len;
                            while ((len = pageZip.read(buffer)) > 0) {
                                content.append(new String(buffer, 0, len));
                            }
                            
                            // Обработка контента и подсчет страниц
                            String processedContent = fixUnclosedTags(content.toString());
                            List<String> pagesList = splitContentIntoPages(processedContent);
                            currentPage += pagesList.size();
                            break;
                        }
                    }
                    
                    pageCountStream.close();
                }
            }
            
            // Теперь читаем NCX-файл и создаем оглавление
            List<TocItem> tocItems = new ArrayList<>();
            
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(tocPath)) {
                    StringBuilder content = new StringBuilder();
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        content.append(new String(buffer, 0, len));
                    }
                    
                    // Парсим NCX для извлечения элементов оглавления
                    try {
                        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                        XmlPullParser parser = factory.newPullParser();
                        parser.setInput(new StringReader(content.toString()));
                        
                        Stack<Integer> depthStack = new Stack<>();
                        String currentText = null;
                        String currentContent = null;
                        int currentDepth = 0;
                        
                        int eventType = parser.getEventType();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            switch (eventType) {
                                case XmlPullParser.START_TAG:
                                    String tagName = parser.getName();
                                    
                                    if ("navPoint".equals(tagName)) {
                                        currentDepth++;
                                        depthStack.push(currentDepth);
                                        
                                        // Получаем id и playOrder, если они есть
                                        String id = null;
                                        int playOrder = 0;
                                        
                                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                                            if ("id".equals(parser.getAttributeName(i))) {
                                                id = parser.getAttributeValue(i);
                                            } else if ("playOrder".equals(parser.getAttributeName(i))) {
                                                try {
                                                    playOrder = Integer.parseInt(parser.getAttributeValue(i));
                                                } catch (NumberFormatException e) {
                                                    // Игнорируем, если playOrder не число
                                                }
                                            }
                                        }
                                    } else if ("text".equals(tagName)) {
                                        currentText = "";
                                    } else if ("content".equals(tagName)) {
                                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                                            if ("src".equals(parser.getAttributeName(i))) {
                                                currentContent = parser.getAttributeValue(i);
                                                break;
                                            }
                                        }
                                    }
                                    break;
                                    
                                case XmlPullParser.TEXT:
                                    if (currentText != null) {
                                        currentText += parser.getText();
                                    }
                                    break;
                                    
                                case XmlPullParser.END_TAG:
                                    tagName = parser.getName();
                                    
                                    if ("navPoint".equals(tagName)) {
                                        // Проверяем, что у нас есть все необходимые данные
                                        if (currentText != null && currentContent != null) {
                                            // Вычисляем номер страницы
                                            int pageNumber = calculatePageNumber(currentContent, fileToPage, opfDir);
                                            
                                            // Получаем уровень вложенности
                                            int level = !depthStack.isEmpty() ? depthStack.peek() : 1;
                                            
                                            // Создаем элемент оглавления с корректным уровнем вложенности
                                            TocItem item = new TocItem(currentText.trim(), pageNumber, level, currentContent);
                                            tocItems.add(item);
                                            
                                            Log.d(TAG, "Added TOC item: " + currentText + ", page: " + pageNumber + ", level: " + level);
                                            
                                            // Сбрасываем текущие данные
                                            currentText = null;
                                            currentContent = null;
                                        }
                                        
                                        // Уменьшаем глубину и удаляем из стека
                                        if (!depthStack.isEmpty()) {
                                            depthStack.pop();
                                        }
                                        currentDepth--;
                                    } else if ("text".equals(tagName)) {
                                        // Текст прочитан полностью, ничего не делаем
                                    }
                                    break;
                            }
                            
                            eventType = parser.next();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing NCX file: " + e.getMessage(), e);
                    }
                    
                    break;
                }
            }
            
            // Если оглавление пустое, создаем простое оглавление на основе имен файлов
            if (tocItems.isEmpty()) {
                Log.d(TAG, "No TOC items found, creating simple TOC from file names");
                int index = 1;
                for (String contentFile : orderedContentFiles) {
                    // Извлекаем имя файла из пути
                    String fileName = contentFile.substring(contentFile.lastIndexOf('/') + 1);
                    // Убираем расширение
                    fileName = fileName.replaceAll("\\.[^.]*$", "");
                    // Преобразуем в читаемый формат (например, chapter_1 -> Chapter 1)
                    fileName = fileName.replace('_', ' ').replace('-', ' ');
                    
                    // Если имя начинается с цифры, добавляем "Глава"
                    if (fileName.matches("^\\d.*")) {
                        fileName = "Глава " + fileName;
                    }
                    
                    // Первая буква заглавная, остальные строчные
                    if (fileName.length() > 0) {
                        fileName = fileName.substring(0, 1).toUpperCase() + fileName.substring(1).toLowerCase();
                    }
                    
                    int pageNumber = 1;
                    if (fileToPage.containsKey(contentFile)) {
                        pageNumber = fileToPage.get(contentFile);
                    }
                    
                    tocItems.add(new TocItem(fileName, pageNumber, 1, contentFile));
                    index++;
                }
            }

            zipInputStream.close();
            
            Log.d(TAG, "TOC extraction complete. Found " + tocItems.size() + " items");
            callback.onTocReady(tocItems);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting TOC from EPUB: " + e.getMessage(), e);
            callback.onError("Ошибка при извлечении оглавления из EPUB: " + e.getMessage());
        }
    }
    
    /**
     * Вычисляет номер страницы на основе ссылки контента и карты соответствия файлов
     */
    private static int calculatePageNumber(String contentRef, HashMap<String, Integer> fileToPage, String opfDir) {
        // Убираем якорь (#) из ссылки
        String ref = contentRef;
        if (ref.contains("#")) {
            ref = ref.substring(0, ref.indexOf('#'));
        }
        
        // Пробуем найти полный путь в карте
        String fullPath = opfDir + ref;
        if (fileToPage.containsKey(fullPath)) {
            return fileToPage.get(fullPath);
        }
        
        // Если полный путь не найден, ищем по окончанию пути
        for (Map.Entry<String, Integer> entry : fileToPage.entrySet()) {
            if (entry.getKey().endsWith(ref)) {
                return entry.getValue();
            }
        }
        
        // Если не нашли, возвращаем 1
        return 1;
    }
    
    /**
     * Получает расширение файла из пути или URL
     */
    private static String getFileExtension(String path) {
        if (path == null) return "";
        
        int lastDotPosition;
        int lastSlashPosition;
        
        // Обработка URL-адресов
        if (path.startsWith("http://") || path.startsWith("https://")) {
            // Удаляем параметры запроса, если они есть
            int queryPosition = path.indexOf('?');
            if (queryPosition > 0) {
                path = path.substring(0, queryPosition);
            }
            
            // Удаляем якорь, если он есть
            int anchorPosition = path.indexOf('#');
            if (anchorPosition > 0) {
                path = path.substring(0, anchorPosition);
            }
        }
        
        lastSlashPosition = path.lastIndexOf('/');
        String fileName = (lastSlashPosition >= 0) ? path.substring(lastSlashPosition + 1) : path;
        
        lastDotPosition = fileName.lastIndexOf('.');
        if (lastDotPosition >= 0 && lastDotPosition < fileName.length() - 1) {
            return fileName.substring(lastDotPosition + 1).toLowerCase();
        }
        
        return "";
    }

    /**
     * Извлекает оглавление из FB2-файла
     */
    private static void extractTocFromFb2(Context context, Uri fileUri, TocCallback callback) {
        Log.d(TAG, "Extracting TOC from FB2: " + fileUri);
        
        try {
            InputStream inputStream = null;
            
            // Проверяем схему URI и выбираем подходящий способ открытия файла
            String scheme = fileUri.getScheme();
            if ("content".equals(scheme)) {
                inputStream = context.getContentResolver().openInputStream(fileUri);
            } else if ("file".equals(scheme) || scheme == null) {
                String path = fileUri.getPath();
                if (path != null) {
                    inputStream = new java.io.FileInputStream(new java.io.File(path));
                }
            } else {
                Log.e(TAG, "Unsupported URI scheme: " + scheme);
                callback.onError("Неподдерживаемая схема URI: " + scheme);
                return;
            }
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open stream for FB2 TOC extraction: " + fileUri);
                callback.onError("Не удалось открыть файл для извлечения оглавления: " + fileUri);
                return;
            }

            // Сначала получим содержимое файла для разбивки на страницы
            List<String> allPages = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            // Используем новый поток для чтения содержимого без блокировки
            new Thread(() -> {
                try {
                    InputStream contentStream;
                    if ("content".equals(scheme)) {
                        contentStream = context.getContentResolver().openInputStream(fileUri);
                    } else {
                        String path = fileUri.getPath();
                        contentStream = path != null
                                ? new java.io.FileInputStream(new java.io.File(path))
                                : null;
                    }
                    
                    if (contentStream != null) {
                        readFb2(context, fileUri, new BookContentCallback() {
                            @Override
                            public void onContentReady(List<String> pages) {
                                allPages.addAll(pages);
                                latch.countDown();
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Error reading FB2 content: " + error);
                                latch.countDown();
                            }
                        });
                        contentStream.close();
                    } else {
                        latch.countDown();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in FB2 content thread: " + e.getMessage());
                    latch.countDown();
                }
            }).start();
            
            // Ждем максимум 5 секунд для получения страниц
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting for FB2 content was interrupted");
            }
            
            int totalPages = allPages.size() > 0 ? allPages.size() : 50;
            
            // Прочитаем все главы из FB2 и сформируем оглавление
            List<TocItem> tocItems = new ArrayList<>();
            
            try {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                
                Stack<Integer> depthStack = new Stack<>();
                Stack<String> titleStack = new Stack<>();
            boolean inBody = false;
                boolean inSection = false;
                boolean inTitle = false;
                boolean inTitleP = false;
                boolean inBookTitle = false;
                StringBuilder titleText = new StringBuilder();
                int sectionDepth = 0;
                int currentPage = 1;
                int titleCount = 0;
                int sectionCount = 0;
                
                boolean hasTOC = false;
                
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        String tagName = parser.getName();
                        
                        if ("body".equals(tagName)) {
                    inBody = true;
                            
                            // Проверяем атрибут name="notes" в body - если есть, не считаем как основные главы
                            boolean isNotes = false;
                            for (int i = 0; i < parser.getAttributeCount(); i++) {
                                if ("name".equals(parser.getAttributeName(i)) && 
                                    "notes".equals(parser.getAttributeValue(i))) {
                                    isNotes = true;
                                    break;
                                }
                            }
                            
                            if (!isNotes) {
                                depthStack.push(1);
                                sectionDepth = 1;
                            }
                        } else if ("section".equals(tagName) && inBody) {
                            inSection = true;
                            sectionCount++;
                            sectionDepth = depthStack.isEmpty() ? 1 : depthStack.peek() + 1;
                            depthStack.push(sectionDepth);
                        } else if ("title".equals(tagName)) {
                            if (inBody || inSection) {
                                inTitle = true;
                                titleText = new StringBuilder();
                                titleCount++;
                            } else if (tagName.contains("book-title")) {
                                inBookTitle = true;
                            }
                        } else if ("p".equals(tagName) && inTitle) {
                            inTitleP = true;
                            if (titleText.length() > 0) {
                                titleText.append(" "); // Разделяем параграфы пробелами
                            }
                        }
                    } else if (eventType == XmlPullParser.TEXT) {
                        if (inTitleP || inTitle) {
                            String text = parser.getText().trim();
                            if (!text.isEmpty()) {
                                titleText.append(text);
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        String tagName = parser.getName();
                        
                        if ("body".equals(tagName)) {
                            inBody = false;
                            if (!depthStack.isEmpty()) {
                                depthStack.pop();
                            }
                        } else if ("section".equals(tagName) && inBody) {
                            inSection = false;
                            if (!depthStack.isEmpty()) {
                                depthStack.pop();
                                sectionDepth = depthStack.isEmpty() ? 1 : depthStack.peek();
                            }
                        } else if ("title".equals(tagName)) {
                            if (inTitle) {
                                inTitle = false;
                                
                                // Добавляем элемент оглавления, если заголовок не пустой
                                String title = titleText.toString().trim();
                                if (!title.isEmpty()) {
                                    int level = depthStack.isEmpty() ? 1 : depthStack.peek();
                                    
                                    // Определяем номер страницы на основе закономерности распределения страниц
                                    // и количества секций/заголовков
                                    int pageEstimate = Math.min(
                                        1 + (int)((double)titleCount / (sectionCount > 0 ? sectionCount : 1) * totalPages), 
                                        totalPages);
                                    
                                    // Убедимся, что номера страниц увеличиваются последовательно
                                    currentPage = Math.max(currentPage, pageEstimate);
                                    
                                    tocItems.add(new TocItem(title, currentPage, level));
                                    hasTOC = true;
                                    
                                    Log.d(TAG, "Added FB2 TOC item: " + title + ", page " + currentPage + 
                                        ", level " + level + ", depth stack: " + depthStack);
                                    
                                    // Увеличиваем номер страницы для следующей главы
                                    currentPage += 2; // Примерно 2 страницы на раздел
                                }
                            } else if (inBookTitle) {
                                inBookTitle = false;
                            }
                        } else if ("p".equals(tagName) && inTitleP) {
                            inTitleP = false;
                        }
                    }
                    
                    eventType = parser.next();
                }
                
                // Если оглавление пустое или содержит только один элемент, создаем простое оглавление
                if (!hasTOC || tocItems.size() <= 1) {
                    Log.d(TAG, "No meaningful FB2 TOC found, creating enhanced TOC. Original items: " + tocItems.size());
                    
                    // Попробуем проанализировать контент напрямую, чтобы найти заголовки
                    boolean foundHeadings = false;
                    
                    // Получаем содержимое страниц книги и анализируем его
                    if (allPages != null && !allPages.isEmpty()) {
                        tocItems.clear();
                        tocItems.add(new TocItem("Начало книги", 1, 1));
                        
                        // Анализируем больше страниц книги для поиска заголовков
                        int pagesToCheck = Math.min(allPages.size(), 150);
                        int chapterCount = 0;
                        
                        // Расширенные паттерны для определения заголовков в FB2
                        Pattern chapterPattern = Pattern.compile(
                            // HTML-заголовки
                            "(?i)<h[1-6][^>]*>(.+?)</h[1-6]>|" + 
                            // Жирный текст (часто используется для заголовков)
                            "<(?:strong|b)[^>]*>([^<]{5,100})</(?:strong|b)>|" +
                            // Выделенный текст (может быть заголовком)
                            "<(?:emphasis|em)[^>]*>([^<]{5,100})</(?:emphasis|em)>|" +
                            // Текст с классами заголовков
                            "<p[^>]*class=[\"'](?:title|heading|chapter|header)[\"'][^>]*>([^<]+)</p>|" +
                            // Заголовки глав
                            "(?i)<p[^>]*>\\s*(?:<[^>]+>)*\\s*(?:глава|chapter|часть|part|раздел|section)\\s+(?:\\d+|[ivxlcdmIVXLCDM]+)\\s*[.:]?.*?</p>|" +
                            // Subtitle элементы
                            "<subtitle[^>]*>(.+?)</subtitle>|" +
                            // Title элементы
                            "<title[^>]*>(.+?)</title>|" +
                            // Нумерованные заголовки
                            "<p[^>]*>\\s*(?:<[^>]+>)*\\s*(?:\\d+|[ivxlcdmIVXLCDM]+)\\s*[.:]\\s+.+?</p>"
                        );
                        
                        // Проходим по страницам
                        int interval = 1;
                        for (int i = 0; i < pagesToCheck; i += interval) {
                            // Увеличиваем интервал проверки для страниц дальше начала книги
                            if (i > 30) interval = 2;
                            if (i > 60) interval = 3;
                            if (i > 100) interval = 5;
                            
                            String content = allPages.get(i);
                            Matcher matcher = chapterPattern.matcher(content);
                            boolean foundOnThisPage = false;
                            
                            while (matcher.find()) {
                                // Извлекаем заголовок из найденного совпадения
                                String title = null;
                                for (int g = 1; g <= matcher.groupCount(); g++) {
                                    if (matcher.group(g) != null) {
                                        title = matcher.group(g).trim();
                                        break;
                                    }
                                }
                                
                                // Если группа не захватила текст, берем все совпадение и очищаем от HTML
                                if (title == null || title.isEmpty()) {
                                    title = matcher.group().replaceAll("<[^>]*>", " ").trim();
                                }
                                
                                if (title != null && !title.isEmpty()) {
                                    // Очищаем заголовок от оставшихся HTML-тегов и специальных символов
                                    title = title.replaceAll("<[^>]*>", " ")
                                               .replaceAll("&[^;]+;", " ")
                                               .replaceAll("\\s+", " ")
                                               .trim();
                                    
                                    // Проверяем длину заголовка после очистки
                                    if (title.length() > 3 && title.length() < 100) {
                                        // Если заголовок слишком длинный, обрезаем его
                                        if (title.length() > 80) {
                                            title = title.substring(0, 77) + "...";
                                        }
                                        
                                        tocItems.add(new TocItem(title, i + 1, 1));
                                        foundHeadings = true;
                                        chapterCount++;
                                        
                                        Log.d(TAG, "FB2: Добавлен заголовок: '" + title + "' на стр. " + (i + 1));
                                    }
                                    
                                    // Если уже нашли достаточно много заголовков, останавливаемся
                                    if (chapterCount >= 50) break;
                                }
                            }
                            
                            // Пытаемся найти текстовые заголовки, если не нашли HTML заголовки
                            if (!foundOnThisPage) {
                                // Разбиваем страницу на параграфы и ищем короткие строки, которые могут быть заголовками
                                String contentNoTags = content.replaceAll("<[^>]*>", " ")
                                                             .replaceAll("&[^;]+;", " ")
                                                             .replaceAll("\\s+", " ")
                                                             .trim();
                                
                                String[] paragraphs = contentNoTags.split("\\.\\s+|\\n");
                                
                                for (String para : paragraphs) {
                                    para = para.trim();
                                    
                                    // Пропускаем пустые или слишком длинные/короткие параграфы
                                    if (para.isEmpty() || para.length() < 10 || para.length() > 100) {
                    continue;
                }
                                    
                                    // Проверяем на типичные паттерны заголовков
                                    boolean isLikelyHeader = 
                                        // Строка в верхнем регистре (для русских и английских текстов)
                                        (para.equals(para.toUpperCase()) && para.length() < 60 && para.length() > 10) ||
                                        // Строка начинается с типичных маркеров глав
                                        para.matches("(?i)^\\s*(?:глава|chapter|часть|part|раздел|section)\\s+[\\dIVXLCDM]+.*") ||
                                        // Строка начинается с цифры или римской цифры с точкой/двоеточием
                                        para.matches("^\\s*[\\dIVXLCDM]+[.:]\\s+.*");
                                    
                                    if (isLikelyHeader) {
                                        // Если заголовок слишком длинный, обрезаем его
                                        if (para.length() > 80) {
                                            para = para.substring(0, 77) + "...";
                                        }
                                        
                                        // Проверяем на дубликаты
                                        boolean isDuplicate = false;
                                        for (TocItem existingItem : tocItems) {
                                            if (existingItem.getTitle().equals(para) || 
                                                Math.abs(existingItem.getPageNumber() - (i + 1)) < 2) {
                                                isDuplicate = true;
                    break;
                }
                                        }
                                        
                                        if (!isDuplicate) {
                                            tocItems.add(new TocItem(para, i + 1, 1));
                                            chapterCount++;
                                            foundHeadings = true;
                                            
                                            Log.d(TAG, "Added FB2 text header: '" + para + "' on page " + (i + 1));
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            // Ограничиваем количество заголовков
                            if (chapterCount >= 50) break;
                        }
                        
                        // Если обнаружили хотя бы несколько заголовков, считаем анализ успешным
                        if (chapterCount >= 3) {
                            Log.d(TAG, "Found " + chapterCount + " chapter headings in FB2");
                        } else {
                            // Если не нашли достаточно заголовков, создаем базовую навигацию
                            tocItems.clear();
                            tocItems.add(new TocItem("Start of the book", 1, 1));
                            
                            // Добавляем равномерно распределенные точки навигации
                            int step = Math.max(totalPages / 10, 5); // ~10 точек или через каждые 5 страниц
                            for (int page = step; page < totalPages; page += step) {
                                int percentage = Math.round((float)page / totalPages * 100);
                                tocItems.add(new TocItem("Page " + page + " (" + percentage + "%)", page, 1));
                            }
                            
                            tocItems.add(new TocItem("End of the book", totalPages, 1));
                            
                            Log.d(TAG, "No headings found in FB2, added navigation points");
                        }
                    }
                }
                
                // Возвращаем оглавление
                callback.onTocReady(tocItems);
                
        } catch (Exception e) {
                Log.e(TAG, "Error parsing FB2 for TOC: " + e.getMessage(), e);
                callback.onError("Error extracting TOC from FB2: " + e.getMessage());
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing FB2 stream: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting TOC from FB2: " + e.getMessage(), e);
            callback.onError("Error extracting TOC from FB2: " + e.getMessage());
        }
    }
    
    /**
     * Извлекает простое оглавление из TXT-файла
     */
    private static void extractTocFromTxt(Context context, Uri fileUri, TocCallback callback) {
        Log.d(TAG, "Creating TOC for TXT file: " + fileUri);
        
        try {
            // Извлекаем сырой текст и анализируем его на наличие заголовков глав
            final List<String> txtPages = new ArrayList<>();
            final List<String> rawContent = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch(1);
            
            // Читаем файл для получения сырого текста
            new Thread(() -> {
                try {
                    // Получаем страницы через стандартный метод
                    readTxt(context, fileUri, new BookContentCallback() {
                        @Override
                        public void onContentReady(List<String> pages) {
                            txtPages.addAll(pages);
                            
                            // Получаем оригинальный текст
                            InputStream contentStream = null;
                            String scheme = fileUri.getScheme();
                            
                            try {
                                if ("content".equals(scheme)) {
                                    contentStream = context.getContentResolver().openInputStream(fileUri);
                                } else {
                                    String path = fileUri.getPath();
                                    if (path != null) {
                                        contentStream = new java.io.FileInputStream(new java.io.File(path));
                                    }
                                }
                                
                                if (contentStream != null) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(contentStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                                        rawContent.add(line);
                                    }
                                    contentStream.close();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading raw TXT: " + e.getMessage(), e);
                            }
                            
                            latch.countDown();
                        }
                        
                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error reading TXT pages: " + error);
                            latch.countDown();
                        }
                    });
        } catch (Exception e) {
                    Log.e(TAG, "Error in TXT reading thread: " + e.getMessage(), e);
                    latch.countDown();
                }
            }).start();
            
            // Ждем максимум 5 секунд
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting for TXT reading was interrupted", e);
            }
            
            // Создаем элементы оглавления
            List<TocItem> tocItems = new ArrayList<>();
            int totalPages = Math.max(txtPages.size(), 1);
            
            // Анализируем текст и ищем потенциальные заголовки
            Log.d(TAG, "Starting to analyze TXT content for headings");
            
            if (!rawContent.isEmpty()) {
                // Добавляем начало книги
                tocItems.add(new TocItem("Start of the book", 1, 1));
                
                // Улучшенные паттерны для определения заголовков
                Pattern chapterPattern = Pattern.compile(
                    // Типичные обозначения глав
                    "(?i)^\\s*(chapter|part|section)\\s+([\\dIVXLCDM]+|[a-zA-Z]+).*" + 
                    // Пронумерованные заголовки
                    "|^\\s*([\\dIVXLCDM]+)[.:]\\s+.*"
                );
                
                int chapterCount = 0;
                int lastPage = 1;
                
                // Анализируем содержимое для поиска заголовков
                for (int i = 0; i < rawContent.size() && chapterCount < 50; i++) {
                    String line = rawContent.get(i).trim();
                    
                    // Пропускаем пустые строки
                    if (line.isEmpty() || line.length() < 5) {
                        continue;
                    }
                    
                    // Проверяем паттерны заголовков
                    boolean isPotentialHeader = 
                        // Паттерн совпадает с известными форматами заголовков
                        chapterPattern.matcher(line).find() ||
                        // Или короткая строка в верхнем регистре
                        (line.equals(line.toUpperCase()) && line.length() > 10 && line.length() < 60) ||
                        // Короткая строка с пустыми строками до и после
                        (line.length() < 80 && i > 0 && i < rawContent.size() - 1 && 
                         rawContent.get(i-1).trim().isEmpty() && 
                         rawContent.get(i+1).trim().isEmpty());
                    
                    if (isPotentialHeader) {
                        // Обрезаем слишком длинные заголовки
                        String title = line;
                        if (title.length() > 80) {
                            title = title.substring(0, 77) + "...";
                        }
                        
                        // Вычисляем примерную страницу
                        int page = 1 + (int)((double)i / rawContent.size() * totalPages);
                        
                        // Избегаем дублирования близких страниц
                        if (page - lastPage >= 2 || tocItems.size() <= 1) {
                            tocItems.add(new TocItem(title, page, 1));
                            chapterCount++;
                            lastPage = page;
                            
                            Log.d(TAG, "Added TXT heading: '" + title + "' on page " + page);
                        }
                    }
                }
                
                // Если заголовки не найдены, создаем простую навигацию
                if (chapterCount < 3) {
                    Log.d(TAG, "Not enough headings found in TXT, creating simple navigation");
                    
                    tocItems.clear();
                    tocItems.add(new TocItem("Start of the book", 1, 1));
                    
                    // Добавляем равномерно распределенные точки навигации
                    int step = Math.max(totalPages / 10, 5);
                    for (int page = step; page < totalPages; page += step) {
                        int percentage = Math.round((float)page / totalPages * 100);
                        tocItems.add(new TocItem("Page " + page + " (" + percentage + "%)", page, 1));
                    }
                    
                    tocItems.add(new TocItem("End of the book", totalPages, 1));
                }
            } else {
                // Если текст пустой, добавляем хотя бы базовые элементы
                tocItems.add(new TocItem("Start of the book", 1, 1));
                tocItems.add(new TocItem("End of the book", totalPages, 1));
            }
            
            Log.d(TAG, "TXT TOC ready: " + tocItems.size() + " items");
            callback.onTocReady(tocItems);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating TOC for TXT: " + e.getMessage(), e);
            callback.onError("Error creating TOC for TXT: " + e.getMessage());
        }
    }
    
    // Вспомогательный класс для хранения пары значений
    private static class Pair<F, S> {
        public final F first;
        public final S second;
        
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
} 

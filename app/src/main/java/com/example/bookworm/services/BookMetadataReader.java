package com.example.bookworm.services;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class BookMetadataReader {
    private static final String TAG = "BookMetadataReader";

    public interface MetadataCallback {
        void onMetadataReady(Map<String, String> metadata);
        void onError(String error);
    }

    public static void readMetadata(Context context, Uri fileUri, MetadataCallback callback) {
        String fileName = null;
        try (Cursor cursor = context.getContentResolver().query(fileUri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting filename", e);
        }

        if (fileName == null) {
            fileName = fileUri.getLastPathSegment();
        }

        if (fileName == null) {
            callback.onError("Не удалось определить имя файла");
            return;
        }

        Log.d(TAG, "Reading metadata for file: " + fileName);

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".epub")) {
            readEpubMetadata(context, fileUri, callback);
        } else if (lowerFileName.endsWith(".fb2")) {
            readFb2Metadata(context, fileUri, callback);
        } else if (lowerFileName.endsWith(".fb2.zip")) {
            readFb2ZipMetadata(context, fileUri, callback);
        } else if (lowerFileName.endsWith(".txt")) {
            readTxtMetadata(context, fileUri, callback);
        } else if (lowerFileName.endsWith(".pdf")) {
            readPdfMetadata(context, fileUri, callback);
        } else {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("title", getFileNameWithoutExtension(fileName));
            callback.onMetadataReady(metadata);
        }
    }

    private static String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "Unknown";
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private static void readEpubMetadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;
        ZipInputStream zipInputStream = null;

        try {
            Map<String, String> metadata = new HashMap<>();
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            zipInputStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            String opfPath = findOpfPath(zipInputStream);

            if (opfPath == null) {
                callback.onError("Не удалось найти метаданные в EPUB файле");
                return;
            }

            // Теперь читаем OPF файл
            closeQuietly(zipInputStream);
            closeQuietly(inputStream);

            inputStream = context.getContentResolver().openInputStream(fileUri);
            zipInputStream = new ZipInputStream(inputStream);

            String coverPath = null;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(opfPath)) {
                    coverPath = parseOpfFile(zipInputStream, metadata);
                    break;
                }
            }

            // Если не нашли метаданные, используем имя файла как заголовок
            if (!metadata.containsKey("title")) {
                metadata.put("title", getFileNameWithoutExtension(fileUri.getLastPathSegment()));
            }

            // Если нашли путь к обложке, читаем и сохраняем ее
            if (coverPath != null) {
                closeQuietly(zipInputStream);
                closeQuietly(inputStream);
                
                Log.d(TAG, "Found cover path: " + coverPath);
                inputStream = context.getContentResolver().openInputStream(fileUri);
                zipInputStream = new ZipInputStream(inputStream);
                
                String basePath = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
                String fullCoverPath = coverPath.startsWith("/") ? coverPath.substring(1) : basePath + coverPath;
                Log.d(TAG, "Full cover path: " + fullCoverPath);
                
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().equals(fullCoverPath)) {
                        // Извлекаем и сохраняем обложку
                        Bitmap coverBitmap = BitmapFactory.decodeStream(zipInputStream);
                        if (coverBitmap != null) {
                            try {
                                // Сохраняем обложку во временный файл
                                File coverFile = File.createTempFile("cover", ".jpg", context.getCacheDir());
                                FileOutputStream out = new FileOutputStream(coverFile);
                                coverBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                                out.flush();
                                out.close();
                                
                                // Добавляем путь к обложке в метаданные
                                metadata.put("coverPath", coverFile.getAbsolutePath());
                                Log.d(TAG, "Cover saved to: " + coverFile.getAbsolutePath());
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving cover image", e);
                            }
                        }
                        break;
                    }
                }
            }

            callback.onMetadataReady(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Error reading EPUB metadata", e);
            callback.onError("Ошибка при чтении метаданных EPUB: " + e.getMessage());
        } finally {
            closeQuietly(zipInputStream);
            closeQuietly(inputStream);
        }
    }

    private static String findOpfPath(ZipInputStream zipInputStream) throws Exception {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (entry.getName().equals("META-INF/container.xml")) {
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(zipInputStream, "UTF-8");

                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG &&
                            "rootfile".equals(parser.getName())) {
                        return parser.getAttributeValue(null, "full-path");
                    }
                    eventType = parser.next();
                }
                break;
            }
        }
        return null;
    }

    private static String parseOpfFile(ZipInputStream zipInputStream, Map<String, String> metadata) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(zipInputStream, "UTF-8");

        int eventType = parser.getEventType();
        String currentTag = null;
        StringBuilder currentValue = new StringBuilder();
        String coverId = null;
        String coverPath = null;
        Map<String, String> idMap = new HashMap<>();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = parser.getName();
                    currentValue = new StringBuilder();
                    
                    // Ищем мета-тег с ID обложки
                    if ("meta".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        String content = parser.getAttributeValue(null, "content");
                        if ("cover".equals(name) && content != null) {
                            coverId = content;
                            Log.d(TAG, "Found cover ID: " + coverId);
                        }
                    }
                    
                    // Ищем элемент с ID обложки в манифесте
                    if ("item".equals(currentTag)) {
                        String id = parser.getAttributeValue(null, "id");
                        String href = parser.getAttributeValue(null, "href");
                        if (id != null && href != null) {
                            idMap.put(id, href);
                            if (id.equals(coverId)) {
                                coverPath = href;
                                Log.d(TAG, "Found cover path from ID: " + coverPath);
                            } else if (id.contains("cover") || id.contains("Cover")) {
                                // Как запасной вариант, ищем элемент с ID, содержащим "cover"
                                coverPath = href;
                                Log.d(TAG, "Found potential cover path from ID: " + coverPath);
                            }
                        }
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (currentTag != null) {
                        currentValue.append(parser.getText());
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if (currentTag != null) {
                        String value = currentValue.toString().trim();
                        if (!value.isEmpty()) {
                            switch (currentTag) {
                                case "dc:title":
                                    metadata.put("title", value);
                                    break;
                                case "dc:creator":
                                    metadata.put("author", value);
                                    break;
                                case "dc:description":
                                    metadata.put("description", value);
                                    break;
                                case "dc:language":
                                    metadata.put("language", value);
                                    break;
                                case "dc:publisher":
                                    metadata.put("publisher", value);
                                    break;
                                case "dc:date":
                                    metadata.put("publicationDate", value);
                                    break;
                            }
                        }
                        currentTag = null;
                    }
                    break;
            }
            eventType = parser.next();
        }
        
        // Если нашли ID обложки, возвращаем путь
        if (coverId != null && idMap.containsKey(coverId)) {
            return idMap.get(coverId);
        }
        
        return coverPath;
    }

    private static void readFb2Metadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;

        try {
            Map<String, String> metadata = new HashMap<>();
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(inputStream, "UTF-8");

            int eventType = parser.getEventType();
            boolean inTitleInfo = false;
            boolean inAuthor = false;
            boolean inBinary = false;
            String binaryId = null;
            String binaryContentType = null;
            StringBuilder authorBuilder = new StringBuilder();
            StringBuilder coverDataBuilder = null;
            String currentElement = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentElement = parser.getName();
                        if ("title-info".equals(currentElement)) {
                            inTitleInfo = true;
                        } else if (inTitleInfo && "author".equals(currentElement)) {
                            inAuthor = true;
                            authorBuilder = new StringBuilder();
                        } else if ("binary".equals(currentElement)) {
                            inBinary = true;
                            binaryId = parser.getAttributeValue(null, "id");
                            binaryContentType = parser.getAttributeValue(null, "content-type");
                            
                            // Проверяем, является ли binary элемент обложкой
                            boolean isCover = (binaryId != null && (binaryId.equals("cover") || 
                                    binaryId.toLowerCase().contains("cover") || 
                                    binaryId.equals("image_0"))) &&
                                    (binaryContentType != null && binaryContentType.startsWith("image/"));
                            
                            if (isCover) {
                                Log.d(TAG, "Found potential cover image: id=" + binaryId + ", type=" + binaryContentType);
                                coverDataBuilder = new StringBuilder();
                            } else {
                                coverDataBuilder = null;
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (inTitleInfo) {
                            String text = parser.getText().trim();
                            if (!text.isEmpty()) {
                                if ("book-title".equals(currentElement)) {
                                    metadata.put("title", text);
                                } else if (inAuthor) {
                                    if ("first-name".equals(currentElement) ||
                                            "last-name".equals(currentElement) ||
                                            "middle-name".equals(currentElement)) {
                                        authorBuilder.append(text).append(" ");
                                    }
                                } else if ("annotation".equals(currentElement)) {
                                    metadata.put("description", text);
                                }
                            }
                        } else if (inBinary && coverDataBuilder != null) {
                            // Собираем Base64-данные изображения
                            coverDataBuilder.append(parser.getText().trim());
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("title-info".equals(parser.getName())) {
                            inTitleInfo = false;
                        } else if ("author".equals(parser.getName())) {
                            inAuthor = false;
                            if (authorBuilder.length() > 0) {
                                metadata.put("author", authorBuilder.toString().trim());
                            }
                        } else if ("binary".equals(parser.getName())) {
                            // Если закончили чтение элемента binary и это была обложка
                            if (inBinary && coverDataBuilder != null && coverDataBuilder.length() > 0) {
                                try {
                                    // Декодируем Base64 в байты
                                    byte[] imageBytes = android.util.Base64.decode(
                                            coverDataBuilder.toString(), android.util.Base64.DEFAULT);
                                    
                                    // Создаем Bitmap из байтов
                                    Bitmap coverBitmap = BitmapFactory.decodeByteArray(
                                            imageBytes, 0, imageBytes.length);
                                    
                                    if (coverBitmap != null) {
                                        // Сохраняем обложку во временный файл
                                        File coverFile = File.createTempFile("cover", ".jpg", context.getCacheDir());
                                        FileOutputStream out = new FileOutputStream(coverFile);
                                        String extension = binaryContentType.contains("png") ? ".png" : ".jpg";
                                        Bitmap.CompressFormat format = extension.equals(".png") ? 
                                                Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
                                        
                                        coverBitmap.compress(format, 100, out);
                                        out.flush();
                                        out.close();
                                        
                                        // Добавляем путь к обложке в метаданные
                                        metadata.put("coverPath", coverFile.getAbsolutePath());
                                        Log.d(TAG, "FB2 cover image saved to: " + coverFile.getAbsolutePath());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error saving cover image from FB2", e);
                                }
                            }
                            inBinary = false;
                            coverDataBuilder = null;
                        }
                        break;
                }
                eventType = parser.next();
            }

            // Fallback на имя файла, если нет метаданных
            if (!metadata.containsKey("title")) {
                metadata.put("title", getFileNameWithoutExtension(fileUri.getLastPathSegment()));
            }

            callback.onMetadataReady(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Error reading FB2 metadata", e);
            callback.onError("Ошибка при чтении метаданных FB2: " + e.getMessage());
        } finally {
            closeQuietly(inputStream);
        }
    }

    private static void readTxtMetadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            Map<String, String> metadata = new HashMap<>();
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            int lineCount = 0;

            // Читаем первые 20 строк для анализа
            while ((line = reader.readLine()) != null && lineCount < 20) {
                content.append(line).append("\n");
                lineCount++;
            }

            // Используем имя файла как заголовок
            metadata.put("title", getFileNameWithoutExtension(fileUri.getLastPathSegment()));

            // Добавляем размер файла как приблизительное количество страниц
            int estimatedPages = estimatePages(content.length());
            if (estimatedPages > 0) {
                metadata.put("pages", String.valueOf(estimatedPages));
            }

            callback.onMetadataReady(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Error reading TXT metadata", e);
            callback.onError("Ошибка при чтении метаданных TXT: " + e.getMessage());
        } finally {
            closeQuietly(reader);
            closeQuietly(inputStream);
        }
    }

    private static int estimatePages(long fileSize) {
        // Очень приблизительно: 40 символов на строку, 40 строк на страницу
        return (int) (fileSize / (40 * 40));
    }

    private static void readPdfMetadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;
        try {
            Map<String, String> metadata = new HashMap<>();
            inputStream = context.getContentResolver().openInputStream(fileUri);

            PDDocument document = PDDocument.load(inputStream);
            PDDocumentInformation info = document.getDocumentInformation();

            metadata.put("title", info.getTitle() != null ? info.getTitle() :
                    getFileNameWithoutExtension(fileUri.getLastPathSegment()));
            if (info.getAuthor() != null) metadata.put("author", info.getAuthor());
            if (info.getSubject() != null) metadata.put("description", info.getSubject());
            metadata.put("pages", String.valueOf(document.getNumberOfPages()));

            document.close();
            callback.onMetadataReady(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Error reading PDF metadata", e);
            callback.onError("Ошибка при чтении метаданных PDF");
        } finally {
            closeQuietly(inputStream);
        }
    }

    private static void readFb2ZipMetadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;
        ZipInputStream zipInputStream = null;

        try {
            Map<String, String> metadata = new HashMap<>();
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            zipInputStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            
            // Находим первый FB2 файл в архиве
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith(".fb2")) {
                    Log.d(TAG, "Found FB2 file in archive: " + entry.getName());
                    
                    // Распаковываем FB2 файл во временный файл
                    File tempFb2File = File.createTempFile("book", ".fb2", context.getCacheDir());
                    FileOutputStream fos = new FileOutputStream(tempFb2File);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zipInputStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    
                    // Читаем метаданные из распакованного FB2 файла
                    readFb2Metadata(context, Uri.fromFile(tempFb2File), new MetadataCallback() {
                        @Override
                        public void onMetadataReady(Map<String, String> extractedMetadata) {
                            // Передаем метаданные в исходный callback
                            callback.onMetadataReady(extractedMetadata);
                            
                            // Удаляем временный файл
                            tempFb2File.delete();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error reading FB2 from ZIP: " + error);
                            callback.onError("Ошибка при чтении FB2 из архива: " + error);
                            tempFb2File.delete();
                        }
                    });
                    
                    return; // Нашли файл, выходим
                }
            }
            
            // Если не нашли FB2 файл
            callback.onError("FB2 файл не найден в архиве");

        } catch (Exception e) {
            Log.e(TAG, "Error reading FB2.ZIP metadata", e);
            callback.onError("Ошибка при чтении FB2.ZIP: " + e.getMessage());
        } finally {
            closeQuietly(zipInputStream);
            closeQuietly(inputStream);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing stream", e);
            }
        }
    }
}
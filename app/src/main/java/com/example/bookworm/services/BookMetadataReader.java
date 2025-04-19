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

    private Context context;

    public BookMetadataReader(Context context) {
        this.context = context;
    }

    public interface MetadataCallback {
        void onMetadataReady(Map<String, String> metadata);
        void onError(String error);
    }
    
    public interface MetadataCallback2 {
        void onMetadataExtracted(String title, String author, String description, int pageCount);
        void onError(String error);
    }

    // Method that adapts the new interface to the existing implementation
    public void readMetadata(Uri fileUri, MetadataCallback2 callback) {
        readMetadata(context, fileUri, new MetadataCallback() {
            @Override
            public void onMetadataReady(Map<String, String> metadata) {
                String title = metadata.getOrDefault("title", "");
                String author = metadata.getOrDefault("author", "");
                String description = metadata.getOrDefault("description", "");
                
                // Extract page count
                int pageCount = 0;
                if (metadata.containsKey("pages")) {
                    try {
                        pageCount = Integer.parseInt(metadata.get("pages"));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing page count", e);
                    }
                }
                
                callback.onMetadataExtracted(title, author, description, pageCount);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
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
        } else if (lowerFileName.endsWith(".fb2.zip") || (lowerFileName.endsWith(".zip") && lowerFileName.contains("fb2"))) {
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
        boolean inMetadata = false;
        boolean inManifest = false;
        boolean inSpine = false;
        int pageCount = 0;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = parser.getName();
                    currentValue = new StringBuilder();
                    
                    if ("metadata".equals(currentTag)) {
                        inMetadata = true;
                    } else if ("manifest".equals(currentTag)) {
                        inManifest = true;
                    } else if ("spine".equals(currentTag)) {
                        inSpine = true;
                    }
                    
                    // Ищем мета-тег с ID обложки
                    if ("meta".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        if ("cover".equals(name)) {
                            coverId = parser.getAttributeValue(null, "content");
                        }
                    }
                    
                    // Ищем элемент с количеством страниц
                    if ("meta".equals(currentTag) && inMetadata) {
                        String name = parser.getAttributeValue(null, "name");
                        if ("page-count".equals(name)) {
                            String content = parser.getAttributeValue(null, "content");
                            try {
                                pageCount = Integer.parseInt(content);
                                metadata.put("pages", String.valueOf(pageCount));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing page count", e);
                            }
                        }
                    }
                    
                    // Ищем элемент с путем к обложке
                    if ("item".equals(currentTag) && inManifest) {
                        String id = parser.getAttributeValue(null, "id");
                        String href = parser.getAttributeValue(null, "href");
                        if (id != null && href != null) {
                            idMap.put(id, href);
                            if (coverId != null && id.equals(coverId)) {
                                coverPath = href;
                            }
                        }
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (currentValue != null) {
                        currentValue.append(parser.getText());
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if (currentValue != null && currentTag != null) {
                        String value = currentValue.toString().trim();
                        if (inMetadata) {
                            if ("dc:title".equals(currentTag)) {
                                metadata.put("title", value);
                            } else if ("dc:creator".equals(currentTag)) {
                                metadata.put("author", value);
                            } else if ("dc:description".equals(currentTag)) {
                                metadata.put("description", value);
                            }
                        }
                    }
                    
                    if ("metadata".equals(currentTag)) {
                        inMetadata = false;
                    } else if ("manifest".equals(currentTag)) {
                        inManifest = false;
                    } else if ("spine".equals(currentTag)) {
                        inSpine = false;
                    }
                    break;
            }
            eventType = parser.next();
        }

        // Если не нашли количество страниц в метаданных, пробуем подсчитать по spine
        if (!metadata.containsKey("pages") && !idMap.isEmpty()) {
            pageCount = idMap.size();
            metadata.put("pages", String.valueOf(pageCount));
        }

        return coverPath;
    }

    private static void readFb2Metadata(Context context, Uri fileUri, MetadataCallback callback) {
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(inputStream, "UTF-8");

            Map<String, String> metadata = new HashMap<>();
            int eventType = parser.getEventType();
            String currentTag = null;
            StringBuilder currentValue = new StringBuilder();
            boolean inTitleInfo = false;
            boolean inDocumentInfo = false;
            boolean inPublishInfo = false;
            boolean inBody = false;
            int pageCount = 0;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();
                        currentValue = new StringBuilder();
                        
                        if ("title-info".equals(currentTag)) {
                            inTitleInfo = true;
                        } else if ("document-info".equals(currentTag)) {
                            inDocumentInfo = true;
                        } else if ("publish-info".equals(currentTag)) {
                            inPublishInfo = true;
                        } else if ("body".equals(currentTag)) {
                            inBody = true;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (currentValue != null) {
                            currentValue.append(parser.getText());
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if (currentValue != null && currentTag != null) {
                            String value = currentValue.toString().trim();
                            if (inTitleInfo) {
                                if ("book-title".equals(currentTag)) {
                                    metadata.put("title", value);
                                } else if ("author".equals(currentTag)) {
                                    metadata.put("author", value);
                                } else if ("annotation".equals(currentTag)) {
                                    metadata.put("description", value);
                                }
                            } else if (inDocumentInfo) {
                                if ("page-count".equals(currentTag)) {
                                    try {
                                        pageCount = Integer.parseInt(value);
                                        metadata.put("pages", String.valueOf(pageCount));
                                    } catch (NumberFormatException e) {
                                        Log.e(TAG, "Error parsing page count", e);
                                    }
                                }
                            }
                        }
                        
                        if ("title-info".equals(currentTag)) {
                            inTitleInfo = false;
                        } else if ("document-info".equals(currentTag)) {
                            inDocumentInfo = false;
                        } else if ("publish-info".equals(currentTag)) {
                            inPublishInfo = false;
                        } else if ("body".equals(currentTag)) {
                            inBody = false;
                        }
                        break;
                }
                eventType = parser.next();
            }

            // Если не нашли метаданные, используем имя файла как заголовок
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
                    
                    // Estimate page count based on file size
                    long fileSize = tempFb2File.length();
                    int estimatedPages = estimatePages(fileSize);
                    
                    // Create a final copy of the entry name for use in the callback
                    final String finalEntryName = entry.getName();
                    
                    // Читаем метаданные из распакованного FB2 файла
                    readFb2Metadata(context, Uri.fromFile(tempFb2File), new MetadataCallback() {
                        @Override
                        public void onMetadataReady(Map<String, String> extractedMetadata) {
                            // Add estimated page count if not present
                            if (!extractedMetadata.containsKey("pages") && estimatedPages > 0) {
                                extractedMetadata.put("pages", String.valueOf(estimatedPages));
                            }
                            
                            // Передаем метаданные в исходный callback
                            callback.onMetadataReady(extractedMetadata);
                            
                            // Удаляем временный файл
                            tempFb2File.delete();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error reading FB2 from ZIP: " + error);
                            
                            // If FB2 extraction failed but we have a file size,
                            // create basic metadata with estimated page count
                            if (estimatedPages > 0) {
                                Map<String, String> basicMetadata = new HashMap<>();
                                basicMetadata.put("title", getFileNameWithoutExtension(finalEntryName));
                                basicMetadata.put("pages", String.valueOf(estimatedPages));
                                callback.onMetadataReady(basicMetadata);
                            } else {
                                callback.onError("Ошибка при чтении FB2 из архива: " + error);
                            }
                            
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
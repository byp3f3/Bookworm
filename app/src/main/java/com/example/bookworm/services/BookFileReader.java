package com.example.bookworm.services;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BookFileReader {
    private static final String TAG = "BookFileReader";

    public interface BookContentCallback {
        void onContentReady(String content);
        void onError(String error);
    }

    public static void readBookContent(Context context, Uri fileUri, BookContentCallback callback) {
        String mimeType = context.getContentResolver().getType(fileUri);
        if (mimeType == null) {
            callback.onError("Не удалось определить тип файла");
            return;
        }

        try {
            switch (mimeType) {
                case "application/epub+zip":
                    readEpub(context, fileUri, callback);
                    break;
                case "application/pdf":
                    readPdf(context, fileUri, callback);
                    break;
                case "application/x-fictionbook+xml":
                    readFb2(context, fileUri, callback);
                    break;
                case "text/plain":
                    readTxt(context, fileUri, callback);
                    break;
                default:
                    callback.onError("Неподдерживаемый формат файла: " + mimeType);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading book content", e);
            callback.onError("Ошибка при чтении файла: " + e.getMessage());
        }
    }

    private static void readEpub(Context context, Uri fileUri, BookContentCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            StringBuilder content = new StringBuilder();
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith(".html") || entry.getName().endsWith(".xhtml")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
            }

            zipInputStream.close();
            callback.onContentReady(content.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error reading EPUB", e);
            callback.onError("Ошибка при чтении EPUB файла: " + e.getMessage());
        }
    }

    private static void readPdf(Context context, Uri fileUri, BookContentCallback callback) {
        // TODO: Implement PDF reading using PdfBox or similar library
        callback.onError("Чтение PDF файлов пока не поддерживается");
    }

    private static void readFb2(Context context, Uri fileUri, BookContentCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Не удалось открыть файл");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            boolean inBody = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains("<body>")) {
                    inBody = true;
                    continue;
                }
                if (line.contains("</body>")) {
                    break;
                }
                if (inBody) {
                    content.append(line).append("\n");
                }
            }

            reader.close();
            callback.onContentReady(content.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error reading FB2", e);
            callback.onError("Ошибка при чтении FB2 файла: " + e.getMessage());
        }
    }

    private static void readTxt(Context context, Uri fileUri, BookContentCallback callback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
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
            callback.onContentReady(content.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error reading TXT", e);
            callback.onError("Ошибка при чтении TXT файла: " + e.getMessage());
        }
    }
} 
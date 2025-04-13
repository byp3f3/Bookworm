package com.example.bookworm.services;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.example.bookworm.Book;
import com.example.bookworm.SupabaseAuth;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SupabaseService {

    private static final String TAG = "SupabaseService";
    private static final String SUPABASE_URL = "";
    private static final String SUPABASE_KEY = "";

    private final Context context;
    private final Handler mainHandler;
    private final SupabaseAuth supabaseAuth;
    private final ExecutorService executorService;

    public interface BookSaveCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface FileUploadCallback {
        void onSuccess(String fileUrl);
        void onError(String error);
    }

    public SupabaseService(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.supabaseAuth = new SupabaseAuth(context);
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    private String mapStatusToDb(String displayStatus) {
        switch (displayStatus) {
            case "В планах": return "PLANNED";
            case "Читаю": return "READING";
            case "Прочитано": return "FINISHED";
            default: return "PLANNED";
        }
    }

    private String getUserIdFromToken(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8);
                JSONObject payloadJson = new JSONObject(payload);
                return payloadJson.getString("sub");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting user ID from token", e);
        }
        return null;
    }

    private String getFileExtension(Uri uri) {
        String extension = null;

        // Try to get extension from URI first
        String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (fileExtension != null && !fileExtension.isEmpty()) {
            return fileExtension.toLowerCase();
        }

        // Fallback to content resolver
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        }

        // Final fallback - use default extension based on bucket
        if (extension == null) {
            if (uri.toString().contains("books")) {
                return "pdf"; // Default to PDF for books
            } else if (uri.toString().contains("covers")) {
                return "jpg"; // Default to JPG for covers
            }
        }

        return extension != null ? extension : "dat";
    }

    private void uploadFile(Uri fileUri, String bucket, String fileName, FileUploadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            try {
                // 1. Authentication check
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "Upload failed: User not authenticated");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. Log URI details
                Log.d(TAG, "Attempting to upload file:");
                Log.d(TAG, "URI: " + fileUri.toString());
                Log.d(TAG, "Authority: " + fileUri.getAuthority());
                Log.d(TAG, "Scheme: " + fileUri.getScheme());
                Log.d(TAG, "Path: " + fileUri.getPath());

                // 3. Open file with proper error handling
                try {
                    // Try to get file info first
                    try (Cursor cursor = context.getContentResolver().query(
                            fileUri,
                            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                            null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                            long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                            Log.d(TAG, "File info - Name: " + displayName + ", Size: " + size);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Could not get file info: " + e.getMessage());
                    }

                    // Try to open the file
                    Log.d(TAG, "Attempting to open input stream");
                    inputStream = context.getContentResolver().openInputStream(fileUri);
                    if (inputStream == null) {
                        Log.e(TAG, "Failed to open input stream: stream is null");
                        notifyError(callback, "Could not open file");
                        return;
                    }
                    Log.d(TAG, "Successfully opened input stream");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception when accessing file: " + e.getMessage());
                    Log.e(TAG, "Stack trace:", e);
                    notifyError(callback, "Permission denied. Please select the file again using the file picker.");
                    return;
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found: " + e.getMessage());
                    notifyError(callback, "File not found. It may have been moved or deleted.");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error opening file: " + e.getMessage());
                    Log.e(TAG, "Stack trace:", e);
                    notifyError(callback, "Error opening file: " + e.getMessage());
                    return;
                }

                // 4. Configure connection
                Log.d(TAG, "Configuring connection for upload");
                URL url = new URL(SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + fileName);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);

                // 5. Stream file data with progress (optional)
                Log.d(TAG, "Starting file upload");
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        Log.d(TAG, "Uploaded " + totalBytes + " bytes");
                    }
                    Log.d(TAG, "File upload completed, total bytes: " + totalBytes);
                }

                // 6. Handle response
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Upload response code: " + responseCode);
                if (responseCode >= 200 && responseCode < 300) {
                    String fileUrl = SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + fileName;
                    Log.d(TAG, "Upload successful, file URL: " + fileUrl);
                    notifySuccess(callback, fileUrl);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Upload failed: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Upload failed: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Network error during upload: " + e.getMessage());
                Log.e(TAG, "Stack trace:", e);
                notifyError(callback, "Network error: " + e.getMessage());
            } finally {
                closeQuietly(inputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void saveBook(Book book, BookSaveCallback callback) {
        executorService.execute(() -> {
            try {
                // 1. Authentication check
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. Get user ID
                String userId = getUserIdFromToken(accessToken);
                if (userId == null) {
                    notifyError(callback, "Could not get user ID");
                    return;
                }

                // 3. Validate book file
                if (book.getFilePath() == null) {
                    notifyError(callback, "Book file is required");
                    return;
                }

                // Log whether cover path exists
                if (book.getCoverPath() != null) {
                    Log.d(TAG, "Cover path exists: " + book.getCoverPath());
                } else {
                    Log.d(TAG, "No cover path specified for book");
                }

                Uri bookUri = Uri.parse(book.getFilePath());
                String bookExtension = getFileExtension(bookUri);
                String bookFileName = book.getId() + "_" + System.currentTimeMillis() + "." + bookExtension;

                // 4. Upload book file
                uploadFile(bookUri, "books", bookFileName, new FileUploadCallback() {
                    @Override
                    public void onSuccess(String fileUrl) {
                        // 5. Upload cover if exists
                        if (book.getCoverPath() != null) {
                            Log.d(TAG, "Attempting to upload cover: " + book.getCoverPath());
                            Uri coverUri = Uri.parse(book.getCoverPath());
                            String coverExtension = getFileExtension(coverUri);
                            String coverFileName = book.getId() + "_cover_" + System.currentTimeMillis() + "." + coverExtension;

                            uploadFile(coverUri, "covers", coverFileName, new FileUploadCallback() {
                                @Override
                                public void onSuccess(String coverUrl) {
                                    Log.d(TAG, "Cover upload successful: " + coverUrl);
                                    saveBookToDatabase(book, userId, fileUrl, coverUrl, callback);
                                }

                                @Override
                                public void onError(String error) {
                                    Log.e(TAG, "Failed to upload cover: " + error + ". Continuing without cover image.");
                                    saveBookToDatabase(book, userId, fileUrl, null, callback);
                                }
                            });
                        } else {
                            Log.d(TAG, "No cover to upload, saving book with file URL only: " + fileUrl);
                            saveBookToDatabase(book, userId, fileUrl, null, callback);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        notifyError(callback, "Book upload failed: " + error);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in saveBook", e);
                notifyError(callback, "Error: " + e.getMessage());
            }
        });
    }

    private void saveBookToDatabase(Book book, String userId, String fileUrl, String coverUrl, BookSaveCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. Prepare JSON data
                JSONObject bookData = new JSONObject();
                bookData.put("id", book.getId());
                bookData.put("title", book.getTitle());
                bookData.put("author", book.getAuthor());
                bookData.put("description", book.getDescription());
                bookData.put("page_count", book.getTotalPages());
                bookData.put("status", mapStatusToDb(book.getStatus()));
                bookData.put("current_page", book.getCurrentPage());
                bookData.put("file_url", fileUrl);
                bookData.put("cover_image_url", coverUrl);

                // Optional fields
                int rating = book.getRating();
                if (rating >= 1 && rating <= 5) {
                    bookData.put("rating", rating);
                }

                if (book.getReview() != null && !book.getReview().isEmpty()) {
                    bookData.put("review", book.getReview());
                }

                if (book.getStartDate() != null) {
                    bookData.put("start_date", book.getStartDate());
                }

                if (book.getEndDate() != null) {
                    bookData.put("finish_date", book.getEndDate());
                }

                // Technical fields
                bookData.put("file_format", getFileFormatFromUrl(fileUrl));
                bookData.put("user_id", userId);

                // 2. Configure connection
                URL url = new URL(SUPABASE_URL + "/rest/v1/books");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + supabaseAuth.getAccessToken());
                connection.setRequestProperty("Prefer", "return=minimal");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                // 3. Send data
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = bookData.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 4. Handle response
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    notifySuccess(callback);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Save failed: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Save failed: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Network error saving book", e);
                notifyError(callback, "Network error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String getFileFormatFromUrl(String fileUrl) {
        if (fileUrl == null) return "UNKNOWN";

        int lastDot = fileUrl.lastIndexOf('.');
        if (lastDot > 0) {
            String ext = fileUrl.substring(lastDot + 1).toLowerCase();
            switch (ext) {
                case "pdf": return "PDF";
                case "epub": return "EPUB";
                case "fb2": return "FB2";
                case "txt": return "TXT";
                // Обрабатываем случаи, когда расширение не входит в список допустимых
                default: {
                    Log.w(TAG, "Non-standard file extension detected: " + ext);
                    // Для FB2.ZIP файлов
                    if (ext.equals("zip") && fileUrl.toLowerCase().contains("fb2")) {
                        return "FB2";
                    }
                    // Возвращаем один из допустимых форматов, предпочтительно EPUB как наиболее распространенный
                    return "EPUB";
                }
            }
        }
        // Возвращаем допустимое значение по умолчанию вместо UNKNOWN
        Log.w(TAG, "Could not determine file format from URL: " + fileUrl);
        return "EPUB";
    }

    private String readErrorResponse(HttpURLConnection connection) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading error response", e);
            return "Unknown error";
        }
    }

    private void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing stream", e);
            }
        }
    }

    private void notifySuccess(BookSaveCallback callback) {
        mainHandler.post(callback::onSuccess);
    }

    private void notifyError(BookSaveCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    private void notifySuccess(FileUploadCallback callback, String url) {
        mainHandler.post(() -> callback.onSuccess(url));
    }

    private void notifyError(FileUploadCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
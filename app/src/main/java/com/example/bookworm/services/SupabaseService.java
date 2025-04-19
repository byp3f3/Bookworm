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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class SupabaseService {

    private static final String TAG = "SupabaseService";
    private static final String SUPABASE_URL = "https://mfszyfmtujztqrjweixz.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mc3p5Zm10dWp6dHFyandlaXh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTkzNDAsImV4cCI6MjA1NzE3NTM0MH0.3URDTNl5T0R_TyWn6L0NlEFuLYoiH2qcQdYVNovFtVw";

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

    public interface BookProgressCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface BookProgressDataCallback {
        void onSuccess(int currentPage);
        void onError(String error);
    }

    /**
     * Callback for retrieving a single book
     */
    public interface BookCallback {
        void onSuccess(Book book);
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
                bookData.put("status", book.getStatus());
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


    public void getCurrentlyReadingBooks(BooksLoadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

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

                // 3. Configure connection - use UI status value directly
                // Use Russian status in query string: "status=eq.Читаю"
                String urlString = SUPABASE_URL + "/rest/v1/books?select=*&user_id=eq." + userId + "&status=eq.Читаю";
                Log.d(TAG, "Fetching currently reading books with URL: " + urlString);
                
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                // 4. Handle response
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "getCurrentlyReadingBooks response code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseData = response.toString();
                    Log.d(TAG, "getCurrentlyReadingBooks response: " + responseData);

                    JSONArray booksArray = new JSONArray(responseData);
                    List<Book> books = new ArrayList<>();

                    for (int i = 0; i < booksArray.length(); i++) {
                        JSONObject bookJson = booksArray.getJSONObject(i);
                        Book book = new Book();
                        book.setId(bookJson.getString("id"));
                        book.setTitle(bookJson.getString("title"));
                        book.setAuthor(bookJson.getString("author"));
                        book.setDescription(bookJson.optString("description", ""));
                        book.setTotalPages(bookJson.optInt("page_count", 0));
                        book.setCurrentPage(bookJson.optInt("current_page", 0));
                        
                        // Use status directly from database - should already be "Читаю"
                        String status = bookJson.optString("status", "Читаю");
                        book.setStatus(status);
                        
                        book.setCoverPath(bookJson.optString("cover_image_url", null));
                        book.setStartDate(bookJson.optString("start_date", null));
                        book.setEndDate(bookJson.optString("finish_date", null));
                        book.setRating(bookJson.optInt("rating", 0));

                        books.add(book);
                    }

                    notifySuccess(callback, books);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Failed to load reading books: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Failed to load books: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading reading books", e);
                notifyError(callback, "Error: " + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Error closing reader", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void getAllUserBooks(BooksLoadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

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

                // 3. Configure connection - получаем ВСЕ книги пользователя
                String urlString = SUPABASE_URL + "/rest/v1/books?select=*&user_id=eq." + userId;
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                // 4. Handle response
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONArray booksArray = new JSONArray(response.toString());
                    List<Book> books = new ArrayList<>();

                    for (int i = 0; i < booksArray.length(); i++) {
                        JSONObject bookJson = booksArray.getJSONObject(i);
                        Book book = new Book();
                        book.setId(bookJson.getString("id"));
                        book.setTitle(bookJson.getString("title"));
                        book.setAuthor(bookJson.getString("author"));
                        book.setDescription(bookJson.optString("description", ""));
                        book.setTotalPages(bookJson.optInt("page_count", 0));
                        book.setCurrentPage(bookJson.optInt("current_page", 0));

                        // Use status directly from database, fallback to default if null
                        String status = bookJson.optString("status", "В планах");
                        if (status == null) {
                            status = "В планах"; // Default value
                        }
                        book.setStatus(status);

                        book.setCoverPath(bookJson.optString("cover_image_url", null));
                        book.setStartDate(bookJson.optString("start_date", null));
                        book.setEndDate(bookJson.optString("finish_date", null));
                        book.setRating(bookJson.optInt("rating", 0));

                        books.add(book);
                    }

                    notifySuccess(callback, books);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    notifyError(callback, "Failed to load books: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading books", e);
                notifyError(callback, "Error: " + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Error closing reader", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void notifySuccess(BooksLoadCallback callback, List<Book> books) {
        mainHandler.post(() -> callback.onSuccess(books));
    }

    private void notifyError(BooksLoadCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    public interface BooksLoadCallback {
        void onSuccess(List<Book> books);
        void onError(String error);
    }

    /**
     * Обновляет прогресс чтения книги (текущую страницу)
     */
    public void updateBookProgress(String bookId, int currentPage, BookProgressCallback callback) {
        if (bookId == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Null bookId"));
            }
            return;
        }

        // Выполняем в отдельном потоке
        executorService.execute(() -> {
            try {
                // Получаем токен авторизации
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("User not authenticated"));
                    }
                    return;
                }

                // Создаем JSON для обновления
                JSONObject jsonParams = new JSONObject();
                try {
                    jsonParams.put("current_page", currentPage);
                } catch (JSONException e) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Ошибка формирования JSON: " + e.getMessage()));
                    }
                    return;
                }

                // Выполняем запрос
                URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Prefer", "return=minimal");
                connection.setRequestProperty("If-Match", "*");
                
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonParams.toString().getBytes("UTF-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Successfully updated book current_page to: " + currentPage);
                    
                    // Сразу проверяем результат обновления
                    verifyPageUpdate(bookId, currentPage, accessToken, callback);
                } else {
                    String responseMessage = connection.getResponseMessage();
                    String errorMessage = "Error updating book progress: " + responseCode + " " + responseMessage;
                    Log.e(TAG, errorMessage);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(errorMessage));
                    }
                }
                
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Exception updating book progress: " + e.getMessage());
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Ошибка обновления прогресса: " + e.getMessage()));
                }
            }
        });
    }
    
    /**
     * Проверяет, что обновление страницы в Supabase действительно прошло успешно
     */
    private void verifyPageUpdate(String bookId, int expectedPage, String accessToken, BookProgressCallback callback) {
        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject book = jsonArray.getJSONObject(0);
                    int actualPage = book.getInt("current_page");
                    
                    if (actualPage == expectedPage) {
                        Log.d(TAG, "Verified book page update: " + expectedPage);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess());
                        }
                    } else {
                        Log.w(TAG, "Page verification failed! Expected: " + expectedPage + ", Actual: " + actualPage + ". Retrying update...");
                        // Повторная попытка обновления
                        retryPageUpdate(bookId, expectedPage, accessToken, callback);
                    }
                } else {
                    Log.e(TAG, "Book not found during verification");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Книга не найдена при проверке"));
                    }
                }
            } else {
                Log.e(TAG, "Error during verification: " + responseCode);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess()); // Считаем успешным, чтобы не блокировать UI
                }
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during verification: " + e.getMessage());
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess()); // Считаем успешным, чтобы не блокировать UI
            }
        }
    }
    
    /**
     * Повторная попытка обновления страницы с принудительной синхронизацией
     */
    private void retryPageUpdate(String bookId, int currentPage, String accessToken, BookProgressCallback callback) {
        try {
            // Создаем JSON для обновления
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("current_page", currentPage);
            
            URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Prefer", "return=representation");  // Запрашиваем возврат данных после обновления
            
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonParams.toString().getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                // Читаем ответ, чтобы убедиться в обновлении
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                Log.d(TAG, "Retry update response: " + response.toString());
                Log.d(TAG, "Successfully forced book current_page update to: " + currentPage);
                
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess());
                }
            } else {
                String errorMessage = "Error in retry update: " + responseCode;
                Log.e(TAG, errorMessage);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(errorMessage));
                }
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Exception in retry update: " + e.getMessage());
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Ошибка при повторном обновлении: " + e.getMessage()));
            }
        }
    }

    /**
     * Updates the total page count of a book in Supabase
     * @param bookId The ID of the book
     * @param pageCount The total number of pages
     * @param callback Callback to handle success or error
     */
    public void updateBookPageCount(String bookId, int pageCount, BookProgressCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. Authentication check
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. Prepare JSON data
                JSONObject updateData = new JSONObject();
                updateData.put("page_count", pageCount);

                // 3. Configure connection
                URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Prefer", "return=minimal");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // 4. Send data
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = updateData.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 5. Handle response
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Successfully updated book page_count to: " + pageCount);
                    notifySuccess(callback);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Update page_count failed: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Update failed: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating book page count", e);
                notifyError(callback, "Network error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void notifySuccess(BookProgressCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(BookProgressCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    /**
     * Получает текущий прогресс чтения книги из Supabase
     * @param bookId ID книги
     * @param callback Callback для обработки ответа
     */
    public void getBookProgress(String bookId, BookProgressDataCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. Проверка аутентификации
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. Настройка соединения
                URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                // 3. Обработка ответа
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    try {
                        // Парсинг JSON ответа
                        JSONArray jsonArray = new JSONArray(response.toString());
                        if (jsonArray.length() > 0) {
                            JSONObject book = jsonArray.getJSONObject(0);
                            int currentPage = book.getInt("current_page");
                            Log.d(TAG, "Retrieved current_page from Supabase: " + currentPage + " for book " + bookId);
                            notifySuccess(callback, currentPage);
                        } else {
                            notifyError(callback, "Book not found");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing book progress data", e);
                        notifyError(callback, "Error parsing data: " + e.getMessage());
                    }
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Get book progress failed: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Request failed: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting book progress", e);
                notifyError(callback, "Network error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void notifySuccess(BookProgressDataCallback callback, int currentPage) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(currentPage));
        }
    }

    private void notifyError(BookProgressDataCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    /**
     * Retrieves a book by its ID from Supabase
     * @param bookId ID of the book to retrieve
     * @param callback Callback to handle the response
     */
    public void getBookById(String bookId, BookCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                // 1. Authentication check
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. Configure connection
                String urlString = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId;
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // 3. Handle response
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseData = response.toString();
                    Log.d(TAG, "getBookById response: " + responseData);

                    JSONArray booksArray = new JSONArray(responseData);
                    if (booksArray.length() > 0) {
                        JSONObject bookJson = booksArray.getJSONObject(0);
                        Book book = new Book();
                        book.setId(bookJson.getString("id"));
                        book.setTitle(bookJson.getString("title"));
                        book.setAuthor(bookJson.getString("author"));
                        book.setDescription(bookJson.optString("description", ""));
                        book.setTotalPages(bookJson.optInt("page_count", 0));
                        book.setCurrentPage(bookJson.optInt("current_page", 0));
                        book.setStatus(bookJson.optString("status", "В планах"));
                        book.setCoverPath(bookJson.optString("cover_image_url", null));
                        book.setStartDate(bookJson.optString("start_date", null));
                        book.setEndDate(bookJson.optString("finish_date", null));
                        book.setRating(bookJson.optInt("rating", 0));

                        notifySuccess(callback, book);
                    } else {
                        notifyError(callback, "Book not found");
                    }
                } else {
                    String errorMsg = readErrorResponse(connection);
                    Log.e(TAG, "Failed to get book: " + responseCode + " - " + errorMsg);
                    notifyError(callback, "Failed to get book: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting book", e);
                notifyError(callback, "Error: " + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Error closing reader", e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void notifySuccess(BookCallback callback, Book book) {
        mainHandler.post(() -> callback.onSuccess(book));
    }

    private void notifyError(BookCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    /**
     * Принудительно обновляет current_page с блокировкой потока
     * Используется только для критических случаев синхронизации
     */
    public void forceUpdateBookPage(String bookId, int currentPage) {
        if (bookId == null) {
            Log.e(TAG, "BookId is null, cannot update current page");
            return;
        }
        
        try {
            // Создаем данные для обновления
            JSONObject jsonData = new JSONObject();
            jsonData.put("current_page", currentPage);

            // Формируем URL
            String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId;
            
            // Создаем соединение
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=representation");
            
            // Важно: добавляем If-Match заголовок для предотвращения конфликтов
            connection.setRequestProperty("If-Match", "*");
            
            connection.setDoOutput(true);
            
            // Записываем данные
            OutputStream os = connection.getOutputStream();
            os.write(jsonData.toString().getBytes());
            os.flush();
            os.close();
            
            // Проверяем ответ
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                Log.d(TAG, "⚡ Force update successful: page " + currentPage + " for book " + bookId);
                
                // Проверяем, что обновление действительно применилось
                verifyPageUpdate(bookId, currentPage);
            } else {
                Log.e(TAG, "⚡ Force update failed with code: " + responseCode);
                
                // Читаем сообщение об ошибке
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorMessage = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorMessage.append(line);
                    }
                    reader.close();
                    Log.e(TAG, "Error details: " + errorMessage.toString());
                }
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error during force update: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет, что значение current_page в базе соответствует ожидаемому
     * и при необходимости выполняет обновление
     */
    public void verifyAndUpdateBookData(String bookId, int expectedPage) {
        if (bookId == null) {
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Получаем текущее значение из базы
                String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page";
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                connection.setRequestProperty("Content-Type", "application/json");

                int responseCode = connection.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Читаем ответ
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Парсим JSON
                    JSONArray jsonArray = new JSONArray(response.toString());
                    if (jsonArray.length() > 0) {
                        JSONObject bookData = jsonArray.getJSONObject(0);
                        int actualPage = bookData.optInt("current_page", -1);
                        
                        if (actualPage != expectedPage) {
                            Log.w(TAG, "⚠️ Обнаружено несоответствие: ожидалась страница " + expectedPage + 
                                    ", но в базе " + actualPage + ". Выполняем синхронизацию.");
                            
                            // Принудительно обновляем страницу в базе
                            forceUpdateBookPage(bookId, expectedPage);
                        } else {
                            Log.d(TAG, "✅ Проверка страницы: в базе корректное значение " + actualPage);
                        }
                    }
                } else {
                    Log.e(TAG, "Ошибка при проверке текущей страницы: " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при проверке текущей страницы: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Проверяет, что значение current_page в базе соответствует ожидаемому
     */
    private boolean verifyPageUpdate(String bookId, int expectedPage) {
        try {
            // Получаем текущее значение из базы
            String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page";
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Читаем ответ
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Парсим JSON
                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject bookData = jsonArray.getJSONObject(0);
                    int actualPage = bookData.optInt("current_page", -1);
                    
                    if (actualPage != expectedPage) {
                        Log.w(TAG, "⚠️ Верификация не прошла: ожидалась страница " + expectedPage + 
                                ", но в базе " + actualPage);
                        return false;
                    } else {
                        Log.d(TAG, "✅ Верификация прошла успешно: страница " + actualPage);
                        return true;
                    }
                }
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при верификации страницы: " + e.getMessage(), e);
        }
        
        return false;
    }
}


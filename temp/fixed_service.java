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
import com.example.bookworm.Quote;
import com.example.bookworm.SupabaseAuth;
import com.example.bookworm.models.Shelf;

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

    public interface BooksLoadCallback {
        void onSuccess(List<Book> books);
        void onError(String error);
    }

    public interface QuoteCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface QuotesLoadCallback {
        void onSuccess(List<Quote> quotes);
        void onError(String error);
    }

    // Add new interface callbacks for shelves
    public interface ShelfCallback {
        /**
         * Called when operation is successful
         * @param shelf The shelf object
         */
        void onSuccess(Shelf shelf);
        
        /**
         * For backward compatibility with old code
         * @deprecated Use onSuccess(Shelf) instead
         */
        @Deprecated
        default void onSuccess() {
            // Default implementation calls the new method with null
            onSuccess(null);
        }
        
        /**
         * Called when operation fails
         * @param error Error message
         */
        void onError(String error);
    }

    public interface ShelvesLoadCallback {
        void onSuccess(List<com.example.bookworm.models.Shelf> shelves);
        void onError(String error);
    }

    public interface BookShelfCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface BooksIdsCallback {
        void onSuccess(List<String> bookIds);
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
                // РћР±СЂР°Р±Р°С‚С‹РІР°РµРј СЃР»СѓС‡Р°Рё, РєРѕРіРґР° СЂР°СЃС€РёСЂРµРЅРёРµ РЅРµ РІС…РѕРґРёС‚ РІ СЃРїРёСЃРѕРє РґРѕРїСѓСЃС‚РёРјС‹С…
                default: {
                    Log.w(TAG, "Non-standard file extension detected: " + ext);
                    // Р”Р»СЏ FB2.ZIP С„Р°Р№Р»РѕРІ
                    if (ext.equals("zip") && fileUrl.toLowerCase().contains("fb2")) {
                        return "FB2";
                    }
                    // Р’РѕР·РІСЂР°С‰Р°РµРј РѕРґРёРЅ РёР· РґРѕРїСѓСЃС‚РёРјС‹С… С„РѕСЂРјР°С‚РѕРІ, РїСЂРµРґРїРѕС‡С‚РёС‚РµР»СЊРЅРѕ EPUB РєР°Рє РЅР°РёР±РѕР»РµРµ СЂР°СЃРїСЂРѕСЃС‚СЂР°РЅРµРЅРЅС‹Р№
                    return "EPUB";
                }
            }
        }
        // Р’РѕР·РІСЂР°С‰Р°РµРј РґРѕРїСѓСЃС‚РёРјРѕРµ Р·РЅР°С‡РµРЅРёРµ РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ РІРјРµСЃС‚Рѕ UNKNOWN
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
                // Use Russian status in query string: "status=eq.Р§РёС‚Р°СЋ"
                String urlString = SUPABASE_URL + "/rest/v1/books?select=*&user_id=eq." + userId + "&status=eq.Р§РёС‚Р°СЋ";
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
                        
                        // Use status directly from database - should already be "Р§РёС‚Р°СЋ"
                        String status = bookJson.optString("status", "Р§РёС‚Р°СЋ");
                        book.setStatus(status);
                        
                        book.setCoverPath(bookJson.optString("cover_image_url", null));
                        book.setStartDate(bookJson.optString("start_date", null));
                        book.setEndDate(bookJson.optString("finish_date", null));
                        book.setRating(bookJson.optInt("rating", 0));
                        
                        // Р”РѕР±Р°РІР»СЏРµРј Р·Р°РіСЂСѓР·РєСѓ file_url РёР· Р±Р°Р·С‹ РґР°РЅРЅС‹С…
                        if (bookJson.has("file_url") && !bookJson.isNull("file_url")) {
                            String fileUrl = bookJson.getString("file_url");
                            book.setFilePath(fileUrl);
                            
                            // РћРїСЂРµРґРµР»СЏРµРј С„РѕСЂРјР°С‚ С„Р°Р№Р»Р° РїРѕ URL
                            String fileFormat = getFileFormatFromUrl(fileUrl);
                            book.setFileFormat(fileFormat);
                        }

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

                // 3. Configure connection - РїРѕР»СѓС‡Р°РµРј Р’РЎР• РєРЅРёРіРё РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ
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
                        String status = bookJson.optString("status", "Р’ РїР»Р°РЅР°С…");
                        if (status == null) {
                            status = "Р’ РїР»Р°РЅР°С…"; // Default value
                        }
                        book.setStatus(status);

                        book.setCoverPath(bookJson.optString("cover_image_url", null));
                        book.setStartDate(bookJson.optString("start_date", null));
                        book.setEndDate(bookJson.optString("finish_date", null));
                        book.setRating(bookJson.optInt("rating", 0));
                        
                        // Р”РѕР±Р°РІР»СЏРµРј Р·Р°РіСЂСѓР·РєСѓ file_url РёР· Р±Р°Р·С‹ РґР°РЅРЅС‹С…
                        if (bookJson.has("file_url") && !bookJson.isNull("file_url")) {
                            String fileUrl = bookJson.getString("file_url");
                            book.setFilePath(fileUrl);
                            
                            // РћРїСЂРµРґРµР»СЏРµРј С„РѕСЂРјР°С‚ С„Р°Р№Р»Р° РїРѕ URL
                            String fileFormat = getFileFormatFromUrl(fileUrl);
                            book.setFileFormat(fileFormat);
                        }

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

    /**
     * РћР±РЅРѕРІР»СЏРµС‚ РїСЂРѕРіСЂРµСЃСЃ С‡С‚РµРЅРёСЏ РєРЅРёРіРё (С‚РµРєСѓС‰СѓСЋ СЃС‚СЂР°РЅРёС†Сѓ)
     */
    public void updateBookProgress(String bookId, int currentPage, BookProgressCallback callback) {
        if (bookId == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Null bookId"));
            }
            return;
        }

        // Р’С‹РїРѕР»РЅСЏРµРј РІ РѕС‚РґРµР»СЊРЅРѕРј РїРѕС‚РѕРєРµ
        executorService.execute(() -> {
            try {
                // РџРѕР»СѓС‡Р°РµРј С‚РѕРєРµРЅ Р°РІС‚РѕСЂРёР·Р°С†РёРё
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("User not authenticated"));
                    }
                    return;
                }

                // РЎРѕР·РґР°РµРј JSON РґР»СЏ РѕР±РЅРѕРІР»РµРЅРёСЏ
                JSONObject jsonParams = new JSONObject();
                try {
                    jsonParams.put("current_page", currentPage);
                } catch (JSONException e) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("РћС€РёР±РєР° С„РѕСЂРјРёСЂРѕРІР°РЅРёСЏ JSON: " + e.getMessage()));
                    }
                    return;
                }

                // Р’С‹РїРѕР»РЅСЏРµРј Р·Р°РїСЂРѕСЃ
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
                    
                    // РЎСЂР°Р·Сѓ РїСЂРѕРІРµСЂСЏРµРј СЂРµР·СѓР»СЊС‚Р°С‚ РѕР±РЅРѕРІР»РµРЅРёСЏ
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
                    mainHandler.post(() -> callback.onError("РћС€РёР±РєР° РѕР±РЅРѕРІР»РµРЅРёСЏ РїСЂРѕРіСЂРµСЃСЃР°: " + e.getMessage()));
                }
            }
        });
    }
    
    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, С‡С‚Рѕ РѕР±РЅРѕРІР»РµРЅРёРµ СЃС‚СЂР°РЅРёС†С‹ РІ Supabase РґРµР№СЃС‚РІРёС‚РµР»СЊРЅРѕ РїСЂРѕС€Р»Рѕ СѓСЃРїРµС€РЅРѕ
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
                        // РџРѕРІС‚РѕСЂРЅР°СЏ РїРѕРїС‹С‚РєР° РѕР±РЅРѕРІР»РµРЅРёСЏ
                        retryPageUpdate(bookId, expectedPage, accessToken, callback);
                    }
                } else {
                    Log.e(TAG, "Book not found during verification");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("РљРЅРёРіР° РЅРµ РЅР°Р№РґРµРЅР° РїСЂРё РїСЂРѕРІРµСЂРєРµ"));
                    }
                }
            } else {
                Log.e(TAG, "Error during verification: " + responseCode);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess()); // РЎС‡РёС‚Р°РµРј СѓСЃРїРµС€РЅС‹Рј, С‡С‚РѕР±С‹ РЅРµ Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ UI
                }
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during verification: " + e.getMessage());
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess()); // РЎС‡РёС‚Р°РµРј СѓСЃРїРµС€РЅС‹Рј, С‡С‚РѕР±С‹ РЅРµ Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ UI
            }
        }
    }
    
    /**
     * РџРѕРІС‚РѕСЂРЅР°СЏ РїРѕРїС‹С‚РєР° РѕР±РЅРѕРІР»РµРЅРёСЏ СЃС‚СЂР°РЅРёС†С‹ СЃ РїСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕР№ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРµР№
     */
    private void retryPageUpdate(String bookId, int currentPage, String accessToken, BookProgressCallback callback) {
        try {
            // РЎРѕР·РґР°РµРј JSON РґР»СЏ РѕР±РЅРѕРІР»РµРЅРёСЏ
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("current_page", currentPage);
            
            URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Prefer", "return=representation");  // Р—Р°РїСЂР°С€РёРІР°РµРј РІРѕР·РІСЂР°С‚ РґР°РЅРЅС‹С… РїРѕСЃР»Рµ РѕР±РЅРѕРІР»РµРЅРёСЏ
            
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonParams.toString().getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                // Р§РёС‚Р°РµРј РѕС‚РІРµС‚, С‡С‚РѕР±С‹ СѓР±РµРґРёС‚СЊСЃСЏ РІ РѕР±РЅРѕРІР»РµРЅРёРё
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
                mainHandler.post(() -> callback.onError("РћС€РёР±РєР° РїСЂРё РїРѕРІС‚РѕСЂРЅРѕРј РѕР±РЅРѕРІР»РµРЅРёРё: " + e.getMessage()));
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
     * РџРѕР»СѓС‡Р°РµС‚ С‚РµРєСѓС‰РёР№ РїСЂРѕРіСЂРµСЃСЃ С‡С‚РµРЅРёСЏ РєРЅРёРіРё РёР· Supabase
     * @param bookId ID РєРЅРёРіРё
     * @param callback Callback РґР»СЏ РѕР±СЂР°Р±РѕС‚РєРё РѕС‚РІРµС‚Р°
     */
    public void getBookProgress(String bookId, BookProgressDataCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. РџСЂРѕРІРµСЂРєР° Р°СѓС‚РµРЅС‚РёС„РёРєР°С†РёРё
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. РќР°СЃС‚СЂРѕР№РєР° СЃРѕРµРґРёРЅРµРЅРёСЏ
                URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                // 3. РћР±СЂР°Р±РѕС‚РєР° РѕС‚РІРµС‚Р°
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
                        // РџР°СЂСЃРёРЅРі JSON РѕС‚РІРµС‚Р°
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
                        book.setStatus(bookJson.optString("status", "Р’ РїР»Р°РЅР°С…"));
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
     * РџСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ РѕР±РЅРѕРІР»СЏРµС‚ current_page СЃ Р±Р»РѕРєРёСЂРѕРІРєРѕР№ РїРѕС‚РѕРєР°
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ РєСЂРёС‚РёС‡РµСЃРєРёС… СЃР»СѓС‡Р°РµРІ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё
     */
    public void forceUpdateBookPage(String bookId, int currentPage) {
        if (bookId == null) {
            Log.e(TAG, "BookId is null, cannot update current page");
            return;
        }
        
        try {
            // РЎРѕР·РґР°РµРј РґР°РЅРЅС‹Рµ РґР»СЏ РѕР±РЅРѕРІР»РµРЅРёСЏ
            JSONObject jsonData = new JSONObject();
            jsonData.put("current_page", currentPage);

            // Р¤РѕСЂРјРёСЂСѓРµРј URL
            String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId;
            
            // РЎРѕР·РґР°РµРј СЃРѕРµРґРёРЅРµРЅРёРµ
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PATCH");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=representation");
            
            // Р’Р°Р¶РЅРѕ: РґРѕР±Р°РІР»СЏРµРј If-Match Р·Р°РіРѕР»РѕРІРѕРє РґР»СЏ РїСЂРµРґРѕС‚РІСЂР°С‰РµРЅРёСЏ РєРѕРЅС„Р»РёРєС‚РѕРІ
            connection.setRequestProperty("If-Match", "*");
            
            connection.setDoOutput(true);
            
            // Р—Р°РїРёСЃС‹РІР°РµРј РґР°РЅРЅС‹Рµ
            OutputStream os = connection.getOutputStream();
            os.write(jsonData.toString().getBytes());
            os.flush();
            os.close();
            
            // РџСЂРѕРІРµСЂСЏРµРј РѕС‚РІРµС‚
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                Log.d(TAG, "вљЎ Force update successful: page " + currentPage + " for book " + bookId);
                
                // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РѕР±РЅРѕРІР»РµРЅРёРµ РґРµР№СЃС‚РІРёС‚РµР»СЊРЅРѕ РїСЂРёРјРµРЅРёР»РѕСЃСЊ
                verifyPageUpdate(bookId, currentPage);
            } else {
                Log.e(TAG, "вљЎ Force update failed with code: " + responseCode);
                
                // Р§РёС‚Р°РµРј СЃРѕРѕР±С‰РµРЅРёРµ РѕР± РѕС€РёР±РєРµ
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
     * РџСЂРѕРІРµСЂСЏРµС‚, С‡С‚Рѕ Р·РЅР°С‡РµРЅРёРµ current_page РІ Р±Р°Р·Рµ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓРµС‚ РѕР¶РёРґР°РµРјРѕРјСѓ
     * Рё РїСЂРё РЅРµРѕР±С…РѕРґРёРјРѕСЃС‚Рё РІС‹РїРѕР»РЅСЏРµС‚ РѕР±РЅРѕРІР»РµРЅРёРµ
     */
    public void verifyAndUpdateBookData(String bookId, int expectedPage) {
        if (bookId == null) {
            return;
        }
        
        executorService.execute(() -> {
            try {
                // РџРѕР»СѓС‡Р°РµРј С‚РµРєСѓС‰РµРµ Р·РЅР°С‡РµРЅРёРµ РёР· Р±Р°Р·С‹
                String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page";
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                connection.setRequestProperty("Content-Type", "application/json");

                int responseCode = connection.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Р§РёС‚Р°РµРј РѕС‚РІРµС‚
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // РџР°СЂСЃРёРј JSON
                    JSONArray jsonArray = new JSONArray(response.toString());
                    if (jsonArray.length() > 0) {
                        JSONObject bookData = jsonArray.getJSONObject(0);
                        int actualPage = bookData.optInt("current_page", -1);
                        
                        if (actualPage != expectedPage) {
                            Log.w(TAG, "вљ пёЏ РћР±РЅР°СЂСѓР¶РµРЅРѕ РЅРµСЃРѕРѕС‚РІРµС‚СЃС‚РІРёРµ: РѕР¶РёРґР°Р»Р°СЃСЊ СЃС‚СЂР°РЅРёС†Р° " + expectedPage + 
                                    ", РЅРѕ РІ Р±Р°Р·Рµ " + actualPage + ". Р’С‹РїРѕР»РЅСЏРµРј СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЋ.");
                            
                            // РџСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ РѕР±РЅРѕРІР»СЏРµРј СЃС‚СЂР°РЅРёС†Сѓ РІ Р±Р°Р·Рµ
                            forceUpdateBookPage(bookId, expectedPage);
                        } else {
                            Log.d(TAG, "вњ… РџСЂРѕРІРµСЂРєР° СЃС‚СЂР°РЅРёС†С‹: РІ Р±Р°Р·Рµ РєРѕСЂСЂРµРєС‚РЅРѕРµ Р·РЅР°С‡РµРЅРёРµ " + actualPage);
                        }
                    }
                } else {
                    Log.e(TAG, "РћС€РёР±РєР° РїСЂРё РїСЂРѕРІРµСЂРєРµ С‚РµРєСѓС‰РµР№ СЃС‚СЂР°РЅРёС†С‹: " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "РћС€РёР±РєР° РїСЂРё РїСЂРѕРІРµСЂРєРµ С‚РµРєСѓС‰РµР№ СЃС‚СЂР°РЅРёС†С‹: " + e.getMessage(), e);
            }
        });
    }

    /**
     * РџСЂРѕРІРµСЂСЏРµС‚, С‡С‚Рѕ Р·РЅР°С‡РµРЅРёРµ current_page РІ Р±Р°Р·Рµ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓРµС‚ РѕР¶РёРґР°РµРјРѕРјСѓ
     */
    private boolean verifyPageUpdate(String bookId, int expectedPage) {
        try {
            // РџРѕР»СѓС‡Р°РµРј С‚РµРєСѓС‰РµРµ Р·РЅР°С‡РµРЅРёРµ РёР· Р±Р°Р·С‹
            String endpoint = SUPABASE_URL + "/rest/v1/books?id=eq." + bookId + "&select=current_page";
            URL url = new URL(endpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Р§РёС‚Р°РµРј РѕС‚РІРµС‚
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // РџР°СЂСЃРёРј JSON
                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject bookData = jsonArray.getJSONObject(0);
                    int actualPage = bookData.optInt("current_page", -1);
                    
                    if (actualPage != expectedPage) {
                        Log.w(TAG, "вљ пёЏ Р’РµСЂРёС„РёРєР°С†РёСЏ РЅРµ РїСЂРѕС€Р»Р°: РѕР¶РёРґР°Р»Р°СЃСЊ СЃС‚СЂР°РЅРёС†Р° " + expectedPage + 
                                ", РЅРѕ РІ Р±Р°Р·Рµ " + actualPage);
                        return false;
                    } else {
                        Log.d(TAG, "вњ… Р’РµСЂРёС„РёРєР°С†РёСЏ РїСЂРѕС€Р»Р° СѓСЃРїРµС€РЅРѕ: СЃС‚СЂР°РЅРёС†Р° " + actualPage);
                        return true;
                    }
                }
            }
            
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "РћС€РёР±РєР° РїСЂРё РІРµСЂРёС„РёРєР°С†РёРё СЃС‚СЂР°РЅРёС†С‹: " + e.getMessage(), e);
        }
        
        return false;
    }

    public void saveQuote(Quote quote, QuoteCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. РџСЂРѕРІРµСЂРєР° Р°СѓС‚РµРЅС‚РёС„РёРєР°С†РёРё
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. РџРѕРґРіРѕС‚РѕРІРєР° JSON РґР°РЅРЅС‹С…
                JSONObject quoteData = new JSONObject();
                quoteData.put("id", quote.getId());
                quoteData.put("book_id", quote.getBookId());
                quoteData.put("user_id", quote.getUserId());
                quoteData.put("text", quote.getText());
                quoteData.put("start_page", quote.getStartPage());
                quoteData.put("end_page", quote.getEndPage());
                quoteData.put("created_at", quote.getCreatedAt());

                // 3. РќР°СЃС‚СЂРѕР№РєР° СЃРѕРµРґРёРЅРµРЅРёСЏ
                URL url = new URL(SUPABASE_URL + "/rest/v1/quotes");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Prefer", "return=minimal");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // 4. РћС‚РїСЂР°РІРєР° РґР°РЅРЅС‹С…
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = quoteData.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 5. РћР±СЂР°Р±РѕС‚РєР° РѕС‚РІРµС‚Р°
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    notifySuccess(callback);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    notifyError(callback, "РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ С†РёС‚Р°С‚С‹: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                notifyError(callback, "РћС€РёР±РєР°: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public void getQuotesForBook(String bookId, QuotesLoadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                // 1. РџСЂРѕРІРµСЂРєР° Р°СѓС‚РµРЅС‚РёС„РёРєР°С†РёРё
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. РќР°СЃС‚СЂРѕР№РєР° СЃРѕРµРґРёРЅРµРЅРёСЏ
                String urlString = SUPABASE_URL + "/rest/v1/quotes?book_id=eq." + bookId + "&order=created_at.desc";
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // 3. РћР±СЂР°Р±РѕС‚РєР° РѕС‚РІРµС‚Р°
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONArray quotesArray = new JSONArray(response.toString());
                    List<Quote> quotes = new ArrayList<>();

                    for (int i = 0; i < quotesArray.length(); i++) {
                        JSONObject quoteJson = quotesArray.getJSONObject(i);
                        Quote quote = new Quote(
                                quoteJson.getString("id"),
                                quoteJson.getString("book_id"),
                                quoteJson.getString("user_id"),
                                quoteJson.getString("text"),
                                quoteJson.getInt("start_page"),
                                quoteJson.getInt("end_page"),
                                quoteJson.getLong("created_at")
                        );
                        quotes.add(quote);
                    }

                    notifySuccess(callback, quotes);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    notifyError(callback, "РћС€РёР±РєР° Р·Р°РіСЂСѓР·РєРё С†РёС‚Р°С‚: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                notifyError(callback, "РћС€РёР±РєР°: " + e.getMessage());
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

    public void deleteQuote(String quoteId, QuoteCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // 1. РџСЂРѕРІРµСЂРєР° Р°СѓС‚РµРЅС‚РёС„РёРєР°С†РёРё
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // 2. РќР°СЃС‚СЂРѕР№РєР° СЃРѕРµРґРёРЅРµРЅРёСЏ
                URL url = new URL(SUPABASE_URL + "/rest/v1/quotes?id=eq." + quoteId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Prefer", "return=minimal");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // 3. РћР±СЂР°Р±РѕС‚РєР° РѕС‚РІРµС‚Р°
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    notifySuccess(callback);
                } else {
                    String errorMsg = readErrorResponse(connection);
                    notifyError(callback, "РћС€РёР±РєР° СѓРґР°Р»РµРЅРёСЏ С†РёС‚Р°С‚С‹: " + responseCode + " - " + errorMsg);
                }
            } catch (Exception e) {
                notifyError(callback, "РћС€РёР±РєР°: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void notifySuccess(QuoteCallback callback) {
        mainHandler.post(callback::onSuccess);
    }

    private void notifyError(QuoteCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    private void notifySuccess(QuotesLoadCallback callback, List<Quote> quotes) {
        mainHandler.post(() -> callback.onSuccess(quotes));
    }

    private void notifyError(QuotesLoadCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    /**
     * Gets the current user ID from the JWT token
     * @return User ID or null if not authenticated
     */
    public String getUserId() {
        String accessToken = supabaseAuth.getAccessToken();
        if (accessToken == null) {
            return null;
        }
        return getUserIdFromToken(accessToken);
    }

    // Add new methods for shelf operations
    /**
     * Save a new shelf to Supabase
     * @param shelf The shelf to save
     * @param callback Callback for success/error
     */
    public void saveShelf(com.example.bookworm.models.Shelf shelf, ShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the access token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "saveShelf: User not authenticated");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // Check required fields
                if (shelf.getName() == null || shelf.getName().isEmpty()) {
                    Log.e(TAG, "saveShelf: Shelf name is required");
                    notifyError(callback, "Shelf name is required");
                    return;
                }

                if (shelf.getUserId() == null || shelf.getUserId().isEmpty()) {
                    Log.e(TAG, "saveShelf: User ID is required");
                    notifyError(callback, "User ID is required");
                    return;
                }

                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Prefer", "return=representation");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setDoOutput(true);

                // Create JSON payload
                JSONObject shelfJson = new JSONObject();
                shelfJson.put("id", shelf.getId());
                shelfJson.put("shelf_name", shelf.getName());
                shelfJson.put("shelf_description", shelf.getDescription());
                shelfJson.put("user_id", shelf.getUserId());

                String jsonPayload = shelfJson.toString();
                Log.d(TAG, "saveShelf: Sending payload: " + jsonPayload);

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "saveShelf: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response to get the created shelf
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        String responseStr = response.toString();
                        Log.d(TAG, "saveShelf: Response: " + responseStr);
                        
                        // Parse the response to get the created shelf
                        JSONArray shelvesArray = new JSONArray(responseStr);
                        if (shelvesArray.length() > 0) {
                            JSONObject savedShelfJson = shelvesArray.getJSONObject(0);
                            com.example.bookworm.models.Shelf savedShelf = new com.example.bookworm.models.Shelf();
                            savedShelf.setId(savedShelfJson.getString("id"));
                            savedShelf.setName(savedShelfJson.getString("shelf_name"));
                            savedShelf.setDescription(savedShelfJson.optString("shelf_description", ""));
                            savedShelf.setUserId(savedShelfJson.getString("user_id"));
                            
                            Log.d(TAG, "saveShelf: Successfully saved shelf with ID: " + savedShelf.getId());
                            notifySuccess(callback, savedShelf);
                        } else {
                            Log.e(TAG, "saveShelf: Response did not contain shelf data");
                            notifyError(callback, "Response did not contain shelf data");
                        }
                    }
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "saveShelf: Error saving shelf: " + errorResponse);
                    notifyError(callback, "Failed to save shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "saveShelf: Exception saving shelf to Supabase", e);
                notifyError(callback, "Error saving shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Update an existing shelf in Supabase
     * @param shelf The shelf to update
     * @param callback Callback for success/error
     */
    public void updateShelf(com.example.bookworm.models.Shelf shelf, ShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the access token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "updateShelf: User not authenticated");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // Check required fields
                if (shelf.getId() == null || shelf.getId().isEmpty()) {
                    Log.e(TAG, "updateShelf: Shelf ID is required");
                    notifyError(callback, "Shelf ID is required");
                    return;
                }

                if (shelf.getName() == null || shelf.getName().isEmpty()) {
                    Log.e(TAG, "updateShelf: Shelf name is required");
                    notifyError(callback, "Shelf name is required");
                    return;
                }

                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?id=eq." + shelf.getId());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Prefer", "return=representation");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setDoOutput(true);

                // Create JSON payload
                JSONObject shelfJson = new JSONObject();
                shelfJson.put("shelf_name", shelf.getName());
                shelfJson.put("shelf_description", shelf.getDescription());

                String jsonPayload = shelfJson.toString();
                Log.d(TAG, "updateShelf: Sending payload: " + jsonPayload);

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "updateShelf: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        String responseStr = response.toString();
                        Log.d(TAG, "updateShelf: Response: " + responseStr);
                        
                        // Parse the response to get the updated shelf
                        JSONArray shelvesArray = new JSONArray(responseStr);
                        if (shelvesArray.length() > 0) {
                            JSONObject updatedShelfJson = shelvesArray.getJSONObject(0);
                            com.example.bookworm.models.Shelf updatedShelf = new com.example.bookworm.models.Shelf();
                            updatedShelf.setId(updatedShelfJson.getString("id"));
                            updatedShelf.setName(updatedShelfJson.getString("shelf_name"));
                            updatedShelf.setDescription(updatedShelfJson.optString("shelf_description", ""));
                            updatedShelf.setUserId(updatedShelfJson.getString("user_id"));
                            
                            Log.d(TAG, "updateShelf: Successfully updated shelf with ID: " + updatedShelf.getId());
                            notifySuccess(callback, updatedShelf);
                        } else {
                            Log.e(TAG, "updateShelf: Response did not contain shelf data");
                            notifyError(callback, "Response did not contain shelf data");
                        }
                    }
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "updateShelf: Error updating shelf: " + errorResponse);
                    notifyError(callback, "Failed to update shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "updateShelf: Exception updating shelf in Supabase", e);
                notifyError(callback, "Error updating shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Delete a shelf from Supabase
     * @param shelfId The ID of the shelf to delete
     * @param callback Callback for success/error
     */
    public void deleteShelf(String shelfId, ShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the access token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "deleteShelf: User not authenticated");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                // Check required fields
                if (shelfId == null || shelfId.isEmpty()) {
                    Log.e(TAG, "deleteShelf: Shelf ID is required");
                    notifyError(callback, "Shelf ID is required");
                    return;
                }

                // First get the shelf so we can return it in the callback
                com.example.bookworm.models.Shelf shelf = null;
                try {
                    HttpURLConnection getConnection = null;
                    try {
                        URL getUrl = new URL(SUPABASE_URL + "/rest/v1/shelves?id=eq." + shelfId);
                        getConnection = (HttpURLConnection) getUrl.openConnection();
                        getConnection.setRequestMethod("GET");
                        getConnection.setRequestProperty("Content-Type", "application/json");
                        getConnection.setRequestProperty("apikey", SUPABASE_KEY);
                        getConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
                        
                        int getResponseCode = getConnection.getResponseCode();
                        
                        if (getResponseCode == HttpURLConnection.HTTP_OK) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getConnection.getInputStream()))) {
                                StringBuilder response = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    response.append(line);
                                }
                                
                                JSONArray shelvesArray = new JSONArray(response.toString());
                                if (shelvesArray.length() > 0) {
                                    JSONObject shelfJson = shelvesArray.getJSONObject(0);
                                    shelf = new com.example.bookworm.models.Shelf();
                                    shelf.setId(shelfJson.getString("id"));
                                    shelf.setName(shelfJson.getString("shelf_name"));
                                    shelf.setDescription(shelfJson.optString("shelf_description", ""));
                                    shelf.setUserId(shelfJson.getString("user_id"));
                                }
                            }
                        }
                    } finally {
                        if (getConnection != null) {
                            getConnection.disconnect();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "deleteShelf: Error getting shelf before delete", e);
                    // Continue with delete even if we can't get the shelf
                }

                // Now delete the shelf
                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?id=eq." + shelfId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "deleteShelf: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "deleteShelf: Successfully deleted shelf with ID: " + shelfId);
                    
                    // Return the shelf we retrieved earlier, or a new one with just the ID if we couldn't get it
                    if (shelf == null) {
                        shelf = new com.example.bookworm.models.Shelf();
                        shelf.setId(shelfId);
                    }
                    
                    final com.example.bookworm.models.Shelf finalShelf = shelf;
                    notifySuccess(callback, finalShelf);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "deleteShelf: Error deleting shelf: " + errorResponse);
                    notifyError(callback, "Failed to delete shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteShelf: Exception deleting shelf from Supabase", e);
                notifyError(callback, "Error deleting shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Add a book to a shelf in Supabase
     * @param bookId The book ID
     * @param shelfId The shelf ID
     * @param callback Callback for success/error
     */
    public void addBookToShelf(String bookId, String shelfId, BookShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (bookId == null || bookId.isEmpty()) {
                    Log.e(TAG, "addBookToShelf: Invalid book ID (null or empty)");
                    notifyError(callback, "Invalid book ID");
                    return;
                }
                
                if (shelfId == null || shelfId.isEmpty()) {
                    Log.e(TAG, "addBookToShelf: Invalid shelf ID (null or empty)");
                    notifyError(callback, "Invalid shelf ID");
                    return;
                }
                
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "addBookToShelf: User not authenticated (null access token)");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                Log.d(TAG, "addBookToShelf: Adding book " + bookId + " to shelf " + shelfId);
                
                URL url = new URL(SUPABASE_URL + "/rest/v1/book_shelf");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Prefer", "return=representation");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setDoOutput(true);

                // Create JSON payload
                JSONObject bookShelfJson = new JSONObject();
                bookShelfJson.put("book_id", bookId);
                bookShelfJson.put("shelf_id", shelfId);
                
                String jsonPayload = bookShelfJson.toString();
                Log.d(TAG, "addBookToShelf: Sending payload: " + jsonPayload);

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "addBookToShelf: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        Log.d(TAG, "addBookToShelf: Success response: " + response.toString());
                    } catch (Exception e) {
                        Log.w(TAG, "addBookToShelf: Could not read success response", e);
                    }
                    
                    Log.d(TAG, "addBookToShelf: Successfully added book to shelf");
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "addBookToShelf: Error adding book to shelf: " + errorResponse);
                    
                    // Check if it's a conflict error (book already in shelf)
                    if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
                        Log.d(TAG, "addBookToShelf: Book is already in the shelf (conflict)");
                        notifySuccess(callback); // Treat as success since the desired state is achieved
                    } else {
                        notifyError(callback, "Failed to add book to shelf: " + errorResponse);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "addBookToShelf: Exception adding book to shelf in Supabase", e);
                notifyError(callback, "Error adding book to shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Remove a book from a shelf in Supabase
     * @param bookId The book ID
     * @param shelfId The shelf ID
     * @param callback Callback for success/error
     */
    public void removeBookFromShelf(String bookId, String shelfId, BookShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (bookId == null || bookId.isEmpty()) {
                    Log.e(TAG, "removeBookFromShelf: Invalid book ID (null or empty)");
                    notifyError(callback, "Invalid book ID");
                    return;
                }
                
                if (shelfId == null || shelfId.isEmpty()) {
                    Log.e(TAG, "removeBookFromShelf: Invalid shelf ID (null or empty)");
                    notifyError(callback, "Invalid shelf ID");
                    return;
                }
                
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "removeBookFromShelf: User not authenticated (null access token)");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                Log.d(TAG, "removeBookFromShelf: Removing book " + bookId + " from shelf " + shelfId);
                
                URL url = new URL(SUPABASE_URL + "/rest/v1/book_shelf?book_id=eq." + bookId + "&shelf_id=eq." + shelfId);
                Log.d(TAG, "removeBookFromShelf: URL: " + url.toString());
                
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "removeBookFromShelf: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "removeBookFromShelf: Successfully removed book from shelf");
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "removeBookFromShelf: Error removing book from shelf: " + errorResponse + " (code: " + responseCode + ")");
                    
                    // Check if it's a not found error (book not in shelf)
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        Log.d(TAG, "removeBookFromShelf: Book was not in the shelf (not found)");
                        notifySuccess(callback); // Treat as success since the desired state is achieved
                    } else {
                        notifyError(callback, "Failed to remove book from shelf: " + errorResponse);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "removeBookFromShelf: Exception removing book from shelf in Supabase", e);
                notifyError(callback, "Error removing book from shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Get all shelves for a user from Supabase
     * @param callback Callback to return the shelves list
     */
    public void getShelves(ShelvesLoadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "getShelves: Failed - User not authenticated (null access token)");
                    notifyError(callback, "User not authenticated");
                    return;
                }
                
                String userId = supabaseAuth.getCurrentUserId();
                if (userId == null) {
                    Log.e(TAG, "getShelves: Failed - User ID not found (null user ID)");
                    notifyError(callback, "User ID not found");
                    return;
                }
                
                Log.d(TAG, "getShelves: Fetching shelves for user: " + userId);

                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?user_id=eq." + userId + "&order=shelf_name.asc");
                Log.d(TAG, "getShelves: URL: " + url.toString());
                
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "getShelves: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }

                        String responseStr = response.toString();
                        Log.d(TAG, "getShelves: Raw response: " + responseStr);

                        // Parse JSON response
                        JSONArray shelvesArray = new JSONArray(responseStr);
                        Log.d(TAG, "getShelves: Found " + shelvesArray.length() + " shelves");
                        List<com.example.bookworm.models.Shelf> shelves = new ArrayList<>();

                        for (int i = 0; i < shelvesArray.length(); i++) {
                            JSONObject shelfJson = shelvesArray.getJSONObject(i);
                            com.example.bookworm.models.Shelf shelf = new com.example.bookworm.models.Shelf();
                            
                            // Check for "id" or "shelf_id" field - database might use either name
                            String shelfId;
                            if (shelfJson.has("id")) {
                                shelfId = shelfJson.getString("id");
                                shelf.setId(shelfId);
                            } else if (shelfJson.has("shelf_id")) {
                                shelfId = shelfJson.getString("shelf_id");
                                shelf.setId(shelfId);
                            } else {
                                Log.e(TAG, "getShelves: Missing ID field in shelf: " + shelfJson);
                                continue; // Skip this shelf
                            }
                            
                            // Check for "name" or "shelf_name" field - database might use either name
                            if (shelfJson.has("name")) {
                                shelf.setName(shelfJson.getString("name"));
                            } else if (shelfJson.has("shelf_name")) {
                                shelf.setName(shelfJson.getString("shelf_name"));
                            } else {
                                Log.e(TAG, "getShelves: Missing name field in shelf: " + shelfJson);
                                shelf.setName("Unnamed Shelf");
                            }
                            
                            // Check for "description" or "shelf_description" field
                            if (shelfJson.has("description")) {
                                shelf.setDescription(shelfJson.optString("description", ""));
                            } else if (shelfJson.has("shelf_description")) {
                                shelf.setDescription(shelfJson.optString("shelf_description", ""));
                            } else {
                                shelf.setDescription("");
                            }
                            
                            // Check for "user_id" field
                            if (shelfJson.has("user_id")) {
                                shelf.setUserId(shelfJson.getString("user_id"));
                            } else {
                                shelf.setUserId(userId); // Use current user ID as fallback
                            }
                            
                            Log.d(TAG, "getShelves: Created shelf object: ID=" + shelf.getId() + ", Name=" + shelf.getName());
                            
                            // Get book IDs for this shelf
                            List<String> bookIds = getBookIdsForShelfSync(shelf.getId(), accessToken);
                            Log.d(TAG, "getShelves: Retrieved " + bookIds.size() + " book IDs for shelf " + shelf.getId());
                            shelf.setBookIds(bookIds);
                            
                            shelves.add(shelf);
                        }

                        Log.d(TAG, "getShelves: Successfully processed " + shelves.size() + " shelves");
                        notifySuccess(callback, shelves);
                    }
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "getShelves: Error getting shelves: " + errorResponse);
                    notifyError(callback, "Failed to get shelves: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "getShelves: Exception getting shelves from Supabase", e);
                notifyError(callback, "Error getting shelves: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Get book IDs for a shelf synchronously (for internal use)
     * @param shelfId The shelf ID
     * @param accessToken The access token
     * @return List of book IDs
     */
    private List<String> getBookIdsForShelfSync(String shelfId, String accessToken) {
        HttpURLConnection connection = null;
        List<String> bookIds = new ArrayList<>();
        
        Log.d(TAG, "getBookIdsForShelfSync: Retrieving book IDs for shelf: " + shelfId);
        
        try {
            // The correct URL to query the book_shelf table
            URL url = new URL(SUPABASE_URL + "/rest/v1/book_shelf?shelf_id=eq." + shelfId + "&select=shelf_id");
            Log.d(TAG, "getBookIdsForShelfSync: URL: " + url.toString());
            
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Get book IDs for shelf response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    String responseStr = response.toString();
                    Log.d(TAG, "getBookIdsForShelfSync: Raw response: " + responseStr);

                    // Parse JSON response
                    JSONArray bookIdsArray = new JSONArray(responseStr);
                    Log.d(TAG, "getBookIdsForShelfSync: Found " + bookIdsArray.length() + " book entries");
                    
                    for (int i = 0; i < bookIdsArray.length(); i++) {
                        JSONObject bookIdJson = bookIdsArray.getJSONObject(i);
                        if (bookIdJson.has("book_id")) {
                            String bookId = bookIdJson.getString("book_id");
                            bookIds.add(bookId);
                            Log.d(TAG, "getBookIdsForShelfSync: Adding book ID: " + bookId);
                        } else {
                            Log.e(TAG, "getBookIdsForShelfSync: Missing book_id field in response item: " + bookIdJson);
                        }
                    }
                }
            } else {
                String errorResponse = readErrorResponse(connection);
                Log.e(TAG, "Error getting book IDs for shelf: " + errorResponse);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting book IDs for shelf from Supabase", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        Log.d(TAG, "getBookIdsForShelfSync: Returning " + bookIds.size() + " book IDs for shelf " + shelfId);
        return bookIds;
    }

    /**
     * Get book IDs for a shelf
     * @param shelfId The shelf ID
     * @param callback Callback to return the book IDs
     */
    public void getBookIdsForShelf(String shelfId, final BooksIdsCallback callback) {
        executorService.execute(() -> {
            // Get the user id from the token
            String accessToken = supabaseAuth.getAccessToken();
            if (accessToken == null) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("User not authenticated"));
                }
                return;
            }
            
            List<String> bookIds = getBookIdsForShelfSync(shelfId, accessToken);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(bookIds));
            }
        });
    }

    /**
     * Get the current user ID (helper method)
     * @return The current user ID or null if not authenticated
     */
    public String getCurrentUserId() {
        try {
            String accessToken = supabaseAuth.getAccessToken();
            return accessToken != null ? getUserIdFromToken(accessToken) : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current user ID", e);
            return null;
        }
    }

    // Add notifySuccess and notifyError methods for new interfaces
        if (callback != null) {
            // Use the new method with a null shelf for backward compatibility
            mainHandler.post(() -> callback.onSuccess(null));
        }
    }

        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(ShelvesLoadCallback callback, List<com.example.bookworm.models.Shelf> shelves) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(shelves));
        }
    }

    private void notifyError(ShelvesLoadCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(BookShelfCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(BookShelfCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(BooksIdsCallback callback, List<String> bookIds) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(bookIds));
        }
    }

    private void notifyError(BooksIdsCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    /**
     * Get a shelf by its ID
     * @param shelfId The shelf ID
     * @param callback Callback to return the shelf
     */
    public void getShelfById(String shelfId, ShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "getShelfById: User not authenticated (null access token)");
                    notifyError(callback, "User not authenticated");
                    return;
                }

                Log.d(TAG, "getShelfById: Getting shelf with ID: " + shelfId);
                
                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?id=eq." + shelfId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "getShelfById: Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        Log.d(TAG, "getShelfById: Response: " + response.toString());
                        
                        JSONArray shelvesArray = new JSONArray(response.toString());
                        if (shelvesArray.length() > 0) {
                            JSONObject shelfJson = shelvesArray.getJSONObject(0);
                            Shelf shelf = new Shelf();
                            shelf.setId(shelfJson.getString("id"));
                            shelf.setName(shelfJson.getString("shelf_name")); // Note the field name
                            shelf.setDescription(shelfJson.optString("shelf_description", "")); // Note the field name
                            shelf.setUserId(shelfJson.getString("user_id"));
                            
                            // Get book IDs for this shelf
                            shelf.setBookIds(getBookIdsForShelfSync(shelf.getId(), accessToken));
                            
                            notifySuccess(callback, shelf);
                        } else {
                            Log.e(TAG, "getShelfById: Shelf not found with ID: " + shelfId);
                            notifyError(callback, "Shelf not found");
                        }
                    }
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "getShelfById: Error getting shelf: " + errorResponse);
                    notifyError(callback, "Failed to get shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "getShelfById: Exception getting shelf from Supabase", e);
                notifyError(callback, "Error getting shelf: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Get all books for a shelf
     * @param shelfId The shelf ID
     * @param callback Callback to return the books
     */
    public void getBooksForShelf(String shelfId, final BooksLoadCallback callback) {
        executorService.execute(() -> {
            // Get the user id from the token
            String accessToken = supabaseAuth.getAccessToken();
            if (accessToken == null) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("User not authenticated"));
                }
                return;
            }
            
            List<String> bookIds = getBookIdsForShelfSync(shelfId, accessToken);
            Log.d(TAG, "getBooksForShelf: Found " + bookIds.size() + " book IDs for shelf " + shelfId);
            
            if (bookIds.isEmpty()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                }
                return;
            }
            
            // Now get the books for these IDs
            List<Book> books = new ArrayList<>();
            List<String> failedBookIds = new ArrayList<>();
            
            for (String bookId : bookIds) {
                try {
                    // Fetch each book synchronously in the background thread
                    Book book = getBookByIdSync(bookId, accessToken);
                    if (book != null) {
                        books.add(book);
                        Log.d(TAG, "getBooksForShelf: Successfully loaded book: " + book.getTitle());
                    } else {
                        failedBookIds.add(bookId);
                        Log.e(TAG, "getBooksForShelf: Failed to load book with ID: " + bookId);
                    }
                } catch (Exception e) {
                    failedBookIds.add(bookId);
                    Log.e(TAG, "getBooksForShelf: Exception loading book with ID: " + bookId, e);
                }
            }
            
            if (!failedBookIds.isEmpty()) {
                Log.w(TAG, "getBooksForShelf: Failed to load " + failedBookIds.size() + " books out of " + bookIds.size());
            }
            
            final List<Book> finalBooks = books;
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(finalBooks));
            }
        });
    }

    /**
     * Get a book by ID synchronously (for internal use)
     */
    private Book getBookByIdSync(String bookId, String accessToken) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "getBookByIdSync: Response code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray booksArray = new JSONArray(response.toString());
                    if (booksArray.length() > 0) {
                        JSONObject bookJson = booksArray.getJSONObject(0);
                        Book book = new Book();
                        book.setId(bookJson.getString("id"));
                        book.setTitle(bookJson.optString("title", "Unknown Title"));
                        book.setAuthor(bookJson.optString("author", "Unknown Author"));
                        book.setDescription(bookJson.optString("description", ""));
                        book.setCoverPath(bookJson.optString("cover_image_url", ""));
                        book.setFilePath(bookJson.optString("file_url", ""));
                        book.setFileFormat(bookJson.optString("file_format", ""));
                        book.setStatus(bookJson.optString("status", "to-read"));
                        book.setCurrentPage(bookJson.optInt("current_page", 0));
                        book.setTotalPages(bookJson.optInt("page_count", 0));
                        book.setCreatedDate(bookJson.optString("created_at", ""));
                        
                        return book;
                    }
                }
            } else {
                String errorResponse = readErrorResponse(connection);
                Log.e(TAG, "getBookByIdSync: Error getting book: " + errorResponse);
            }
        } catch (Exception e) {
            Log.e(TAG, "getBookByIdSync: Exception getting book from Supabase", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Success notification for ShelfCallback
     */
    private void notifySuccess(ShelfCallback callback, Shelf shelf) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(shelf));
        }
    }

    /**
     * Error notification for ShelfCallback
     */
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }
}


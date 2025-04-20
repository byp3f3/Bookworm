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

    // Interface declarations
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

    public interface ShelfCallback {
        void onSuccess();
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

    // Notification methods for callbacks
    private void notifySuccess(BookSaveCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(BookSaveCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(FileUploadCallback callback, String url) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(url));
        }
    }

    private void notifyError(FileUploadCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(BooksLoadCallback callback, List<Book> books) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(books));
        }
    }

    private void notifyError(BooksLoadCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
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

    private void notifySuccess(BookCallback callback, Book book) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(book));
        }
    }

    private void notifyError(BookCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(QuoteCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(QuoteCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(QuotesLoadCallback callback, List<Quote> quotes) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(quotes));
        }
    }

    private void notifyError(QuotesLoadCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private void notifySuccess(ShelfCallback callback) {
        if (callback != null) {
            mainHandler.post(callback::onSuccess);
        }
    }

    private void notifyError(ShelfCallback callback, String error) {
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

    // Utility methods 
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

    private String readErrorResponse(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream == null) {
                return "Unknown error";
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } catch (Exception e) {
            return "Error reading response: " + e.getMessage();
        }
    }

    /**
     * Get the current user ID 
     * @return User ID or null if not authenticated
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

    // Add methods for shelf handling

    /**
     * Save a shelf to Supabase
     * @param shelf The shelf to save
     * @param callback Callback for success/error
     */
    public void saveShelf(com.example.bookworm.models.Shelf shelf, ShelfCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
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
                shelfJson.put("name", shelf.getName());
                shelfJson.put("description", shelf.getDescription());
                shelfJson.put("user_id", supabaseAuth.getCurrentUserId());

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = shelfJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Save shelf response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error saving shelf: " + errorResponse);
                    notifyError(callback, "Failed to save shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving shelf to Supabase", e);
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
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
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
                shelfJson.put("name", shelf.getName());
                shelfJson.put("description", shelf.getDescription());

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = shelfJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Update shelf response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error updating shelf: " + errorResponse);
                    notifyError(callback, "Failed to update shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating shelf in Supabase", e);
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
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?id=eq." + shelfId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Delete shelf response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error deleting shelf: " + errorResponse);
                    notifyError(callback, "Failed to delete shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting shelf from Supabase", e);
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
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

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

                // Write JSON to connection
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = bookShelfJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Add book to shelf response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error adding book to shelf: " + errorResponse);
                    notifyError(callback, "Failed to add book to shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding book to shelf in Supabase", e);
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
                // Get the user id from the token
                String accessToken = supabaseAuth.getAccessToken();
                if (accessToken == null) {
                    notifyError(callback, "User not authenticated");
                    return;
                }

                URL url = new URL(SUPABASE_URL + "/rest/v1/book_shelf?book_id=eq." + bookId + "&shelf_id=eq." + shelfId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Remove book from shelf response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_OK) {
                    notifySuccess(callback);
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error removing book from shelf: " + errorResponse);
                    notifyError(callback, "Failed to remove book from shelf: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error removing book from shelf in Supabase", e);
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
                    notifyError(callback, "User not authenticated");
                    return;
                }
                
                String userId = supabaseAuth.getCurrentUserId();
                if (userId == null) {
                    notifyError(callback, "User ID not found");
                    return;
                }

                URL url = new URL(SUPABASE_URL + "/rest/v1/shelves?user_id=eq." + userId + "&order=name.asc");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", SUPABASE_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Get shelves response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }

                        // Parse JSON response
                        JSONArray shelvesArray = new JSONArray(response.toString());
                        List<com.example.bookworm.models.Shelf> shelves = new ArrayList<>();

                        for (int i = 0; i < shelvesArray.length(); i++) {
                            JSONObject shelfJson = shelvesArray.getJSONObject(i);
                            com.example.bookworm.models.Shelf shelf = new com.example.bookworm.models.Shelf();
                            shelf.setId(shelfJson.getString("id"));
                            shelf.setName(shelfJson.getString("name"));
                            shelf.setDescription(shelfJson.optString("description", ""));
                            shelf.setUserId(shelfJson.getString("user_id"));
                            
                            // Get book IDs for this shelf
                            shelf.setBookIds(getBookIdsForShelfSync(shelf.getId(), accessToken));
                            
                            shelves.add(shelf);
                        }

                        notifySuccess(callback, shelves);
                    }
                } else {
                    String errorResponse = readErrorResponse(connection);
                    Log.e(TAG, "Error getting shelves: " + errorResponse);
                    notifyError(callback, "Failed to get shelves: " + errorResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting shelves from Supabase", e);
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
        
        try {
            URL url = new URL(SUPABASE_URL + "/rest/v1/book_shelf?shelf_id=eq." + shelfId + "&select=book_id");
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

                    // Parse JSON response
                    JSONArray bookIdsArray = new JSONArray(response.toString());
                    for (int i = 0; i < bookIdsArray.length(); i++) {
                        JSONObject bookIdJson = bookIdsArray.getJSONObject(i);
                        bookIds.add(bookIdJson.getString("book_id"));
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
} 
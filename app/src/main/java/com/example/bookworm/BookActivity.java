package com.example.bookworm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.text.ParseException;
import android.widget.SeekBar;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import org.json.JSONArray;
import android.view.Menu;
import android.view.MenuItem;
import android.app.Dialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.ArrayList;
import com.example.bookworm.adapters.ShelfSelectionAdapter;
import com.example.bookworm.models.Shelf;
import com.example.bookworm.services.SupabaseService;
import com.example.bookworm.SupabaseAuth;

public class BookActivity extends BaseActivity {
    private static final String TAG = "BookActivity";
    private String bookId;
    private String readingStatus;
    private int currentPage;
    private int totalPages;
    private TextView progressView;
    private SeekBar progressBar;
    private EditText progressEditText;
    private LinearLayout progressUpdateSection;
    private static final String SUPABASE_URL = "https://mfszyfmtujztqrjweixz.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mc3p5Zm10dWp6dHFyandlaXh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTkzNDAsImV4cCI6MjA1NzE3NTM0MH0.3URDTNl5T0R_TyWn6L0NlEFuLYoiH2qcQdYVNovFtVw";
    private static final String SUPABASE_TABLE = "books";
    private SupabaseService supabaseService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        // Initialize Supabase service
        supabaseService = new SupabaseService(this);

        // Настраиваем toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getIntent().getStringExtra("title"));

        // Get data from intent
        bookId = getIntent().getStringExtra("id");
        String bookTitle = getIntent().getStringExtra("title");
        String bookAuthor = getIntent().getStringExtra("author");
        String bookGenre = getIntent().getStringExtra("genre");
        readingStatus = getIntent().getStringExtra("status");
        currentPage = getIntent().getIntExtra("currentPage", 0);
        totalPages = getIntent().getIntExtra("totalPages", 0);
        int readingDays = getIntent().getIntExtra("readingDays", 0);
        String coverPath = getIntent().getStringExtra("coverPath");
        String startDate = getIntent().getStringExtra("startDate");
        String endDate = getIntent().getStringExtra("endDate");
        int rating = getIntent().getIntExtra("rating", 0);
        String review = getIntent().getStringExtra("review");

        // Always load details from Supabase to ensure we have the latest data
        if (bookId != null) {
            loadBookDetailsFromSupabase();
        }

        // Initialize views
        ImageView bookCover = findViewById(R.id.bookCover);
        TextView titleView = findViewById(R.id.bookTitle);
        TextView authorView = findViewById(R.id.bookAuthor);
        Spinner statusSpinner = findViewById(R.id.readingStatusSpinner);
        LinearLayout currentlyReadingSection = findViewById(R.id.currentlyReadingSection);
        LinearLayout finishedReadingSection = findViewById(R.id.finishedReadingSection);
        progressView = findViewById(R.id.readingProgress);
        progressBar = findViewById(R.id.progressBar);
        TextView daysView = findViewById(R.id.readingDays);
        progressEditText = findViewById(R.id.progressEditText);
        progressUpdateSection = findViewById(R.id.progressUpdateSection);
        Button updateProgressButton = findViewById(R.id.updateProgressButton);
        TextView datesView = findViewById(R.id.readingDates);
        TextView timeView = findViewById(R.id.totalReadingTime);
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        EditText reviewView = findViewById(R.id.bookReview);
        Button editReviewButton = findViewById(R.id.editReviewButton);
        Button saveReviewButton = findViewById(R.id.saveReviewButton);
        Button editBookButton = findViewById(R.id.editBookButton);
        Button deleteBookButton = findViewById(R.id.deleteBookButton);

        // Set book data
        titleView.setText(bookTitle);
        authorView.setText(bookAuthor);

        // Load cover image
        if (coverPath != null && !coverPath.isEmpty()) {
            Glide.with(this)
                    .load(coverPath)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(bookCover);
        } else {
            bookCover.setImageResource(R.drawable.ic_book_placeholder);
        }

        // Setup status spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.reading_status_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);
        statusSpinner.setEnabled(false); // Disable spinner interaction

        // Set current status
        if (readingStatus != null) {
            int spinnerPosition = adapter.getPosition(readingStatus);
            statusSpinner.setSelection(spinnerPosition);
        }

        // Update sections visibility based on status
        updateSectionsVisibility();

        // Load review from Supabase
        if (bookId != null) {
            loadBookDetailsFromSupabase();
        }

        // Setup click listeners
        bookCover.setOnClickListener(v -> openBookContent(bookTitle));

        // Setup review save button
        saveReviewButton.setOnClickListener(v -> {
            String newReview = reviewView.getText().toString().trim();
            saveReview(newReview);
        });

        editBookButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditBookActivity.class);
            intent.putExtra("id", bookId);
            intent.putExtra("title", bookTitle);
            intent.putExtra("author", bookAuthor);
            intent.putExtra("genre", getIntent().getStringExtra("genre"));
            intent.putExtra("status", readingStatus);
            intent.putExtra("currentPage", currentPage);
            intent.putExtra("totalPages", totalPages);
            intent.putExtra("startDate", getIntent().getStringExtra("startDate"));
            intent.putExtra("endDate", getIntent().getStringExtra("endDate"));
            intent.putExtra("coverPath", getIntent().getStringExtra("coverPath"));
            intent.putExtra("filePath", getIntent().getStringExtra("filePath"));
            intent.putExtra("fileFormat", getIntent().getStringExtra("fileFormat"));
            
            // Pass file_url as filePath if it exists
            String fileUrl = getIntent().getStringExtra("file_url");
            if (fileUrl != null) {
                intent.putExtra("file_url", fileUrl);
                // Also ensure filePath is set if it's null
                if (getIntent().getStringExtra("filePath") == null) {
                    intent.putExtra("filePath", fileUrl);
                }
            }
            
            startActivityForResult(intent, 1);
        });
        deleteBookButton.setOnClickListener(v -> showDeleteConfirmationDialog());
        
        // Setup progress bar
        progressBar.setMax(totalPages);
        progressBar.setProgress(currentPage);
        
        // Setup progress bar change listener
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Update progress text
                    progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                            progress, totalPages, totalPages > 0 ? (progress * 100 / totalPages) : 0));
                    
                    // Check if book is finished - but don't update status here, do it in onStopTrackingTouch
                    // to avoid premature status changes while dragging the seekbar
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Update progress in database and sync with Supabase
                int newPage = seekBar.getProgress();
                updateReadingProgress(newPage);
                
                // The status change will be handled in updateReadingProgress if needed
            }
        });
        
        // Setup progress update button (for manual input)
        updateProgressButton.setOnClickListener(v -> {
            String progressText = progressEditText.getText().toString();
            if (progressText.isEmpty()) {
                Toast.makeText(this, "Введите номер страницы", Toast.LENGTH_SHORT).show();
                return;
            }

            int newPage = Integer.parseInt(progressText);
            if (newPage < 0 || newPage > totalPages) {
                Toast.makeText(this, "Номер страницы должен быть от 0 до " + totalPages, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update progress bar
            progressBar.setProgress(newPage);
            
            // Update progress in database and sync with Supabase
            updateReadingProgress(newPage);
        });
    }

    private void loadBookDetailsFromSupabase() {
        supabaseService.getBookById(bookId, new SupabaseService.BookCallback() {
            @Override
            public void onSuccess(Book book) {
                if (book != null) {
                    // Update review if available
                    if (book.getReview() != null && !book.getReview().isEmpty()) {
                        final String supabaseReview = book.getReview();
                        
                                // Update intent
                                getIntent().putExtra("review", supabaseReview);
                                
                                // Update EditText
                                EditText reviewView = findViewById(R.id.bookReview);
                                    reviewView.setText(supabaseReview);
                        Log.d(TAG, "Loaded review from Supabase: " + supabaseReview);
                    }
                    
                    // Update rating
                    int rating = book.getRating();
                    if (rating > 0) {
                        RatingBar ratingBar = findViewById(R.id.ratingBar);
                        ratingBar.setRating(rating);
                        getIntent().putExtra("rating", rating);
                        Log.d(TAG, "Loaded rating from Supabase: " + rating);
                    }
                    
                    // Refresh UI based on new data
                    updateSectionsVisibility();
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load book details from Supabase: " + error);
            }
        });
    }

    private void updateReadingProgress(int newPage) {
        currentPage = newPage;

        // Update UI
        progressBar.setProgress(currentPage);
        progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                currentPage, totalPages, totalPages > 0 ? (currentPage * 100 / totalPages) : 0));
        
        // Save to Supabase
        supabaseService.updateBookProgress(bookId, currentPage, new SupabaseService.BookProgressCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(BookActivity.this, "Прогресс чтения обновлен", Toast.LENGTH_SHORT).show();
                
                // Update intent data
                getIntent().putExtra("currentPage", currentPage);

        // Check if book is finished
                if (currentPage >= totalPages && totalPages > 0) {
                    Log.d(TAG, "Book is completed - setting status to 'Прочитано'");
                    // Book is finished, update status to "Прочитано" regardless of current status
                    if (!"Прочитано".equals(readingStatus)) {
                        readingStatus = "Прочитано";
                        updateBookStatus(readingStatus);
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(BookActivity.this, "Ошибка обновления прогресса: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void syncReadingProgress() {
        if (bookId == null || bookId.isEmpty()) {
            Log.e(TAG, "Invalid book ID: null or empty");
            Toast.makeText(this, "Ошибка: неверный идентификатор книги", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // Construct the URL with the correct endpoint
                URL url = new URL(SUPABASE_URL + "/rest/v1/" + SUPABASE_TABLE + "?id=eq." + bookId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                // Set request method and headers
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("apikey", SUPABASE_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "return=minimal");
                conn.setDoOutput(true);

                // Create JSON data
                JSONObject data = new JSONObject();
                data.put("current_page", currentPage);
                data.put("updated_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(new java.util.Date()));

                // Write data to connection
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = data.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Get response
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    runOnUiThread(() -> Toast.makeText(BookActivity.this, 
                        "Прогресс синхронизирован", Toast.LENGTH_SHORT).show());
                } else {
                    // Read error response
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        throw new Exception("HTTP error code: " + responseCode + ", Response: " + response.toString());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync progress with Supabase: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(BookActivity.this, 
                        "Ошибка синхронизации с облаком: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save current reading position when leaving the activity
        if ("Читаю".equals(readingStatus)) {
            // Update progress in Supabase
            supabaseService.updateBookProgress(bookId, currentPage, new SupabaseService.BookProgressCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Reading progress updated in onPause");
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error updating reading progress in onPause: " + error);
                }
            });
        }
    }

    private void openBookContent(String bookTitle) {
        // Check if we have a local file path
        String localFilePath = getIntent().getStringExtra("filePath");
        String fileUrl = getIntent().getStringExtra("file_url");

        if (localFilePath != null && !localFilePath.isEmpty()) {
            // We have a local file, open it directly
            Log.d(TAG, "Opening book from local file: " + localFilePath);
            Intent intent = new Intent(this, BookReaderActivity.class);
            intent.putExtra("title", bookTitle);
            intent.putExtra("id", bookId);
            intent.putExtra("currentPage", currentPage);
            intent.putExtra("fileUri", Uri.parse(localFilePath));
            startActivity(intent);
            return;
        }
        
        // No local file, need to download from Supabase
        Log.d(TAG, "No local file path, attempting to download from Supabase");
        progressBar.setVisibility(ProgressBar.VISIBLE);
        
        // Use the book ID to fetch the file from Supabase
        if (bookId == null || bookId.isEmpty()) {
            Log.e(TAG, "Cannot download file: Book ID is null or empty");
            progressBar.setVisibility(ProgressBar.GONE);
            showNoContentDialog(bookTitle);
            return;
        }
        
        // Show download progress
        Toast.makeText(this, "Загрузка книги...", Toast.LENGTH_SHORT).show();
        
        // Run the download task in background
        new Thread(() -> {
            try {
                // Fetch the file URL from Supabase using the book ID
                URL url = new URL("https://mfszyfmtujztqrjweixz.supabase.co/rest/v1/books?select=file_url&id=eq." + bookId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mc3p5Zm10dWp6dHFyandlaXh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTkzNDAsImV4cCI6MjA1NzE3NTM0MH0.3URDTNl5T0R_TyWn6L0NlEFuLYoiH2qcQdYVNovFtVw");
                connection.setRequestProperty("Content-Type", "application/json");
                
                if (connection.getResponseCode() == 200) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                    
                    // Parse the JSON response to get the file URL
                    JSONArray jsonArray = new JSONArray(response.toString());
                    String fileUrlFromApi = null;
                    if (jsonArray.length() > 0) {
                        JSONObject book = jsonArray.getJSONObject(0);
                        if (book.has("file_url") && !book.isNull("file_url")) {
                            fileUrlFromApi = book.getString("file_url");
                        }
                    }
                    
                    if (fileUrlFromApi != null && !fileUrlFromApi.isEmpty()) {
                        final String finalFileUrl = fileUrlFromApi;
                        Log.d(TAG, "Found file URL from API: " + finalFileUrl);
                        
                        // Download the file from the URL
                        URL fileDownloadUrl = new URL(finalFileUrl);
                        connection = (HttpURLConnection) fileDownloadUrl.openConnection();
                        
                        // Create a temporary file to store the download
                        File tempDir = new File(getFilesDir(), "books");
                        if (!tempDir.exists()) {
                            tempDir.mkdirs();
                        }
                        
                        File tempFile = new File(tempDir, bookId + ".book");
                        
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            try (InputStream inputStream = connection.getInputStream();
                                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytesRead);
                                }
                            }
                            
                            // Update the file path in the intent
                            getIntent().putExtra("filePath", tempFile.getAbsolutePath());
                            
                            // Open the book reader with the downloaded file
                            runOnUiThread(() -> {
                                progressBar.setVisibility(ProgressBar.GONE);
                                Intent intent = new Intent(this, BookReaderActivity.class);
                                intent.putExtra("title", bookTitle);
                                intent.putExtra("id", bookId);
                                intent.putExtra("currentPage", currentPage);
                                intent.putExtra("fileUri", Uri.fromFile(tempFile));
                                startActivity(intent);
                            });
                            return;
                        } else {
                            Log.e(TAG, "Failed to download file, response code: " + connection.getResponseCode());
                        }
                    } else {
                        Log.e(TAG, "No file URL found in the API response");
                    }
                } else {
                    Log.e(TAG, "Failed to fetch file URL, response code: " + connection.getResponseCode());
                }
                
                // If we get here, something went wrong
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    showNoContentDialog(bookTitle);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error downloading book", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(BookActivity.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showNoContentDialog(bookTitle);
                });
            }
        }).start();
    }

    private void showNoContentDialog(String bookTitle) {
            new AlertDialog.Builder(this)
                    .setTitle("Содержание отсутствует")
                    .setMessage("Содержание книги отсутствует. Хотите добавить файл?")
                    .setPositiveButton("Добавить", (dialog, which) -> {
                        // Open file picker
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        String[] mimeTypes = {
                                "application/x-fictionbook+xml",
                            "application/epub+zip"
                        };
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(intent, 1);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            // Update book details from edit activity
            bookId = data.getStringExtra("id");
            String newTitle = data.getStringExtra("title");
            String newAuthor = data.getStringExtra("author");
            String newStatus = data.getStringExtra("status");
            int newCurrentPage = data.getIntExtra("currentPage", currentPage); // Use current value as fallback
            int newTotalPages = data.getIntExtra("totalPages", totalPages); // Use current value as fallback
            String newStartDate = data.getStringExtra("startDate");
            String newEndDate = data.getStringExtra("endDate");
            String newCoverPath = data.getStringExtra("coverPath");
            int newRating = data.getIntExtra("rating", getIntent().getIntExtra("rating", 0));
            String newReview = data.getStringExtra("review");
            String newFilePath = data.getStringExtra("filePath");
            String newFileFormat = data.getStringExtra("fileFormat");
            String newFileUrl = data.getStringExtra("file_url");
            
            // Update class fields
            currentPage = newCurrentPage;
            totalPages = newTotalPages;
            readingStatus = newStatus;

            // Store values in the intent for future use
            getIntent().putExtra("title", newTitle);
            getIntent().putExtra("author", newAuthor);
            getIntent().putExtra("status", newStatus);
            getIntent().putExtra("currentPage", newCurrentPage);
            getIntent().putExtra("totalPages", newTotalPages);
            getIntent().putExtra("startDate", newStartDate);
            getIntent().putExtra("endDate", newEndDate);
            getIntent().putExtra("coverPath", newCoverPath);
            getIntent().putExtra("rating", newRating);
            getIntent().putExtra("filePath", newFilePath);
            getIntent().putExtra("fileFormat", newFileFormat);
            getIntent().putExtra("file_url", newFileUrl);
            
            if (newReview != null) {
            getIntent().putExtra("review", newReview);
            }

            // Update UI
            TextView titleView = findViewById(R.id.bookTitle);
            TextView authorView = findViewById(R.id.bookAuthor);
            ImageView bookCover = findViewById(R.id.bookCover);
            Spinner statusSpinner = findViewById(R.id.readingStatusSpinner);

            // Update basic information
            if (newTitle != null) titleView.setText(newTitle);
            if (newAuthor != null) authorView.setText(newAuthor);

            // Update cover image
            if (newCoverPath != null && !newCoverPath.isEmpty()) {
                Glide.with(this)
                        .load(newCoverPath)
                        .placeholder(R.drawable.ic_book_placeholder)
                        .error(R.drawable.ic_book_placeholder)
                        .into(bookCover);
            }

            // Update status spinner
            if (newStatus != null) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) statusSpinner.getAdapter();
                int spinnerPosition = adapter.getPosition(newStatus);
            statusSpinner.setSelection(spinnerPosition);
            }

            // Update progress bar
            progressBar.setMax(newTotalPages);
            progressBar.setProgress(newCurrentPage);
            progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                newCurrentPage, newTotalPages, newTotalPages > 0 ? (newCurrentPage * 100 / newTotalPages) : 0));

            // Update sections visibility based on new status
            updateSectionsVisibility();

            // Set result to refresh library
            Intent resultIntent = new Intent();
            resultIntent.putExtra("refresh", true);
            setResult(RESULT_OK, resultIntent);
        }
    }

    private void updateSectionsVisibility() {
        LinearLayout currentlyReadingSection = findViewById(R.id.currentlyReadingSection);
        LinearLayout finishedReadingSection = findViewById(R.id.finishedReadingSection);
        TextView progressView = findViewById(R.id.readingProgress);
        SeekBar progressBar = findViewById(R.id.progressBar);
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        EditText reviewView = findViewById(R.id.bookReview);
        Button editReviewButton = findViewById(R.id.editReviewButton);
        Button saveReviewButton = findViewById(R.id.saveReviewButton);
        TextView datesView = findViewById(R.id.readingDates);
        TextView timeView = findViewById(R.id.totalReadingTime);
        TextView daysView = findViewById(R.id.readingDays);

        // Get data from intent
        String startDate = getIntent().getStringExtra("startDate");
        String endDate = getIntent().getStringExtra("endDate");
        int rating = getIntent().getIntExtra("rating", 0);
        String review = getIntent().getStringExtra("review");

        Log.d(TAG, "updateSectionsVisibility - Review from intent: " + (review != null ? review : "null"));
        Log.d(TAG, "updateSectionsVisibility - Rating from intent: " + rating);

        // Initialize date formatters
        SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayDateFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));

        if ("Читаю".equals(readingStatus)) {
            currentlyReadingSection.setVisibility(View.VISIBLE);
            finishedReadingSection.setVisibility(View.GONE);
            
            // Update progress info
            progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                    currentPage, totalPages, totalPages > 0 ? (currentPage * 100 / totalPages) : 0));
            progressBar.setMax(totalPages);
            progressBar.setProgress(currentPage);
            
            // Update reading days
            if (startDate != null && !startDate.isEmpty()) {
                try {
                    Date date = dbDateFormat.parse(startDate);
                    String formattedDate = displayDateFormat.format(date);
                    daysView.setText(String.format("Читаю с %s", formattedDate));
                } catch (ParseException e) {
                    daysView.setText(String.format("Читаю %d дней", calculateReadingDays(startDate)));
                }
            }
            
            // Hide rating and review
            ratingBar.setVisibility(View.GONE);
            reviewView.setVisibility(View.GONE);
            editReviewButton.setVisibility(View.GONE);
            saveReviewButton.setVisibility(View.GONE);
        } else if ("Прочитано".equals(readingStatus)) {
            currentlyReadingSection.setVisibility(View.GONE);
            finishedReadingSection.setVisibility(View.VISIBLE);
            
            // Update dates display with formatted dates
            if (startDate != null && endDate != null) {
                try {
                    Date start = dbDateFormat.parse(startDate);
                    Date end = dbDateFormat.parse(endDate);
                    String formattedStartDate = displayDateFormat.format(start);
                    String formattedEndDate = displayDateFormat.format(end);
                    datesView.setText(String.format("Даты чтения: %s - %s", formattedStartDate, formattedEndDate));
                    timeView.setText(String.format("Всего дней чтения: %d", calculateReadingDays(startDate, endDate)));
                } catch (ParseException e) {
                    datesView.setText(String.format("Даты чтения: %s - %s", startDate, endDate));
                    timeView.setText(String.format("Всего дней чтения: %d", calculateReadingDays(startDate, endDate)));
                }
            }
            
            // Show rating
            ratingBar.setVisibility(View.VISIBLE);
            ratingBar.setRating(rating);
            
            // Setup rating change listener
            ratingBar.setOnRatingBarChangeListener((rBar, value, fromUser) -> {
                if (fromUser) {
                    saveRating((int)value);
                }
            });
            
            // Show review
            reviewView.setVisibility(View.VISIBLE);
            reviewView.clearFocus(); // Ensure edittext is not focused initially
            
            if (review != null && !review.isEmpty()) {
                reviewView.setText(review);
                Log.d(TAG, "Setting review text to: " + review);
            } else {
                reviewView.setText("");
                reviewView.setHint("Напишите рецензию");
                Log.d(TAG, "No review found, setting empty text");
            }
            
            // Setup review save button
            saveReviewButton.setVisibility(View.VISIBLE);
            saveReviewButton.setOnClickListener(v -> {
                String newReview = reviewView.getText().toString().trim();
                saveReview(newReview);
            });
            
            // Hide edit button since we now have direct editing
            editReviewButton.setVisibility(View.GONE);
        } else if ("В планах".equals(readingStatus)) {
            currentlyReadingSection.setVisibility(View.GONE);
            finishedReadingSection.setVisibility(View.GONE);
            ratingBar.setVisibility(View.GONE);
            reviewView.setVisibility(View.GONE);
            editReviewButton.setVisibility(View.GONE);
            saveReviewButton.setVisibility(View.GONE);
        }
    }

    private int calculateReadingDays(String startDate) {
        return calculateReadingDays(startDate, new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
    }

    private int calculateReadingDays(String startDate, String endDate) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date start = sdf.parse(startDate);
            java.util.Date end = sdf.parse(endDate);
            long diffInMillies = Math.abs(end.getTime() - start.getTime());
            return (int) (diffInMillies / (1000 * 60 * 60 * 24)) + 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveRating(int rating) {
        supabaseService.saveBook(createUpdatedBookWithRating(rating), new SupabaseService.BookSaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(BookActivity.this, "Рейтинг сохранен", Toast.LENGTH_SHORT).show();
        getIntent().putExtra("rating", rating);
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(BookActivity.this, "Ошибка сохранения рейтинга: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private Book createUpdatedBookWithRating(int rating) {
        Book book = new Book();
        book.setId(bookId);
        book.setTitle(getIntent().getStringExtra("title"));
        book.setAuthor(getIntent().getStringExtra("author"));
        book.setStatus(readingStatus);
        book.setCurrentPage(currentPage);
        book.setTotalPages(totalPages);
        book.setStartDate(getIntent().getStringExtra("startDate"));
        book.setEndDate(getIntent().getStringExtra("endDate"));
        book.setCoverPath(getIntent().getStringExtra("coverPath"));
        book.setRating(rating);
        book.setReview(getIntent().getStringExtra("review"));
        
        // Critical: preserve file information
        String fileUrl = getIntent().getStringExtra("file_url");
        String filePath = getIntent().getStringExtra("filePath");
        String fileFormat = getIntent().getStringExtra("fileFormat");
        
        if (fileUrl != null && !fileUrl.isEmpty()) {
            Log.d(TAG, "Rating saving - Setting file path from file_url: " + fileUrl);
            book.setFilePath(fileUrl);
        } else if (filePath != null && !filePath.isEmpty()) {
            Log.d(TAG, "Rating saving - Setting file path: " + filePath);
            book.setFilePath(filePath);
        } else {
            Log.e(TAG, "Rating saving - No file URL or path found for book");
        }
        
        if (fileFormat != null) {
            book.setFileFormat(fileFormat);
        }
        
        return book;
    }
    
    private Book createUpdatedBookWithReview(String review) {
        Book book = new Book();
        book.setId(bookId);
        book.setTitle(getIntent().getStringExtra("title"));
        book.setAuthor(getIntent().getStringExtra("author"));
        book.setStatus(readingStatus);
        book.setCurrentPage(currentPage);
        book.setTotalPages(totalPages);
        book.setStartDate(getIntent().getStringExtra("startDate"));
        book.setEndDate(getIntent().getStringExtra("endDate"));
        book.setCoverPath(getIntent().getStringExtra("coverPath"));
        book.setRating(getIntent().getIntExtra("rating", 0));
        book.setReview(review);
        
        // Critical: preserve file information
        String fileUrl = getIntent().getStringExtra("file_url");
        String filePath = getIntent().getStringExtra("filePath");
        String fileFormat = getIntent().getStringExtra("fileFormat");
        
        if (fileUrl != null && !fileUrl.isEmpty()) {
            Log.d(TAG, "Review saving - Setting file path from file_url: " + fileUrl);
            book.setFilePath(fileUrl);
        } else if (filePath != null && !filePath.isEmpty()) {
            Log.d(TAG, "Review saving - Setting file path: " + filePath);
            book.setFilePath(filePath);
        } else {
            Log.e(TAG, "Review saving - No file URL or path found for book");
        }
        
        if (fileFormat != null) {
            book.setFileFormat(fileFormat);
        }
        
        return book;
    }

    private void saveReview(String review) {
        supabaseService.saveBook(createUpdatedBookWithReview(review), new SupabaseService.BookSaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(BookActivity.this, "Рецензия сохранена", Toast.LENGTH_SHORT).show();
                getIntent().putExtra("review", review);
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(BookActivity.this, "Ошибка сохранения рецензии: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_delete_confirmation, null);
        
        TextView textViewDeleteTitle = view.findViewById(R.id.textViewDeleteTitle);
        TextView textViewDeleteMessage = view.findViewById(R.id.textViewDeleteMessage);
        Button buttonCancel = view.findViewById(R.id.buttonCancel);
        Button buttonDelete = view.findViewById(R.id.buttonDelete);
        
        textViewDeleteTitle.setText("Удалить книгу");
        textViewDeleteMessage.setText(String.format(
                "Вы уверены, что хотите удалить книгу \"%s\"?",
                getIntent().getStringExtra("title")));
        
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        
        buttonDelete.setOnClickListener(v -> {
            dialog.dismiss();
                    deleteBookFromSupabase();
        });
        
        dialog.show();
    }

    private void deleteBookFromSupabase() {
        // Show loading dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Удаление книги...");
        builder.setCancelable(false);
        AlertDialog loadingDialog = builder.create();
        loadingDialog.show();
        
        // Delete the book from Supabase
        new Thread(() -> {
            try {
                // Construct the URL with the correct endpoint
                URL url = new URL(SUPABASE_URL + "/rest/v1/books?id=eq." + bookId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                // Set request method and headers
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("apikey", SUPABASE_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + getSupabaseAuth().getAccessToken());
                
                // Get response
                int responseCode = conn.getResponseCode();
                
                    runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    
                    if (responseCode == HttpURLConnection.HTTP_OK || 
                        responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        Toast.makeText(BookActivity.this, "Книга удалена", Toast.LENGTH_SHORT).show();
                        finish(); // Close the activity
                } else {
                        Toast.makeText(BookActivity.this, 
                            "Ошибка удаления книги: код " + responseCode, 
                            Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete book: " + e.getMessage());
                
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(BookActivity.this, 
                        "Ошибка удаления книги: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateBookStatus(String newStatus) {
                    readingStatus = newStatus;
                    
        // Update UI to reflect new status
        ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) 
                ((Spinner) findViewById(R.id.readingStatusSpinner)).getAdapter();
        int spinnerPosition = adapter.getPosition(readingStatus);
        ((Spinner) findViewById(R.id.readingStatusSpinner)).setSelection(spinnerPosition);
        
        // Set dates based on status
        SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dbDateFormat.format(new Date());
        
        // Get current dates from intent
        String startDate = getIntent().getStringExtra("startDate");
        String endDate = getIntent().getStringExtra("endDate");
        
        // Always ensure we have valid dates based on the book status
        if ("Читаю".equals(readingStatus)) {
            // For "Читаю" status, we need a start date but no end date
            if (startDate == null || startDate.isEmpty() || "null".equals(startDate)) {
                startDate = currentDate;
            }
            // Clear end date for books being read
            endDate = null;
        } 
        else if ("Прочитано".equals(readingStatus)) {
            // For "Прочитано" status, we need both start and end date
            if (startDate == null || startDate.isEmpty() || "null".equals(startDate)) {
                startDate = currentDate;
            }
            if (endDate == null || endDate.isEmpty() || "null".equals(endDate)) {
                endDate = currentDate;
            }
        }
        else if ("В планах".equals(readingStatus)) {
            // For planned books, dates are optional
            // Keep existing dates if any
        }
        
        // Save dates to intent for future reference
        getIntent().putExtra("startDate", startDate);
        if (endDate != null) {
            getIntent().putExtra("endDate", endDate);
        }
        
        // Update book in Supabase
        Book book = new Book();
        book.setId(bookId);
        book.setTitle(getIntent().getStringExtra("title"));
        book.setAuthor(getIntent().getStringExtra("author"));
        book.setStatus(readingStatus);
        book.setCurrentPage(currentPage);
        book.setTotalPages(totalPages);
        book.setStartDate(startDate);
        book.setEndDate(endDate);
        book.setCoverPath(getIntent().getStringExtra("coverPath"));
        book.setRating(getIntent().getIntExtra("rating", 0));
        book.setReview(getIntent().getStringExtra("review"));
        
        // Critical: preserve file information
        String fileUrl = getIntent().getStringExtra("file_url");
        String filePath = getIntent().getStringExtra("filePath");
        String fileFormat = getIntent().getStringExtra("fileFormat");
        
        if (fileUrl != null && !fileUrl.isEmpty()) {
            Log.d(TAG, "Setting file path from file_url: " + fileUrl);
            book.setFilePath(fileUrl);
        } else if (filePath != null && !filePath.isEmpty()) {
            Log.d(TAG, "Setting file path: " + filePath);
            book.setFilePath(filePath);
        } else {
            Log.e(TAG, "No file URL or path found for book");
        }
        
        if (fileFormat != null) {
            book.setFileFormat(fileFormat);
        }
        
        Log.d(TAG, "Updating book status to: " + readingStatus);
        Log.d(TAG, "Book file path: " + book.getFilePath());
        Log.d(TAG, "Start date: " + startDate);
        Log.d(TAG, "End date: " + endDate);
        
        supabaseService.saveBook(book, new SupabaseService.BookSaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(BookActivity.this, "Статус обновлен", Toast.LENGTH_SHORT).show();
                updateSectionsVisibility();
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(BookActivity.this, "Ошибка обновления статуса: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error updating book status: " + error);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.menu_add_to_shelf) {
            showAddToShelfDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showAddToShelfDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_to_shelf);
        
        RecyclerView recyclerViewShelves = dialog.findViewById(R.id.recyclerViewShelves);
        TextView textViewNoShelves = dialog.findViewById(R.id.textViewNoShelves);
        Button buttonSave = dialog.findViewById(R.id.buttonClose);
        ProgressBar progressBar = dialog.findViewById(R.id.progressBar);
        
        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);
        textViewNoShelves.setVisibility(View.GONE);
        recyclerViewShelves.setVisibility(View.GONE);
        
        // Get SupabaseService and SupabaseAuth instances
        SupabaseService supabaseService = new SupabaseService(this);
        SupabaseAuth supabaseAuth = new SupabaseAuth(this);
        
        // Get current user ID from Supabase
        String currentUserId = supabaseAuth.getCurrentUserId();
        if (currentUserId == null) {
            Log.e(TAG, "Failed to get current user ID");
            textViewNoShelves.setText("Вы не авторизованы. Пожалуйста, войдите в систему.");
            textViewNoShelves.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            return;
        }
        
        Log.d(TAG, "Loading shelves from Supabase for user: " + currentUserId);
        
        // Get all shelves for the current user directly from Supabase
        supabaseService.getShelves(new SupabaseService.ShelvesLoadCallback() {
            @Override
            public void onSuccess(List<com.example.bookworm.models.Shelf> shelves) {
                Log.d(TAG, "Successfully loaded " + shelves.size() + " shelves from Supabase");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (shelves.isEmpty()) {
                        textViewNoShelves.setVisibility(View.VISIBLE);
                        recyclerViewShelves.setVisibility(View.GONE);
                    } else {
                        textViewNoShelves.setVisibility(View.GONE);
                        recyclerViewShelves.setVisibility(View.VISIBLE);
                        
                        // Convert to list of shelf IDs
                        List<String> bookShelfIds = new ArrayList<>();
                        for (com.example.bookworm.models.Shelf shelf : shelves) {
                            if (shelf.getBookIds() != null && shelf.getBookIds().contains(bookId)) {
                                bookShelfIds.add(shelf.getId());
                            }
                        }
                        
                        // Create adapter for shelf selection
                        ShelfSelectionAdapter adapter = new ShelfSelectionAdapter(
                                BookActivity.this, 
                                shelves, 
                                bookShelfIds,
                                (shelf, isChecked) -> {
                                    // Use Supabase to add/remove book from shelf
                                    if (isChecked) {
                                        Log.d(TAG, "Adding book " + bookId + " to shelf " + shelf.getId());
                                        supabaseService.addBookToShelf(bookId, shelf.getId(), new SupabaseService.BookShelfCallback() {
                                            @Override
                                            public void onSuccess() {
                                                Log.d(TAG, "Successfully added book to shelf in Supabase");
                                            }
                                            
                                            @Override
                                            public void onError(String error) {
                                                Log.e(TAG, "Error adding book to shelf: " + error);
                                                Toast.makeText(BookActivity.this, "Ошибка при добавлении книги на полку", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } else {
                                        Log.d(TAG, "Removing book " + bookId + " from shelf " + shelf.getId());
                                        supabaseService.removeBookFromShelf(bookId, shelf.getId(), new SupabaseService.BookShelfCallback() {
                                            @Override
                                            public void onSuccess() {
                                                Log.d(TAG, "Successfully removed book from shelf in Supabase");
                                            }
                                            
                                            @Override
                                            public void onError(String error) {
                                                Log.e(TAG, "Error removing book from shelf: " + error);
                                                Toast.makeText(BookActivity.this, "Ошибка при удалении книги с полки", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                });
                        
                        recyclerViewShelves.setAdapter(adapter);
                        recyclerViewShelves.setLayoutManager(new LinearLayoutManager(BookActivity.this));
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading shelves from Supabase: " + error);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    textViewNoShelves.setText("Ошибка загрузки полок: " + error);
                    textViewNoShelves.setVisibility(View.VISIBLE);
                    recyclerViewShelves.setVisibility(View.GONE);
                });
            }
        });
        
        buttonSave.setOnClickListener(v -> {
            Toast.makeText(this, "Изменения сохранены", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseService != null) {
            supabaseService.shutdown();
        }
    }
}
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

public class BookActivity extends BaseActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        // Get book data from intent
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

        // If review is not in intent, load it from database
        if (review == null && bookId != null) {
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            String dbReview = dbHelper.getBookReview(bookId);
            if (dbReview != null) {
                getIntent().putExtra("review", dbReview);
                review = dbReview;
            }
            dbHelper.close();
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
            loadReviewFromSupabase();
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
                    
                    // Check if book is finished
                    if (progress >= totalPages && totalPages > 0) {
                        // Book is finished, update status to "Прочитано"
                        updateBookStatus("Прочитано");
                    }
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

    private void loadReviewFromSupabase() {
        new Thread(() -> {
            try {
                // Construct the URL with the correct endpoint
                URL url = new URL(SUPABASE_URL + "/rest/v1/" + SUPABASE_TABLE + "?id=eq." + bookId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                // Set request method and headers
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", SUPABASE_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                
                // Get response
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    // Parse JSON response
                    org.json.JSONArray jsonArray = new org.json.JSONArray(response.toString());
                    if (jsonArray.length() > 0) {
                        org.json.JSONObject bookData = jsonArray.getJSONObject(0);
                        if (bookData.has("review") && !bookData.isNull("review")) {
                            final String supabaseReview = bookData.getString("review");
                            
                            // Update UI on main thread
                            runOnUiThread(() -> {
                                // Update intent
                                getIntent().putExtra("review", supabaseReview);
                                
                                // Update EditText
                                EditText reviewView = findViewById(R.id.bookReview);
                                if (supabaseReview != null && !supabaseReview.isEmpty()) {
                                    reviewView.setText(supabaseReview);
                                    reviewView.setVisibility(View.VISIBLE);
                                }
                                
                                // Save to local database
                                DatabaseHelper dbHelper = new DatabaseHelper(BookActivity.this);
                                dbHelper.updateBookReview(bookId, supabaseReview);
                                dbHelper.close();
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("BookActivity", "Failed to load review from Supabase: " + e.getMessage());
            }
        }).start();
    }

    private void updateReadingProgress(int newPage) {
        // Update progress in database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.updateBookProgress(bookId, newPage);
        dbHelper.close();

        // Update UI
        currentPage = newPage;
        progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                currentPage, totalPages, totalPages > 0 ? (currentPage * 100 / totalPages) : 0));
        progressBar.setProgress(currentPage);
        progressUpdateSection.setVisibility(View.GONE);

        // Check if book is finished
        if (currentPage >= totalPages && totalPages > 0 && "Читаю".equals(readingStatus)) {
            // Book is finished, update status to "Прочитано"
            updateBookStatus("Прочитано");
        }

        // Sync with Supabase
        syncReadingProgress();
    }

    private void syncReadingProgress() {
        if (bookId == null || bookId.isEmpty()) {
            Log.e("BookActivity", "Invalid book ID: null or empty");
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
                Log.e("BookActivity", "Failed to sync progress with Supabase: " + e.getMessage());
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
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            dbHelper.updateBookProgress(bookId, currentPage);
            dbHelper.close();
            syncReadingProgress();
        }
    }

    private void openBookContent(String bookTitle) {
        // Check if book content exists
        boolean hasContent = false; // Replace with actual check

        if (hasContent) {
            Intent intent = new Intent(this, BookReaderActivity.class);
            intent.putExtra("title", bookTitle);
            startActivity(intent);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Содержание отсутствует")
                    .setMessage("Содержание книги отсутствует. Хотите добавить файл?")
                    .setPositiveButton("Добавить", (dialog, which) -> {
                        // Open file picker
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        String[] mimeTypes = {
                                "application/pdf",
                                "application/x-fictionbook+xml",
                                "application/epub+zip",
                                "text/plain"
                        };
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(intent, 1);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            // Update book details from edit activity
            bookId = data.getStringExtra("id");
            String newTitle = data.getStringExtra("title");
            String newAuthor = data.getStringExtra("author");
            String newGenre = data.getStringExtra("genre");
            readingStatus = data.getStringExtra("status");
            currentPage = data.getIntExtra("currentPage", 0);
            totalPages = data.getIntExtra("totalPages", 0);
            String newStartDate = data.getStringExtra("startDate");
            String newEndDate = data.getStringExtra("endDate");
            String newCoverPath = data.getStringExtra("coverPath");
            int newRating = data.getIntExtra("rating", 0);
            String newReview = data.getStringExtra("review");

            // Store dates in the intent for future use
            getIntent().putExtra("startDate", newStartDate);
            getIntent().putExtra("endDate", newEndDate);
            getIntent().putExtra("rating", newRating);
            getIntent().putExtra("review", newReview);

            // Update UI
            TextView titleView = findViewById(R.id.bookTitle);
            TextView authorView = findViewById(R.id.bookAuthor);
            ImageView bookCover = findViewById(R.id.bookCover);
            Spinner statusSpinner = findViewById(R.id.readingStatusSpinner);
            TextView datesView = findViewById(R.id.readingDates);
            TextView timeView = findViewById(R.id.totalReadingTime);
            TextView daysView = findViewById(R.id.readingDays);
            RatingBar ratingBar = findViewById(R.id.ratingBar);
            EditText reviewView = findViewById(R.id.bookReview);

            titleView.setText(newTitle);
            authorView.setText(newAuthor);

            if (newCoverPath != null && !newCoverPath.isEmpty()) {
                Glide.with(this)
                        .load(newCoverPath)
                        .placeholder(R.drawable.ic_book_placeholder)
                        .error(R.drawable.ic_book_placeholder)
                        .into(bookCover);
            }

            // Update status spinner
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) statusSpinner.getAdapter();
            int spinnerPosition = adapter.getPosition(readingStatus);
            statusSpinner.setSelection(spinnerPosition);

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
            if (review != null && !review.isEmpty()) {
                reviewView.setText(review);
            } else {
                reviewView.setText("");
                reviewView.setHint("Напишите рецензию");
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
        // Save rating to database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.updateBookRating(bookId, rating);
        dbHelper.close();
        
        // Update intent
        getIntent().putExtra("rating", rating);
        
        // Sync with Supabase
        syncRatingAndReview(rating, getIntent().getStringExtra("review"));
        
        Toast.makeText(this, "Оценка сохранена", Toast.LENGTH_SHORT).show();
    }
    
    private void showEditReviewDialog(String currentReview) {
        // Create an EditText for the dialog
        final EditText input = new EditText(this);
        input.setHint("Напишите рецензию");
        input.setLines(5);
        input.setMaxLines(10);
        
        if (currentReview != null) {
            input.setText(currentReview);
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Рецензия на книгу")
                .setView(input)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String newReview = input.getText().toString().trim();
                    saveReview(newReview);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    private void saveReview(String review) {
        // Save review to database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.updateBookReview(bookId, review);
        dbHelper.close();
        
        // Update intent
        getIntent().putExtra("review", review);
        
        // Update UI
        EditText reviewView = findViewById(R.id.bookReview);
        if (review != null && !review.isEmpty()) {
            reviewView.setText(review);
            reviewView.setVisibility(View.VISIBLE);
        } else {
            reviewView.setText("");
            reviewView.setHint("Напишите рецензию");
        }
        
        // Sync with Supabase
        syncRatingAndReview(getIntent().getIntExtra("rating", 0), review);
        
        Toast.makeText(this, "Рецензия сохранена", Toast.LENGTH_SHORT).show();
    }
    
    private void syncRatingAndReview(int rating, String review) {
        if (bookId == null || bookId.isEmpty()) {
            Log.e("BookActivity", "Invalid book ID: null or empty");
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
                data.put("rating", rating);
                if (review != null) {
                    data.put("review", review);
                }
                data.put("updated_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(new java.util.Date()));

                // Write data to connection
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = data.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Get response
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                    throw new Exception("HTTP error code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e("BookActivity", "Failed to sync rating/review with Supabase: " + e.getMessage());
            }
        }).start();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Удаление книги")
                .setMessage("Вы уверены, что хотите удалить эту книгу из библиотеки?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    // Delete book from database
                    finish();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateBookStatus(String newStatus) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Изменение статуса")
                .setMessage("Вы уверены, что хотите изменить статус книги на \"" + newStatus + "\"?")
                .setPositiveButton("Да", (dialog, which) -> {
                    // Update status in database
                    DatabaseHelper dbHelper = new DatabaseHelper(this);
                    
                    // If changing to "Прочитано", set the end date to current date
                    String endDate = null;
                    if ("Прочитано".equals(newStatus)) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        endDate = dateFormat.format(new Date());
                        getIntent().putExtra("endDate", endDate);
                    }
                    
                    // Update the book with new status and end date
                    dbHelper.updateBook(bookId, null, null, null, newStatus, -1, -1, null, endDate, null);
                    dbHelper.close();
                    
                    // Update intent
                    getIntent().putExtra("status", newStatus);
                    readingStatus = newStatus;
                    
                    // Update UI
                    Spinner statusSpinner = findViewById(R.id.readingStatusSpinner);
                    ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) statusSpinner.getAdapter();
                    int spinnerPosition = adapter.getPosition(newStatus);
                    statusSpinner.setSelection(spinnerPosition);
                    
                    // Update sections visibility based on new status
                    updateSectionsVisibility();
                    
                    // Sync with Supabase
                    syncBookStatus(newStatus, endDate);
                    
                    // Show success message
                    Toast.makeText(this, "Статус книги изменен на \"" + newStatus + "\"", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
    
    private void syncBookStatus(String status, String endDate) {
        if (bookId == null || bookId.isEmpty()) {
            Log.e("BookActivity", "Invalid book ID: null or empty");
            return;
        }

        // Use original status value directly
        Log.d("BookActivity", "Syncing status to Supabase: " + status);

        new Thread(() -> {
            try {
                // First, let's check the current status in Supabase
                URL getUrl = new URL(SUPABASE_URL + "/rest/v1/" + SUPABASE_TABLE + "?id=eq." + bookId);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setRequestProperty("apikey", SUPABASE_KEY);
                getConn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                getConn.setRequestProperty("Content-Type", "application/json");
                
                int getResponseCode = getConn.getResponseCode();
                if (getResponseCode == HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(getConn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    br.close();
                    Log.d("BookActivity", "Current book data: " + response.toString());
                }
                getConn.disconnect();
                
                // Now try to update the status
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
                
                // Use the original status value directly
                data.put("status", status);
                
                // Add end date if changing to "Прочитано"
                if (endDate != null) {
                    data.put("finish_date", endDate);
                    Log.d("BookActivity", "Setting finish_date to: " + endDate);
                }
                
                data.put("updated_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .format(new Date()));
                
                String jsonData = data.toString();
                Log.d("BookActivity", "Sending data to Supabase: " + jsonData);

                // Write data to connection
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonData.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Get response
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                    // Read error response
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        Log.e("BookActivity", "Error response: " + response.toString());
                        throw new Exception("HTTP error code: " + responseCode + ", Response: " + response.toString());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(BookActivity.this, 
                        "Статус книги успешно обновлен", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e("BookActivity", "Failed to sync book status with Supabase: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(BookActivity.this, 
                    "Ошибка при обновлении статуса: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
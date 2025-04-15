package com.example.bookworm;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import org.json.JSONObject;

public class EditBookActivity extends AppCompatActivity {
    private String bookId;
    private EditText titleInput;
    private EditText authorInput;
    private Spinner statusSpinner;
    private EditText totalPagesInput;
    private EditText currentPageInput;
    private EditText startDateInput;
    private EditText endDateInput;
    private ImageView coverImage;
    private Button selectCoverButton;
    private Button saveButton;
    private Button cancelButton;
    private Calendar calendar;
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat dbDateFormat;
    private String coverPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_book);

        // Initialize views
        titleInput = findViewById(R.id.titleInput);
        authorInput = findViewById(R.id.authorInput);
        statusSpinner = findViewById(R.id.statusSpinner);
        totalPagesInput = findViewById(R.id.totalPagesInput);
        currentPageInput = findViewById(R.id.currentPageInput);
        startDateInput = findViewById(R.id.startDateInput);
        endDateInput = findViewById(R.id.endDateInput);
        coverImage = findViewById(R.id.coverImage);
        selectCoverButton = findViewById(R.id.selectCoverButton);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);

        // Initialize date formatters
        displayDateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        calendar = Calendar.getInstance();

        // Get book data from intent
        bookId = getIntent().getStringExtra("id");
        String title = getIntent().getStringExtra("title");
        String author = getIntent().getStringExtra("author");
        String status = getIntent().getStringExtra("status");
        int totalPages = getIntent().getIntExtra("totalPages", 0);
        int currentPage = getIntent().getIntExtra("currentPage", 0);
        String startDate = getIntent().getStringExtra("startDate");
        String endDate = getIntent().getStringExtra("endDate");
        coverPath = getIntent().getStringExtra("coverPath");

        // Set book data to views
        titleInput.setText(title);
        authorInput.setText(author);
        totalPagesInput.setText(String.valueOf(totalPages));
        currentPageInput.setText(String.valueOf(currentPage));

        // Format dates for display
        try {
            if (startDate != null && !startDate.isEmpty()) {
                Date date = dbDateFormat.parse(startDate);
                startDateInput.setText(displayDateFormat.format(date));
            }
            if (endDate != null && !endDate.isEmpty()) {
                Date date = dbDateFormat.parse(endDate);
                endDateInput.setText(displayDateFormat.format(date));
            }
        } catch (ParseException e) {
            Log.e("EditBookActivity", "Error parsing dates", e);
        }

        // Setup status spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.reading_status_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);
        if (status != null) {
            int spinnerPosition = adapter.getPosition(status);
            statusSpinner.setSelection(spinnerPosition);
        }

        // Load cover image
        if (coverPath != null && !coverPath.isEmpty()) {
            Glide.with(this)
                    .load(coverPath)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(coverImage);
        }

        // Setup click listeners
        startDateInput.setOnClickListener(v -> showDatePicker(startDateInput));
        endDateInput.setOnClickListener(v -> showDatePicker(endDateInput));
        selectCoverButton.setOnClickListener(v -> selectCoverImage());
        saveButton.setOnClickListener(v -> saveBook());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void showDatePicker(EditText dateInput) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                dateInput.setText(displayDateFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void selectCoverImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            coverPath = uri.toString();
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(coverImage);
        }
    }

    private void saveBook() {
        // Validate inputs
        String title = titleInput.getText().toString().trim();
        String author = authorInput.getText().toString().trim();
        String status = statusSpinner.getSelectedItem().toString();
        String totalPagesStr = totalPagesInput.getText().toString().trim();
        String currentPageStr = currentPageInput.getText().toString().trim();
        String startDate = startDateInput.getText().toString().trim();
        String endDate = endDateInput.getText().toString().trim();

        if (title.isEmpty() || author.isEmpty() || totalPagesStr.isEmpty() || currentPageStr.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, заполните все обязательные поля", Toast.LENGTH_SHORT).show();
            return;
        }

        int totalPages = Integer.parseInt(totalPagesStr);
        int currentPage = Integer.parseInt(currentPageStr);

        if (currentPage > totalPages) {
            Toast.makeText(this, "Текущая страница не может быть больше общего количества страниц", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert dates to database format and validate
        String dbStartDate = null;
        String dbEndDate = null;
        try {
            if (!startDate.isEmpty()) {
                Date startDateObj = displayDateFormat.parse(startDate);
                dbStartDate = dbDateFormat.format(startDateObj);
            }
            if (!endDate.isEmpty()) {
                Date endDateObj = displayDateFormat.parse(endDate);
                dbEndDate = dbDateFormat.format(endDateObj);
                
                // Validate end date is not before start date
                if (dbStartDate != null && dbEndDate.compareTo(dbStartDate) < 0) {
                    Toast.makeText(this, "Дата окончания не может быть раньше даты начала", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (ParseException e) {
            Toast.makeText(this, "Ошибка в формате даты", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update book in database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.updateBook(bookId, title, author, "", status, currentPage, totalPages, dbStartDate, dbEndDate, coverPath);
        dbHelper.close();

        // Sync with Supabase
        syncBookChanges(bookId, title, author, status, currentPage, totalPages, dbStartDate, dbEndDate, coverPath);

        // Return to BookActivity with updated data
        Intent resultIntent = new Intent();
        resultIntent.putExtra("id", bookId);
        resultIntent.putExtra("title", title);
        resultIntent.putExtra("author", author);
        resultIntent.putExtra("status", status);
        resultIntent.putExtra("currentPage", currentPage);
        resultIntent.putExtra("totalPages", totalPages);
        resultIntent.putExtra("startDate", dbStartDate);
        resultIntent.putExtra("endDate", dbEndDate);
        resultIntent.putExtra("coverPath", coverPath);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void syncBookChanges(String bookId, String title, String author, String status, 
                                int currentPage, int totalPages, String startDate, String endDate, String coverPath) {
        if (bookId == null || bookId.isEmpty()) {
            Log.e("EditBookActivity", "Invalid book ID: null or empty");
            Toast.makeText(this, "Ошибка: неверный идентификатор книги", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use the original status value directly
        Log.d("EditBookActivity", "Syncing status to Supabase: " + status);

        new Thread(() -> {
            try {
                // Construct the URL with the correct endpoint
                URL url = new URL("https://mfszyfmtujztqrjweixz.supabase.co/rest/v1/books?id=eq." + bookId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                // Set request method and headers
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mc3p5Zm10dWp6dHFyandlaXh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTkzNDAsImV4cCI6MjA1NzE3NTM0MH0.3URDTNl5T0R_TyWn6L0NlEFuLYoiH2qcQdYVNovFtVw");
                conn.setRequestProperty("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1mc3p5Zm10dWp6dHFyandlaXh6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDE1OTkzNDAsImV4cCI6MjA1NzE3NTM0MH0.3URDTNl5T0R_TyWn6L0NlEFuLYoiH2qcQdYVNovFtVw");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "return=minimal");
                conn.setDoOutput(true);

                // Create JSON data
                JSONObject data = new JSONObject();
                data.put("title", title);
                data.put("author", author);
                data.put("status", status); // Use the original status value directly
                data.put("current_page", currentPage);
                data.put("page_count", totalPages);
                if (startDate != null) data.put("start_date", startDate);
                if (endDate != null) data.put("finish_date", endDate);
                if (coverPath != null) data.put("cover_image_url", coverPath);
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
                    runOnUiThread(() -> Toast.makeText(EditBookActivity.this, 
                        "Изменения синхронизированы", Toast.LENGTH_SHORT).show());
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
                Log.e("EditBookActivity", "Failed to sync book changes with Supabase: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(EditBookActivity.this, 
                    "Ошибка синхронизации с сервером: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
} 
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
import com.example.bookworm.services.SupabaseService;

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
    private SupabaseService supabaseService;

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

        // Initialize Supabase service
        supabaseService = new SupabaseService(this);

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
            // Don't fail on parse exception, just leave date fields empty
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
        SimpleDateFormat currentDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = currentDateFormat.format(new Date());
        
        try {
            if (startDate != null && !startDate.isEmpty()) {
                Date startDateObj = displayDateFormat.parse(startDate);
                dbStartDate = dbDateFormat.format(startDateObj);
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                Date endDateObj = displayDateFormat.parse(endDate);
                dbEndDate = dbDateFormat.format(endDateObj);
                
                // Validate end date is not before start date - only if both dates are present
                if (dbStartDate != null && !dbStartDate.isEmpty() && 
                    dbEndDate != null && !dbEndDate.isEmpty() && 
                    dbEndDate.compareTo(dbStartDate) < 0) {
                    Toast.makeText(this, "Дата окончания не может быть раньше даты начала", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (ParseException e) {
            Log.e("EditBookActivity", "Error parsing dates: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка в формате даты", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fix dates based on status
        if ("Читаю".equals(status)) {
            // For reading books, ensure we have a start date
            if (dbStartDate == null || dbStartDate.isEmpty() || "null".equals(dbStartDate)) {
                dbStartDate = today;
            }
            // Clear end date for books being read
            dbEndDate = null;
        } 
        else if ("Прочитано".equals(status)) {
            // For finished books, ensure we have both dates
            if (dbStartDate == null || dbStartDate.isEmpty() || "null".equals(dbStartDate)) {
                dbStartDate = today;
            }
            if (dbEndDate == null || dbEndDate.isEmpty() || "null".equals(dbEndDate)) {
                dbEndDate = today;
            }
        }
        // For planned books, dates are optional

        // Log for debugging
        Log.d("EditBookActivity", "Saving book with status: " + status);
        Log.d("EditBookActivity", "Start date: " + dbStartDate);
        Log.d("EditBookActivity", "End date: " + dbEndDate);

        // Update book in Supabase
        updateBookInSupabase(title, author, status, currentPage, totalPages, dbStartDate, dbEndDate, coverPath);
    }

    private void updateBookInSupabase(String title, String author, String status, 
                                    int currentPage, int totalPages, String startDate, String endDate, String coverPath) {
        // Show progress indicator
        Toast.makeText(this, "Сохранение изменений...", Toast.LENGTH_SHORT).show();
        
        // Create a book object with updated information
        Book updatedBook = new Book();
        updatedBook.setId(bookId);
        updatedBook.setTitle(title);
        updatedBook.setAuthor(author);
        updatedBook.setStatus(status);
        updatedBook.setCurrentPage(currentPage);
        updatedBook.setTotalPages(totalPages);
        updatedBook.setStartDate(startDate);
        updatedBook.setEndDate(endDate);
        updatedBook.setCoverPath(coverPath);
        updatedBook.setExist(true);
        
        // Preserve existing fields that we're not editing
        updatedBook.setRating(getIntent().getIntExtra("rating", 0));
        updatedBook.setReview(getIntent().getStringExtra("review"));
        updatedBook.setDescription(getIntent().getStringExtra("description"));
        
        // We need to preserve file information to avoid re-uploading
        String fileUrl = getIntent().getStringExtra("file_url");
        if (fileUrl != null && !fileUrl.isEmpty()) {
            // This is the URL of an already uploaded file - preserve it
            Log.d("EditBookActivity", "Using existing file URL: " + fileUrl);
            updatedBook.setFilePath(fileUrl);
        } else {
            String filePath = getIntent().getStringExtra("filePath");
            if (filePath != null && !filePath.isEmpty()) {
                Log.d("EditBookActivity", "Using file path: " + filePath);
                updatedBook.setFilePath(filePath);
            }
        }
        
        updatedBook.setFileFormat(getIntent().getStringExtra("fileFormat"));
        
        Log.d("EditBookActivity", "Saving book to Supabase: " + bookId);
        Log.d("EditBookActivity", "FilePath: " + updatedBook.getFilePath());
        
        supabaseService.saveBook(updatedBook, new SupabaseService.BookSaveCallback() {
            @Override
            public void onSuccess() {
                Log.d("EditBookActivity", "Book saved successfully");
                
                // Return to BookActivity with updated data
                Intent resultIntent = new Intent();
                resultIntent.putExtra("id", bookId);
                resultIntent.putExtra("title", title);
                resultIntent.putExtra("author", author);
                resultIntent.putExtra("status", status);
                resultIntent.putExtra("currentPage", currentPage);
                resultIntent.putExtra("totalPages", totalPages);
                resultIntent.putExtra("startDate", startDate);
                resultIntent.putExtra("endDate", endDate);
                resultIntent.putExtra("coverPath", coverPath);
                
                // Include any preserved fields
                resultIntent.putExtra("rating", updatedBook.getRating());
                resultIntent.putExtra("review", updatedBook.getReview());
                resultIntent.putExtra("description", updatedBook.getDescription());
                resultIntent.putExtra("filePath", updatedBook.getFilePath());
                resultIntent.putExtra("fileFormat", updatedBook.getFileFormat());
                resultIntent.putExtra("file_url", updatedBook.getFilePath());
                
                setResult(RESULT_OK, resultIntent);
                finish();
            }
            
            @Override
            public void onError(String error) {
                Log.e("EditBookActivity", "Error saving book: " + error);
                Toast.makeText(EditBookActivity.this, "Ошибка сохранения книги: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseService != null) {
            supabaseService.shutdown();
        }
    }
} 
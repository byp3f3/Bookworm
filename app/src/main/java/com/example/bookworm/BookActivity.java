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
import androidx.appcompat.app.AppCompatActivity;

public class BookActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        // Get book data from intent
        String bookTitle = getIntent().getStringExtra("title");
        String bookAuthor = getIntent().getStringExtra("author");
        String bookGenre = getIntent().getStringExtra("genre");
        String readingStatus = getIntent().getStringExtra("status");
        int currentPage = getIntent().getIntExtra("currentPage", 0);
        int totalPages = getIntent().getIntExtra("totalPages", 0);
        int readingDays = getIntent().getIntExtra("readingDays", 0);

        // Initialize views
        ImageView bookCover = findViewById(R.id.bookCover);
        TextView titleView = findViewById(R.id.bookTitle);
        TextView authorView = findViewById(R.id.bookAuthor);
        Spinner statusSpinner = findViewById(R.id.readingStatusSpinner);
        LinearLayout currentlyReadingSection = findViewById(R.id.currentlyReadingSection);
        LinearLayout finishedReadingSection = findViewById(R.id.finishedReadingSection);
        TextView progressView = findViewById(R.id.readingProgress);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView daysView = findViewById(R.id.readingDays);
        TextView datesView = findViewById(R.id.readingDates);
        TextView timeView = findViewById(R.id.totalReadingTime);
        TextView pagesView = findViewById(R.id.pagesPerDay);
        RatingBar ratingBar = findViewById(R.id.ratingBar);
        TextView reviewView = findViewById(R.id.bookReview);
        Button editReviewButton = findViewById(R.id.editReviewButton);
        Button startReadingButton = findViewById(R.id.startReadingButton);
        Button editBookButton = findViewById(R.id.editBookButton);
        Button deleteBookButton = findViewById(R.id.deleteBookButton);

        // Set book data
        titleView.setText(bookTitle);
        authorView.setText(bookAuthor);

        // Setup status spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.reading_status_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(adapter);

        // Set current status
        if (readingStatus != null) {
            int spinnerPosition = adapter.getPosition(readingStatus);
            statusSpinner.setSelection(spinnerPosition);
        }

        // Show appropriate sections based on status
        if ("Читаю".equals(readingStatus)) {
            currentlyReadingSection.setVisibility(View.VISIBLE);
            progressView.setText(String.format("Прочитано %d из %d страниц (%d%%)",
                    currentPage, totalPages, (currentPage * 100 / totalPages)));
            progressBar.setMax(totalPages);
            progressBar.setProgress(currentPage);
            daysView.setText(String.format("Читаю %d дней", readingDays));
        } else if ("Прочитано".equals(readingStatus)) {
            finishedReadingSection.setVisibility(View.VISIBLE);
            // Set dates and stats (you would get these from your data)
            datesView.setText("Даты чтения: 01.01.2023 - 15.01.2023");
            timeView.setText("Всего дней чтения: 15");
            pagesView.setText("Среднее количество страниц в день: 20");
        }

        // Set click listeners
        bookCover.setOnClickListener(v -> openBookContent(bookTitle));

        startReadingButton.setOnClickListener(v -> openBookContent(bookTitle));

        editReviewButton.setOnClickListener(v -> {
            // Implement review editing
        });

        editBookButton.setOnClickListener(v -> {
            // Implement book editing
        });

        deleteBookButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удаление книги")
                    .setMessage("Вы уверены, что хотите удалить эту книгу из библиотеки?")
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        // Delete book from database
                        finish();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
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
            Uri uri = data.getData();
            if (uri != null) {
                // Take persistable URI permission
                try {
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    Log.d("BookActivity", "Successfully took persistable URI permission for: " + uri);
                } catch (SecurityException e) {
                    Log.e("BookActivity", "Failed to take persistable URI permission: " + e.getMessage());
                    // We still continue because we might be able to access the file anyway
                }
                
                // Now process the file...
                // Add your code to handle the file
                Toast.makeText(this, "Файл выбран: " + uri.getLastPathSegment(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
package com.example.bookworm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.io.File;

import com.example.bookworm.services.BookMetadataReader;
import com.example.bookworm.services.SupabaseService;

public class AddBookActivity extends AppCompatActivity {

    private static final String TAG = "AddBookActivity";
    private static final int PICK_IMAGE_REQUEST = 2;

    private EditText titleInput;
    private EditText authorInput;
    private EditText descriptionInput;
    private EditText pagesInput;
    private RadioGroup statusGroup;
    private LinearLayout readingDateContainer;
    private LinearLayout finishedReadingContainer;
    private EditText startDateInput;
    private EditText endDateInput;
    private RatingBar ratingBar;
    private EditText reviewInput;
    private Button selectCoverButton;
    private Button addButton;
    private ImageView coverPreview;

    private String selectedFilePath;
    private String selectedCoverPath;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private SupabaseService supabaseService;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_book);
        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        supabaseService = new SupabaseService(this);
        context = this;

        initializeViews();
        setupListeners();
        
        // Получаем данные из интента
        String fileUriString = getIntent().getStringExtra("fileUri");
        if (fileUriString != null) {
            Uri fileUri = Uri.parse(fileUriString);
            selectedFilePath = fileUriString;
            Log.d(TAG, "File URI from intent: " + selectedFilePath);
            
            // Заполняем поля метаданными, если они есть
            String title = getIntent().getStringExtra("title");
            String author = getIntent().getStringExtra("author");
            String description = getIntent().getStringExtra("description");
            String pages = getIntent().getStringExtra("pages");
            
            if (title != null) titleInput.setText(title);
            if (author != null) authorInput.setText(author);
            if (description != null) descriptionInput.setText(description);
            if (pages != null) pagesInput.setText(pages);
            
            // Устанавливаем обложку, если она есть
            String coverPath = getIntent().getStringExtra("coverPath");
            if (coverPath != null && !coverPath.isEmpty()) {
                Log.d(TAG, "Setting cover from path: " + coverPath);
                selectedCoverPath = "file://" + coverPath;
                if (coverPreview != null) {
                    File coverFile = new File(coverPath);
                    if (coverFile.exists()) {
                        coverPreview.setImageURI(Uri.fromFile(coverFile));
                        coverPreview.setVisibility(View.VISIBLE);
                        selectCoverButton.setText("Изменить обложку");
                    } else {
                        Log.e(TAG, "Cover file does not exist: " + coverPath);
                    }
                }
            } else {
                Log.d(TAG, "No cover path in intent");
            }
        } else {
            Log.d(TAG, "No file URI in intent");
        }
    }

    private void initializeViews() {
        titleInput = findViewById(R.id.titleInput);
        authorInput = findViewById(R.id.authorInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        pagesInput = findViewById(R.id.pagesInput);
        statusGroup = findViewById(R.id.statusGroup);
        readingDateContainer = findViewById(R.id.readingDateContainer);
        finishedReadingContainer = findViewById(R.id.finishedReadingContainer);
        startDateInput = findViewById(R.id.startDateInput);
        endDateInput = findViewById(R.id.endDateInput);
        ratingBar = findViewById(R.id.ratingBar);
        reviewInput = findViewById(R.id.reviewInput);
        selectCoverButton = findViewById(R.id.selectCoverButton);
        addButton = findViewById(R.id.addButton);
        coverPreview = findViewById(R.id.coverPreview);
        
        // Изначально превью обложки скрыто
        if (coverPreview != null) {
            coverPreview.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        statusGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isReading = checkedId == R.id.readingRadio;
            boolean isFinished = checkedId == R.id.finishedRadio;
            
            readingDateContainer.setVisibility(isReading ? View.VISIBLE : View.GONE);
            finishedReadingContainer.setVisibility(isFinished ? View.VISIBLE : View.GONE);
            
            if (isFinished) {
                readingDateContainer.setVisibility(View.VISIBLE);
            }
        });

        startDateInput.setOnClickListener(v -> showDatePicker(startDateInput));
        endDateInput.setOnClickListener(v -> showDatePicker(endDateInput));

        selectCoverButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        addButton.setOnClickListener(v -> addBook());
    }

    private void showDatePicker(EditText dateInput) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                dateInput.setText(dateFormat.format(calendar.getTime()));
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && requestCode == PICK_IMAGE_REQUEST) {
                // Take persistable permission
                try {
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    Log.d(TAG, "Successfully took persistable URI permission for: " + uri);
                } catch (SecurityException e) {
                    Log.e(TAG, "Failed to take persistable URI permission: " + e.getMessage());
                    // We still continue because we might be able to access the file anyway
                }
                
                selectedCoverPath = uri.toString();
                Log.d(TAG, "Cover selected: " + selectedCoverPath);
                
                // Показываем обложку и меняем текст кнопки
                if (coverPreview != null) {
                    coverPreview.setImageURI(uri);
                    coverPreview.setVisibility(View.VISIBLE);
                    selectCoverButton.setText("Изменить обложку");
                }
                
                Toast.makeText(this, "Обложка выбрана", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addBook() {
        String title = titleInput.getText().toString().trim();
        String author = authorInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        String pagesStr = pagesInput.getText().toString().trim();
        int pages = pagesStr.isEmpty() ? 0 : Integer.parseInt(pagesStr);

        if (title.isEmpty() || author.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, заполните обязательные поля", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверяем, выбран ли файл книги
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, выберите файл книги", Toast.LENGTH_SHORT).show();
            return;
        }

        // Логируем значения путей к файлам
        Log.d(TAG, "Selected file path: " + selectedFilePath);
        Log.d(TAG, "Selected cover path: " + selectedCoverPath);

        String status;
        String startDate = null;
        String endDate = null;
        int rating = 0;
        String review = null;

        int statusId = statusGroup.getCheckedRadioButtonId();
        if (statusId == R.id.plannedRadio) {
            status = "В планах";
        } else if (statusId == R.id.readingRadio) {
            status = "Читаю";
            startDate = startDateInput.getText().toString().trim();
            if (startDate.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, укажите дату начала чтения", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            status = "Прочитано";
            startDate = startDateInput.getText().toString().trim();
            endDate = endDateInput.getText().toString().trim();
            rating = (int) ratingBar.getRating();
            review = reviewInput.getText().toString().trim();
            
            if (startDate.isEmpty() || endDate.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, укажите даты чтения", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Создаем объект книги
        Book book = new Book(
            UUID.randomUUID().toString(),
            title,
            author,
            description,
            selectedCoverPath,
            selectedFilePath,
            status,
            0,
            pages,
            0,
            startDate,
            endDate,
            rating,
            review,
            getFileFormatFromUrl(selectedFilePath)

        );

        // Используем уже созданный экземпляр supabaseService
        saveBookToSupabase(book);
    }

    private void saveBookToSupabase(Book book) {
        // Показываем индикатор загрузки
        Toast.makeText(this, "Сохранение книги...", Toast.LENGTH_SHORT).show();
        
        supabaseService.saveBook(book, new SupabaseService.BookSaveCallback() {
            @Override
            public void onSuccess() {
                String message = "Книга \"" + book.getTitle() + "\" автора " + book.getAuthor() + " добавлена";
                Toast.makeText(AddBookActivity.this, message, Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_OK);
                finish();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error saving book: " + error);
                
                // Проверяем, является ли ошибка дублированием книги
                if (error.contains("duplicate key value") && error.contains("unique_book_per_user")) {
                    Toast.makeText(AddBookActivity.this, 
                            "Книга уже добавлена в вашу библиотеку",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(AddBookActivity.this, "Ошибка при сохранении: " + error, Toast.LENGTH_LONG).show();
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
}
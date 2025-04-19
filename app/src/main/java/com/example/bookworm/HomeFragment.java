package com.example.bookworm;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.bookworm.services.BookMetadataReader;
import com.example.bookworm.services.SupabaseService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private LinearLayout currentlyReadingContainer;
    private LinearLayout emptyCurrentlyReadingContainer;
    private LinearLayout currentlyReadingList;
    private Button showAllButton;
    private Button addBookEmptyButton;
    private ProgressBar loadingProgress;
    private SupabaseService supabaseService;
    
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> storagePermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        currentlyReadingContainer = view.findViewById(R.id.currentlyReadingContainer);
        emptyCurrentlyReadingContainer = view.findViewById(R.id.emptyCurrentlyReadingContainer);
        currentlyReadingList = view.findViewById(R.id.currentlyReadingList);
        showAllButton = view.findViewById(R.id.showAllButton);
        addBookEmptyButton = view.findViewById(R.id.addBookEmptyButton);
        loadingProgress = view.findViewById(R.id.loadingProgress);

        supabaseService = new SupabaseService(requireContext());

        // Инициализация ланчера для запроса разрешений
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openFilePicker();
                } else {
                    Toast.makeText(requireContext(), "Для работы с файлами необходимо разрешение", Toast.LENGTH_LONG).show();
                }
            }
        );

        // Инициализация ланчера для запроса разрешения на доступ ко всем файлам (Android 11+)
        storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        openFilePicker();
                    } else {
                        Toast.makeText(requireContext(), "Для работы с файлами необходимо разрешение", Toast.LENGTH_LONG).show();
                    }
                }
            }
        );

        // Инициализация ланчера для выбора файла
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "File picker result received");
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Log.d(TAG, "Selected file URI: " + uri.toString());
                        
                        // Получаем доступ на чтение файла
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            requireActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            Log.d(TAG, "Successfully took persistable URI permission for: " + uri);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take persistable URI permission: " + e.getMessage());
                        }
                        
                        String mimeType = requireActivity().getContentResolver().getType(uri);
                        Log.d(TAG, "File MIME type: " + mimeType);
                        
                        // Проверяем расширение файла, если MIME тип не определен
                        String fileName = uri.getLastPathSegment();
                        if (mimeType == null && fileName != null) {
                            Log.d(TAG, "MIME type is null, checking extension of file: " + fileName);
                            
                            if (fileName.toLowerCase().endsWith(".fb2")) {
                                mimeType = "application/x-fictionbook+xml";
                            } else if (fileName.toLowerCase().endsWith(".epub")) {
                                mimeType = "application/epub+zip";
                            }
                            Log.d(TAG, "Inferred MIME type from extension: " + mimeType);
                        }

                        if (isSupportedFileType(mimeType, fileName)) {
                            // Читаем метаданные из файла
                            readMetadataAndProceed(uri);
                        } else {
                            // Более детальное сообщение об ошибке
                            StringBuilder message = new StringBuilder("Неподдерживаемый формат файла");
                            
                            if (mimeType != null) {
                                message.append(": ").append(mimeType);
                            }
                            
                            if (fileName != null) {
                                message.append("\nИмя файла: ").append(fileName);
                            }
                            
                            message.append("\n\nПоддерживаются только FB2 и EPUB файлы");
                            
                            Log.w(TAG, message.toString());
                            Toast.makeText(requireContext(), message.toString(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e(TAG, "Selected URI is null");
                        Toast.makeText(requireContext(), "Ошибка при выборе файла", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "File picker cancelled or failed");
                }
            }
        );

        addBookEmptyButton.setOnClickListener(v -> checkPermissionsAndOpenFilePicker());

        showAllButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), BookActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private void checkPermissionsAndOpenFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", requireActivity().getPackageName(), null);
                intent.setData(uri);
                storagePermissionLauncher.launch(intent);
            } else {
                openFilePicker();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 (API 23-29)
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                openFilePicker();
            }
        } else {
            // Android 5 и ниже
            openFilePicker();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Use a more generic MIME type first to show all documents
        intent.setType("*/*");
        
        // Add all possible FB2 and EPUB MIME types
        String[] mimeTypes = {
                "application/x-fictionbook+xml",  // Primary FB2 MIME
                "text/x-fictionbook+xml",         // Alternative FB2 MIME
                "application/fb2",                // Another alternative
                "application/octet-stream",       // Generic binary format
                "text/plain",                     // Try text/plain as some systems use this for FB2
                "application/xml",                // FB2 is XML-based
                "text/xml",                       // Another XML option
                "application/epub+zip"            // EPUB format
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        // Add all possible flags
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        
        filePickerLauncher.launch(intent);
    }

    private boolean isSupportedFileType(String mimeType, String fileName) {
        if (mimeType != null) {
            return mimeType.equals("application/x-fictionbook+xml") ||
                   mimeType.equals("text/x-fictionbook+xml") ||
                   mimeType.equals("application/fb2") ||
                   mimeType.equals("application/epub+zip") ||
                   mimeType.equals("application/octet-stream") ||
                   mimeType.equals("text/plain") ||
                   mimeType.equals("application/xml") ||
                   mimeType.equals("text/xml");
        }
        
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            return lowerFileName.endsWith(".fb2") ||
                   lowerFileName.endsWith(".epub") ||
                   lowerFileName.endsWith(".fb2.zip");
        }
        
        return false;
    }

    private void readMetadataAndProceed(Uri fileUri) {
        Log.d(TAG, "Starting metadata reading for URI: " + fileUri);
        String finalFileName = fileUri.getLastPathSegment();

        BookMetadataReader.readMetadata(requireContext(), fileUri, new BookMetadataReader.MetadataCallback() {
            @Override
            public void onMetadataReady(Map<String, String> metadata) {
                Log.d(TAG, "Metadata ready: " + metadata.toString());

                // Ensure we have at least the filename as title
                if (!metadata.containsKey("title")) {
                    metadata.put("title", getFileNameWithoutExtension(finalFileName));
                }

                // Запускаем AddBookActivity с метаданными
                Intent intent = new Intent(requireActivity(), AddBookActivity.class);
                intent.putExtra("fileUri", fileUri.toString());

                // Передаем метаданные
                intent.putExtra("title", metadata.get("title"));
                if (metadata.containsKey("author")) {
                    intent.putExtra("author", metadata.get("author"));
                }
                if (metadata.containsKey("description")) {
                    intent.putExtra("description", metadata.get("description"));
                }
                if (metadata.containsKey("pages")) {
                    intent.putExtra("pages", metadata.get("pages"));
                }
                
                // Передаем путь к извлеченной обложке, если есть
                if (metadata.containsKey("coverPath")) {
                    String coverPath = metadata.get("coverPath");
                    Log.d(TAG, "Passing cover path to AddBookActivity: " + coverPath);
                    intent.putExtra("coverPath", coverPath);
                }

                startActivity(intent);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error reading metadata: " + error);

                // Use filename as fallback title
                Intent intent = new Intent(requireActivity(), AddBookActivity.class);
                intent.putExtra("fileUri", fileUri.toString());
                intent.putExtra("title", getFileNameWithoutExtension(finalFileName));
                startActivity(intent);
            }
        });
    }

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "Unknown";
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCurrentlyReadingBooks();
    }

    private void loadCurrentlyReadingBooks() {
        loadingProgress.setVisibility(View.VISIBLE);
        currentlyReadingContainer.setVisibility(View.GONE);
        emptyCurrentlyReadingContainer.setVisibility(View.GONE);

        supabaseService.getCurrentlyReadingBooks(new SupabaseService.BooksLoadCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                requireActivity().runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    if (books.isEmpty()) {
                        showEmptyState();
                    } else {
                        showBooksList(books);
                    }
                });
            }

            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Ошибка загрузки книг: " + error, Toast.LENGTH_SHORT).show();
                    showEmptyState();
                });
            }
        });
    }

    private void showEmptyState() {
        currentlyReadingContainer.setVisibility(View.GONE);
        emptyCurrentlyReadingContainer.setVisibility(View.VISIBLE);
    }

    private void showBooksList(List<Book> books) {
        currentlyReadingContainer.setVisibility(View.VISIBLE);
        emptyCurrentlyReadingContainer.setVisibility(View.GONE);
        currentlyReadingList.removeAllViews();

        for (Book book : books) {
            View bookView = LayoutInflater.from(getContext()).inflate(R.layout.item_currently_reading_book, currentlyReadingList, false);

            ImageView bookCover = bookView.findViewById(R.id.bookCover);
            TextView bookTitle = bookView.findViewById(R.id.bookTitle);
            TextView bookAuthor = bookView.findViewById(R.id.bookAuthor);
            TextView bookProgress = bookView.findViewById(R.id.bookProgress);
            TextView readingDuration = bookView.findViewById(R.id.readingDuration);
            ProgressBar progressBar = bookView.findViewById(R.id.progressBar);

            // Загрузка обложки с помощью Glide
            if (book.getCoverPath() != null && !book.getCoverPath().isEmpty()) {
                Glide.with(this)
                        .load(book.getCoverPath())
                        .placeholder(R.drawable.ic_book_placeholder)
                        .error(R.drawable.ic_book_placeholder)
                        .into(bookCover);
            } else {
                bookCover.setImageResource(R.drawable.ic_book_placeholder);
            }

            bookTitle.setText(book.getTitle());
            bookAuthor.setText(book.getAuthor());

            int progress = book.getTotalPages() > 0
                    ? (book.getCurrentPage() * 100) / book.getTotalPages()
                    : 0;
            bookProgress.setText(book.getCurrentPage() + "/" + book.getTotalPages() + " стр.");
            progressBar.setProgress(progress);

            // Расчет дней чтения
            long readingDays = calculateReadingDays(book.getStartDate());
            readingDuration.setText("Читаю " + readingDays + " " + getDayString(readingDays));

            CardView bookCard = bookView.findViewById(R.id.bookCard);
            bookCard.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), BookActivity.class);
                intent.putExtra("id", book.getId());
                intent.putExtra("title", book.getTitle());
                intent.putExtra("author", book.getAuthor());
                intent.putExtra("status", book.getStatus());
                intent.putExtra("currentPage", book.getCurrentPage());
                intent.putExtra("totalPages", book.getTotalPages());
                intent.putExtra("readingDays", book.getReadingDays());
                intent.putExtra("coverPath", book.getCoverPath());
                intent.putExtra("startDate", book.getStartDate());
                intent.putExtra("endDate", book.getEndDate());
                intent.putExtra("rating", book.getRating());
                intent.putExtra("review", book.getReview());
                startActivity(intent);
            });

            currentlyReadingList.addView(bookView);
        }

        // Add "Add book" button at the end
        View addBookView = LayoutInflater.from(getContext()).inflate(R.layout.item_currently_reading_book, currentlyReadingList, false);
        ImageView addBookCover = addBookView.findViewById(R.id.bookCover);
        TextView addBookTitle = addBookView.findViewById(R.id.bookTitle);
        TextView addBookAuthor = addBookView.findViewById(R.id.bookAuthor);
        TextView addBookProgress = addBookView.findViewById(R.id.bookProgress);
        TextView addReadingDuration = addBookView.findViewById(R.id.readingDuration);
        ProgressBar addProgressBar = addBookView.findViewById(R.id.progressBar);

        addBookCover.setImageResource(R.drawable.ic_add_book);
        addBookTitle.setText("Добавить книгу");
        addBookAuthor.setVisibility(View.GONE);
        addBookProgress.setVisibility(View.GONE);
        addReadingDuration.setVisibility(View.GONE);
        addProgressBar.setVisibility(View.GONE);

        addBookView.setOnClickListener(v -> {
            checkPermissionsAndOpenFilePicker();
        });

        currentlyReadingList.addView(addBookView);

        // Show "Show all" button if more than 5 books
        if (books.size() > 5) {
            showAllButton.setVisibility(View.VISIBLE);
        } else {
            showAllButton.setVisibility(View.GONE);
        }
    }

    private long calculateReadingDays(String startDateStr) {
        if (startDateStr == null || startDateStr.isEmpty()) {
            return 0;
        }

        try {
            // Удаляем часть с временем и символ 'T'
            String dateOnly = startDateStr.split("T")[0];

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // Устанавливаем UTC для консистентности

            Date startDate = sdf.parse(dateOnly);
            Date currentDate = new Date();

            // Для отладки
            Log.d("DateDebug", "Original date string: " + startDateStr);
            Log.d("DateDebug", "Parsed start date: " + startDate.toString());
            Log.d("DateDebug", "Current date: " + currentDate.toString());

            // Проверяем, что дата начала не в будущем
            if (startDate.after(currentDate)) {
                Log.d("DateDebug", "Start date is in future");
                return 0;
            }

            long diff = currentDate.getTime() - startDate.getTime();
            long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) + 1;

            Log.d("DateDebug", "Calculated days: " + days);
            return days;
        } catch (Exception e) {
            Log.e("DateDebug", "Error parsing date", e);
            return 0;
        }
    }

    private String getDayString(long days) {
        long lastDigit = days % 10;
        long lastTwoDigits = days % 100;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 19) {
            return "дней";
        }

        switch ((int) lastDigit) {
            case 1: return "день";
            case 2:
            case 3:
            case 4: return "дня";
            default: return "дней";
        }
    }
}
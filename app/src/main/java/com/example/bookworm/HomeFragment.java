package com.example.bookworm;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.bookworm.services.BookMetadataReader;
import com.example.bookworm.services.SupabaseService;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
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
    private TextView yearlyStatsText;
    private ViewPager2 chartViewPager;
    private LinearLayout yearIndicatorContainer;
    
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> storagePermissionLauncher;
    
    private List<Integer> availableYears = new ArrayList<>();
    private int currentYearIndex = 0;
    private ChartPagerAdapter chartPagerAdapter;

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
        yearlyStatsText = view.findViewById(R.id.yearlyStatsText);
        chartViewPager = view.findViewById(R.id.chartViewPager);
        yearIndicatorContainer = view.findViewById(R.id.yearIndicatorContainer);

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

        // Load statistics data
        loadStatisticsData();

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
        loadBooks();
        loadStatisticsData(); // Обновляем статистику при возвращении к фрагменту
    }

    private void loadBooks() {
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
                
                // Добавляем передачу пути к файлу
                intent.putExtra("filePath", book.getFilePath());
                intent.putExtra("file_url", book.getFilePath());
                intent.putExtra("fileFormat", book.getFileFormat());
                
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

    private void loadStatisticsData() {
        // Показываем индикатор загрузки для статистики
        if (yearlyStatsText != null) {
            yearlyStatsText.setText("Загрузка статистики...");
        }
        
        // Получаем все книги пользователя из Supabase и фильтруем по статусу "Прочитано"
        supabaseService.getAllUserBooks(new SupabaseService.BooksLoadCallback() {
            @Override
            public void onSuccess(List<Book> allBooks) {
                // Фильтруем книги со статусом "Прочитано"
                List<Book> finishedBooks = new ArrayList<>();
                for (Book book : allBooks) {
                    if ("Прочитано".equals(book.getStatus())) {
                        finishedBooks.add(book);
                    }
                }
                
                Log.d(TAG, "Found " + finishedBooks.size() + " books with 'Прочитано' status from Supabase");
                
                // Создаем список доступных годов
                availableYears.clear();
                Map<Integer, Map<Integer, Integer>> yearMonthBookCountMap = new HashMap<>();
                int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                
                // Убедимся, что текущий год всегда доступен
                availableYears.add(currentYear);
                yearMonthBookCountMap.put(currentYear, new HashMap<>());
                
                // Инициализируем счетчики для всех месяцев текущего года
                for (int i = 1; i <= 12; i++) {
                    yearMonthBookCountMap.get(currentYear).put(i, 0);
                }
                
                // Обрабатываем книги
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                
                for (Book book : finishedBooks) {
                    if (book.getEndDate() != null && !book.getEndDate().isEmpty()) {
                        try {
                            String endDateStr = book.getEndDate();
                            Log.d(TAG, "Processing book with end date: " + endDateStr + ", title: " + book.getTitle());
                            
                            // Check if date has 'T' format from Supabase
                            if (endDateStr.contains("T")) {
                                endDateStr = endDateStr.split("T")[0];
                            }
                            
                            Date finishDate = dateFormat.parse(endDateStr);
                            if (finishDate != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(finishDate);
                                int year = cal.get(Calendar.YEAR);
                                int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH начинается с 0
                                
                                Log.d(TAG, "Book finished in year: " + year + ", month: " + month + ", title: " + book.getTitle());
                                
                                // Добавляем год в доступные, если его еще нет
                                if (!availableYears.contains(year)) {
                                    availableYears.add(year);
                                    yearMonthBookCountMap.put(year, new HashMap<>());
                                    // Инициализируем счетчики для всех месяцев
                                    for (int i = 1; i <= 12; i++) {
                                        yearMonthBookCountMap.get(year).put(i, 0);
                                    }
                                }
                                
                                // Увеличиваем счетчик для соответствующего месяца и года
                                Map<Integer, Integer> monthCounts = yearMonthBookCountMap.get(year);
                                int currentCount = monthCounts.getOrDefault(month, 0);
                                monthCounts.put(month, currentCount + 1);
                                Log.d(TAG, "Updated count for " + year + "-" + month + " is now: " + monthCounts.get(month));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing date: " + book.getEndDate() + " for book: " + book.getTitle(), e);
                        }
                    } else {
                        Log.d(TAG, "Book has no end date: " + book.getTitle());
                    }
                }
                
                // Сортируем годы по убыванию
                java.util.Collections.sort(availableYears, (a, b) -> b - a);
                
                // Если доступных годов нет, добавляем текущий
                if (availableYears.isEmpty()) {
                    availableYears.add(currentYear);
                    yearMonthBookCountMap.put(currentYear, new HashMap<>());
                    for (int i = 1; i <= 12; i++) {
                        yearMonthBookCountMap.get(currentYear).put(i, 0);
                    }
                }
                
                // Вывод отладочной информации о количестве книг по годам и месяцам
                for (int year : availableYears) {
                    Map<Integer, Integer> monthCounts = yearMonthBookCountMap.get(year);
                    int totalForYear = 0;
                    for (int month = 1; month <= 12; month++) {
                        int count = monthCounts.getOrDefault(month, 0);
                        if (count > 0) {
                            Log.d(TAG, "Stats for " + year + "-" + month + ": " + count + " books");
                            totalForYear += count;
                        }
                    }
                    Log.d(TAG, "Total for year " + year + ": " + totalForYear + " books");
                }
                
                // Обновляем UI в главном потоке
                requireActivity().runOnUiThread(() -> {
                    // Создаем и настраиваем адаптер для ViewPager
                    chartPagerAdapter = new ChartPagerAdapter(requireContext(), availableYears, yearMonthBookCountMap);
                    chartViewPager.setAdapter(chartPagerAdapter);
                    
                    // Устанавливаем текущий год
                    currentYearIndex = 0; // Всегда начинаем с самого последнего года (первого в списке)
                    updateYearIndicators();
                    
                    // Обрабатываем переключение годов
                    chartViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                        @Override
                        public void onPageSelected(int position) {
                            super.onPageSelected(position);
                            currentYearIndex = position;
                            updateYearIndicators();
                            updateYearlyStatsText(availableYears.get(position), yearMonthBookCountMap.get(availableYears.get(position)));
                        }
                    });
                    
                    // Обновляем текст с годовой статистикой для текущего года
                    if (!availableYears.isEmpty()) {
                        updateYearlyStatsText(availableYears.get(0), yearMonthBookCountMap.get(availableYears.get(0)));
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading books from Supabase: " + error);
                requireActivity().runOnUiThread(() -> {
                    if (yearlyStatsText != null) {
                        yearlyStatsText.setText("Ошибка загрузки статистики");
                    }
                    Toast.makeText(requireContext(), "Ошибка загрузки статистики: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateYearIndicators() {
        yearIndicatorContainer.removeAllViews();
        
        for (int i = 0; i < availableYears.size(); i++) {
            View indicator = new View(requireContext());
            int size = getResources().getDimensionPixelSize(R.dimen.indicator_size);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(8, 0, 8, 0);
            indicator.setLayoutParams(params);
            
            if (i == currentYearIndex) {
                indicator.setBackgroundResource(R.drawable.indicator_active);
            } else {
                indicator.setBackgroundResource(R.drawable.indicator_inactive);
            }
            
            final int year = availableYears.get(i);
            indicator.setOnClickListener(v -> {
                chartViewPager.setCurrentItem(availableYears.indexOf(year));
            });
            
            yearIndicatorContainer.addView(indicator);
        }
    }
    
    private void updateYearlyStatsText(int year, Map<Integer, Integer> monthCountMap) {
        if (!monthCountMap.isEmpty()) {
            StringBuilder statsText = new StringBuilder();
            
            // Calculate total books for the year
            int totalBooks = 0;
            for (int count : monthCountMap.values()) {
                totalBooks += count;
            }
            
            // Use proper Russian grammar for book count
            String bookCountText = getBooksCountString(totalBooks);
            statsText.append(year).append(": всего прочитано ").append(totalBooks).append(" ").append(bookCountText).append("\n");
            
            yearlyStatsText.setText(statsText.toString().trim());
        } else {
            yearlyStatsText.setText(year + ": нет данных о прочитанных книгах");
        }
    }
    
    private String getBooksCountString(int count) {
        if (count % 10 == 1 && count % 100 != 11) {
            return "книгу";
        } else if ((count % 10 == 2 || count % 10 == 3 || count % 10 == 4) && 
                  (count % 100 != 12 && count % 100 != 13 && count % 100 != 14)) {
            return "книги";
        } else {
            return "книг";
        }
    }

}


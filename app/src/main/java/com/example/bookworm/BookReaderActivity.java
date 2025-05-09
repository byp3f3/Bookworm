package com.example.bookworm;

import android.os.Bundle;
import android.webkit.WebView;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.content.Intent;
import android.net.Uri;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;
import com.example.bookworm.services.BookFileReader;
import com.example.bookworm.services.SupabaseService;
import com.example.bookworm.Book;
import java.util.List;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.util.DisplayMetrics;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.example.bookworm.models.TocItem;
import com.example.bookworm.adapters.TocAdapter;
import com.example.bookworm.adapters.QuotesAdapter;
import java.util.ArrayList;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.view.Menu;
import android.view.MenuInflater;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import java.util.UUID;
import android.widget.Button;
import android.widget.FrameLayout;
import android.os.SystemClock;
import android.app.AlertDialog;

public class BookReaderActivity extends BaseActivity {
    private static final String TAG = "BookReaderActivity";
    private static final String PREFS_NAME = "BookwormPrefs";
    private static final String KEY_THEME = "theme";
    private WebView contentWebView;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;
    private ProgressBar loadingProgressBar;
    private TextView titleText;
    private boolean panelsVisible = false;
    private SeekBar pageProgressBar;
    private TextView pageIndicator;
    private List<String> pages;
    private int currentPage = 0;
    private GestureDetector gestureDetector;
    private long lastTouchTime = 0;
    private static final long TOUCH_COOLDOWN = 300; // 300ms cooldown between touches
    
    // Book data
    private String bookId;
    private String bookTitle;
    private int savedCurrentPage = 0;
    private SupabaseService supabaseService;
    
    // Progress sync debounce
    private static final long PROGRESS_SYNC_DELAY = 1000; // 1 second delay between Supabase updates
    private long lastProgressSync = 0;
    private Runnable pendingProgressSync;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    // New fields
    private long lastPageTimestamp = 0;
    private final Object syncLock = new Object();
    private boolean syncInProgress = false;
    
    // Table of contents components
    private LinearLayout tocPanel;
    private RecyclerView tocRecyclerView;
    private TocAdapter tocAdapter;
    private List<TocItem> tocItems;
    private ImageButton btnToc;
    private ImageButton btnCloseToc;
    
    // Search components
    private LinearLayout searchPanel;
    private EditText searchEditText;
    private TextView searchResultsCount;
    private ImageButton btnCloseSearch;
    private ImageButton btnPrevResult;
    private ImageButton btnNextResult;
    private List<Integer> searchResults;
    private int currentSearchIndex = -1;
    private String lastSearchQuery = "";

    // Quote components
    private LinearLayout quotePanel;
    private ImageButton btnCopyQuote;
    private ImageButton btnSaveQuote;
    private ImageButton btnCancelQuote;
    private String selectedText = "";
    private boolean isTextSelected = false;
    private List<Quote> bookQuotes = new ArrayList<>();
    private RecyclerView quotesRecyclerView;
    private boolean isShowingQuotes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        // Initialize SupabaseService
        supabaseService = new SupabaseService(this);

        // Initialize components
        contentWebView = findViewById(R.id.contentWebView);
        topPanel = findViewById(R.id.topPanel);
        bottomPanel = findViewById(R.id.bottomPanel);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        titleText = findViewById(R.id.titleText);
        pageProgressBar = findViewById(R.id.pageProgressBar);
        pageIndicator = findViewById(R.id.pageIndicator);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // Create transparent touch overlay for page turning
        View touchOverlay = new View(this);
        touchOverlay.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        ((FrameLayout) findViewById(R.id.root_container)).addView(touchOverlay, params);
        
        // Set up touch detection on the overlay
        touchOverlay.setOnTouchListener((v, event) -> {
            return gestureDetector.onTouchEvent(event);
        });
        
        // Apply theme to UI components
        applyThemeToUI();
        
        // Hide TOC button - we're removing this functionality
        btnToc = findViewById(R.id.btnToc);
        if (btnToc != null) {
            btnToc.setVisibility(View.GONE);
        }
        
        // Hide TOC panel - we're removing this functionality
        tocPanel = findViewById(R.id.tocPanel);
        if (tocPanel != null) {
            tocPanel.setVisibility(View.GONE);
        }
        
        // Initialize close TOC button
        btnCloseToc = findViewById(R.id.btnCloseToc);

        // Initialize search components
        searchPanel = findViewById(R.id.searchPanel);
        searchEditText = findViewById(R.id.searchEditText);
        searchResultsCount = findViewById(R.id.searchResultsCount);
        btnCloseSearch = findViewById(R.id.btnCloseSearch);
        btnPrevResult = findViewById(R.id.btnPrevResult);
        btnNextResult = findViewById(R.id.btnNextResult);
        searchResults = new ArrayList<>();

        // Hide quote panel - we're removing this functionality
        quotePanel = findViewById(R.id.quotePanel);
        if (quotePanel != null) {
            quotePanel.setVisibility(View.GONE);
        }

        // Configure WebView
        WebSettings webSettings = contentWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDefaultFontSize(20);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // Disable text selection
        webSettings.setJavaScriptEnabled(true);
        contentWebView.setWebChromeClient(new android.webkit.WebChromeClient());
        
        // DISABLE text selection in WebView by setting this specific CSS
        webSettings.setTextZoom(100);
        contentWebView.setFocusable(true);
        contentWebView.setFocusableInTouchMode(true);
        
        // Set scrollbars
        contentWebView.setVerticalScrollBarEnabled(true);
        contentWebView.setHorizontalScrollBarEnabled(true);

        // Set up WebView client to handle page loading
        contentWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading");

                // First delay is needed to ensure DOM is fully loaded
                new Handler().postDelayed(() -> {
                    // Inject JavaScript to disable text selection
                    disableTextSelection();
                    
                    loadingProgressBar.setVisibility(View.GONE);
                }, 100);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                loadingProgressBar.setVisibility(View.GONE);
                Toast.makeText(BookReaderActivity.this, "Ошибка загрузки страницы: " + error.getDescription(), Toast.LENGTH_SHORT).show();
            }
        });

        // Setup progress bar listener
        pageProgressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && pages != null && progress >= 0 && progress < pages.size()) {
                    showPage(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Настраиваем детектор жестов
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
            
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // Если текст выделен, то сначала проверяем, не нажал ли пользователь вне выделения
                if (isTextSelected) {
                    // Hide text selection menu and clear selection
                    hideQuotePanel();
                    clearTextSelection();
                    return true;
                }
                
                // Проверяем, было ли нажатие на левую или правую часть экрана
                float x = e.getX();
                int width = contentWebView.getWidth();
                
                if (x < width * 0.2) {
                    // Нажатие на левый край - предыдущая страница
                    prevPage();
                    return true;
                } else if (x > width * 0.8) {
                    // Нажатие на правый край - следующая страница
                    nextPage();
                    return true;
                } else {
                    // Нажатие в центре - показать/скрыть панели
                    togglePanels();
                    return true;
                }
            }
            
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Если текст выделен, игнорируем жесты свайпа
                if (isTextSelected) {
                    return false;
                }
                
                boolean result = false;
                try {
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Свайп вправо - предыдущая страница
                            prevPage();
                        } else {
                            // Свайп влево - следующая страница
                            nextPage();
                        }
                        result = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in gesture detection", e);
                }
                return result;
            }
        });
        
        // Initialize quote buttons - we'll skip setting listeners since we're removing this functionality
        btnCopyQuote = findViewById(R.id.btnCopyQuote);
        btnSaveQuote = findViewById(R.id.btnSaveQuote); 
        btnCancelQuote = findViewById(R.id.btnCancelQuote);
        
        // Only set listeners if buttons exist (they may be null if we're removing this feature)
        if (btnCopyQuote != null) {
            btnCopyQuote.setOnClickListener(v -> {
                copySelectedTextToClipboard();
                hideQuotePanel();
                clearTextSelection();
            });
        }
        
        if (btnSaveQuote != null) {
            btnSaveQuote.setOnClickListener(v -> {
                saveQuoteToDatabase();
                hideQuotePanel();
                clearTextSelection();
            });
        }
        
        if (btnCancelQuote != null) {
            btnCancelQuote.setOnClickListener(v -> {
                hideQuotePanel();
                clearTextSelection();
            });
        }
        
        // Add button click listeners for TOC and search (only if buttons exist)
        if (btnToc != null) {
            btnToc.setOnClickListener(v -> showTableOfContents());
        }
        
        if (btnCloseToc != null) {
            btnCloseToc.setOnClickListener(v -> hideTocPanel());
        }

        
        if (btnCloseSearch != null) {
            btnCloseSearch.setOnClickListener(v -> hideSearchPanel());
        }
        
        if (btnPrevResult != null) {
            btnPrevResult.setOnClickListener(v -> navigateToPreviousSearchResult());
        }
        
        if (btnNextResult != null) {
            btnNextResult.setOnClickListener(v -> navigateToNextSearchResult());
        }
        
        // Setup search
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(v.getText().toString());
                return true;
            }
            return false;
        });
        
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
                @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 3) {
                    performSearch(s.toString());
                } else if (s.length() == 0) {
                    clearSearch();
                }
            }
        });
        
        // Initialize TOC RecyclerView if TOC panel exists
        tocRecyclerView = findViewById(R.id.tocRecyclerView);
        
        // Настраиваем RecyclerView для оглавления (только если он существует)
        if (tocRecyclerView != null) {
            tocRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            tocItems = new ArrayList<>();
            tocAdapter = new TocAdapter(this, tocItems);
            tocRecyclerView.setAdapter(tocAdapter);
            
            // Настраиваем обработчик нажатия на элемент оглавления
            tocAdapter.setOnTocItemClickListener((item, position) -> {
                navigateToPage(item.getPageNumber() - 1); // -1 так как pageNumber начинается с 1
                hideTocPanel();
            });
        }

        // Get book data from intent
        Intent intent = getIntent();
        bookId = intent.getStringExtra("id");
        bookTitle = intent.getStringExtra("title");
        savedCurrentPage = intent.getIntExtra("currentPage", 0);
        
        if (bookTitle != null) {
            titleText.setText(bookTitle);
        }
        
        if (bookId == null) {
            Log.e(TAG, "No book ID provided");
            Toast.makeText(this, "Ошибка: Не указан ID книги", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Получаем URI файла из Intent
        Uri fileUri = intent.getParcelableExtra("fileUri");
        
        if (fileUri == null) {
            // Проверяем, есть ли путь к локальному файлу
            String filePath = intent.getStringExtra("filePath");
            if (filePath != null && !filePath.isEmpty()) {
                // Создаем URI из пути к файлу
                fileUri = Uri.fromFile(new File(filePath));
                Log.d(TAG, "Created file URI from path: " + fileUri);
            } else {
                Log.e(TAG, "No file URI or path provided");
                Toast.makeText(this, "Ошибка: Не указан путь к файлу", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        
        Log.d(TAG, "Loading book: ID=" + bookId + ", Title=" + bookTitle + ", SavedPage=" + savedCurrentPage + ", URI=" + fileUri);
        
        // Загружаем содержимое книги
        loadBookContent(fileUri);
    }
    
    /**
     * Загружает содержимое книги
     */
    private void loadBookContent(Uri fileUri) {
        BookFileReader.readBookContent(this, fileUri, new BookFileReader.BookContentCallback() {
            @Override
            public void onContentReady(List<String> loadedPages) {
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    contentWebView.setVisibility(View.VISIBLE);
                    
                    if (loadedPages != null && !loadedPages.isEmpty()) {
                        pages = loadedPages;
                        
                        // Update page count in Supabase
                        updatePageCountInSupabase(pages.size());
                        
                        // Настраиваем SeekBar
                        pageProgressBar.setMax(pages.size() - 1);
                        
                        // Restore from saved position
                        currentPage = (savedCurrentPage > 0 && savedCurrentPage < pages.size()) 
                                     ? savedCurrentPage - 1 : 0;
                        
                        Log.d(TAG, "Setting initial page to: " + (currentPage + 1) + 
                               " of " + pages.size() + " (saved: " + savedCurrentPage + ")");
                        
                        pageProgressBar.setProgress(currentPage);
                        showPage(currentPage);
                        
                        // Теперь, когда контент загружен, генерируем оглавление
                        generateTableOfContents(fileUri);
                    } else {
                        Toast.makeText(BookReaderActivity.this, "Файл не содержит текста", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // More detailed error message
                    String detailedError = "Ошибка загрузки файла: " + error;
                    if (fileUri.getPath() == null) {
                        detailedError += "\nНеверный путь к файлу";
                    } else if (!fileUri.getPath().contains(".")) {
                        detailedError += "\nФайл не имеет расширения";
                    }
                    Log.e(TAG, "Damn");
                    Toast.makeText(BookReaderActivity.this, detailedError, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Генерирует оглавление книги
     */
    private void generateTableOfContents(Uri fileUri) {
        // Skip if tocRecyclerView doesn't exist
        if (tocRecyclerView == null) {
            Log.d(TAG, "generateTableOfContents: tocRecyclerView is null, skipping TOC generation");
            return;
        }
        
        // Инициализируем пустой список элементов и адаптер
        tocItems = new ArrayList<>();
        tocAdapter = new TocAdapter(this, tocItems);
        tocRecyclerView.setAdapter(tocAdapter);
        
        // Показываем индикатор загрузки
        ProgressBar tocProgressBar = findViewById(R.id.tocProgressBar);
        if (tocProgressBar != null) {
            tocProgressBar.setVisibility(View.VISIBLE);
        }
        
        // Вызываем метод извлечения оглавления
        BookFileReader.generateTableOfContents(this, fileUri, new BookFileReader.TocCallback() {
            @Override
            public void onTocReady(List<TocItem> items) {
                runOnUiThread(() -> {
                    // Очищаем и добавляем новые элементы
                    tocItems.clear();
                    tocItems.addAll(items);
                    tocAdapter.notifyDataSetChanged();
                    
                    // Скрываем индикатор загрузки
                    if (tocProgressBar != null) {
                        tocProgressBar.setVisibility(View.GONE);
                    }
                    
                    Log.d(TAG, "Table of contents loaded successfully: " + items.size() + " items");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // В случае ошибки более тщательно анализируем текст для формирования оглавления
                    tocItems.clear();
                    
                    if (pages != null && !pages.isEmpty()) {
                        Log.d(TAG, "Формирование альтернативного оглавления на основе анализа текста книги...");
                        
                        // Обрабатываем все страницы книги для поиска потенциальных заголовков
                        int maxPagesToCheck = Math.min(pages.size(), 100); // Проверяем до 100 страниц
                        int chapterCount = 0;
                        
                        // Улучшенные паттерны для поиска заголовков
                        Pattern headerPattern = Pattern.compile(
                            // HTML-заголовки
                            "<h[1-6][^>]*>(.*?)</h[1-6]>|" +
                            // Жирный текст
                            "<(?:b|strong)[^>]*>(.*?)</(?:b|strong)>|" +
                            // Текст с увеличенным размером шрифта
                            "<font\\s+size=[\"']?(?:\\+|[4-7])[\"']?[^>]*>(.*?)</font>|" +
                            // Текст с большим размером через стили
                            "<[^>]*style=[\"'][^\"']*font-size\\s*:\\s*(?:large|x-large|xx-large|[1-9][0-9]px)[^\"']*[\"'][^>]*>(.*?)</[^>]*>|" +
                            // Заголовочные элементы
                            "<p[^>]*class=[\"'](?:title|heading|chapter|header)[\"'][^>]*>(.*?)</p>|" +
                            // Традиционные паттерны названий глав
                            "(?i)<p[^>]*>\\s*(?:глава|chapter|часть|part|раздел|section)\\s+(?:\\d+|[IVXLCDM]+)(?:[.:].*)?" +
                            "</p>"
                        );
                        
                        // Дополнительный паттерн для текста без тегов (может быть заголовком)
                        Pattern textHeaderPattern = Pattern.compile(
                            "(?i)^\\s*(?:глава|chapter|часть|part|раздел|section)\\s+(?:\\d+|[IVXLCDM]+)[.:](.*?)$|" +
                            "^\\s*(?:\\d+|[IVXLCDM]+)[.:]\\s+(.*?)$"
                        );
                        
                        // Определяем минимальную длину заголовков, чтобы избежать коротких фраз
                        int minHeaderLength = 5;
                        int maxHeaderLength = 150;
                        
                        // Проходим по всем страницам и ищем заголовки
                        for (int i = 0; i < maxPagesToCheck; i++) {
                            String content = pages.get(i);
                            
                            // Проверяем HTML-заголовки первым делом
                            Matcher headerMatcher = headerPattern.matcher(content);
                            boolean foundHeaderOnPage = false;
                            
                            while (headerMatcher.find()) {
                                String headerText = null;
                                
                                // Получаем текст из группы (проверяем все группы, т.к. паттерн с альтернативами)
                                for (int g = 1; g <= headerMatcher.groupCount(); g++) {
                                    if (headerMatcher.group(g) != null) {
                                        headerText = headerMatcher.group(g).trim();
                                        break;
                                    }
                                }
                                
                                // Если заголовок не найден в группах, пробуем взять полное совпадение
                                // и удалить из него HTML-теги
                                if (headerText == null || headerText.isEmpty()) {
                                    headerText = headerMatcher.group().replaceAll("<[^>]*>", "").trim();
                                }
                                
                                // Очищаем текст от оставшихся HTML-тегов и специальных символов
                                if (headerText != null) {
                                    headerText = headerText.replaceAll("<[^>]*>", "")
                                                          .replaceAll("&[^;]+;", " ")
                                                          .trim();
                                    
                                    // Проверяем длину заголовка после очистки
                                    if (headerText.length() >= minHeaderLength && 
                                        headerText.length() <= maxHeaderLength) {
                                        
                                        // Обрезаем слишком длинные заголовки
                                        if (headerText.length() > 80) {
                                            headerText = headerText.substring(0, 77) + "...";
                                        }
                                        
                                        // Добавляем в оглавление
                                        tocItems.add(new TocItem(headerText, i + 1, 1));
                                        chapterCount++;
                                        foundHeaderOnPage = true;
                                        
                                        Log.d(TAG, "Найден заголовок: \"" + headerText + 
                                              "\" на странице " + (i + 1));
                                    }
                                }
                            }
                            
                            // Если на странице не нашли HTML-заголовки, ищем текстовые заголовки
                            if (!foundHeaderOnPage) {
                                // Разбиваем контент на строки для анализа
                                String[] lines = content.split("\\n|<br>|<br/>|<br />|<p>|</p>");
                                
                                for (String line : lines) {
                                    // Удаляем HTML-теги для анализа
                                    String textLine = line.replaceAll("<[^>]*>", "").trim();
                                    
                                    if (textLine.isEmpty() || textLine.length() < minHeaderLength || 
                                        textLine.length() > maxHeaderLength) {
                                        continue;
                                    }
                                    
                                    // Проверяем текстовые паттерны заголовков
                                    Matcher textMatcher = textHeaderPattern.matcher(textLine);
                                    
                                    // Эвристики для выявления заголовков без специальных маркеров
                                    boolean isPotentialHeader = 
                                        // Короткая строка в верхнем регистре
                                        (textLine.equals(textLine.toUpperCase()) && 
                                         textLine.length() < 60 && textLine.length() > 10) ||
                                        // Строка с минимальной пунктуацией, похожая на название
                                        (textLine.length() < 80 && 
                                         !textLine.contains(". ") && 
                                         !textLine.contains("! ") && 
                                         !textLine.contains("? ") && 
                                         !textLine.contains(", "));
                                    
                                    if (textMatcher.find() || isPotentialHeader) {
                                        // Получаем текст заголовка
                                        String headerText = textLine;
                                        
                                        // Если нашли через регулярное выражение, извлекаем группу
                                        if (textMatcher.find(0)) {
                                            for (int g = 1; g <= textMatcher.groupCount(); g++) {
                                                if (textMatcher.group(g) != null && !textMatcher.group(g).isEmpty()) {
                                                    headerText = textMatcher.group(g).trim();
                                                    break;
                                                }
                                            }
                                        }
                                        
                                        // Добавляем в оглавление
                                        if (headerText.length() > 80) {
                                            headerText = headerText.substring(0, 77) + "...";
                                        }
                                        
                                        tocItems.add(new TocItem(headerText, i + 1, 1));
                                        chapterCount++;
                                        
                                        Log.d(TAG, "Найден текстовый заголовок: \"" + headerText + 
                                              "\" на странице " + (i + 1));
                                        
                                        // Один заголовок на страницу достаточно
                                        break;
                                    }
                                }
                            }
                            
                            // Ограничиваем количество заголовков
                            if (chapterCount >= 50) break;
                        }
                        
                        // Если заголовки вообще не найдены, добавляем хотя бы начало
                        if (tocItems.isEmpty()) {
                            tocItems.add(new TocItem("Начало книги", 1, 1));
                            Log.d(TAG, "Заголовки в книге не найдены, добавлено только 'Начало книги'");
                        }
                    } else {
                        // Если страниц нет, добавляем хотя бы базовый элемент
                        tocItems.add(new TocItem("Начало книги", 1, 1));
                    }
                    
                    tocAdapter.notifyDataSetChanged();
                    
                    // Скрываем индикатор загрузки
                    ProgressBar tocProgressBar = findViewById(R.id.tocProgressBar);
                    if (tocProgressBar != null) {
                        tocProgressBar.setVisibility(View.GONE);
                    }
                    
                    Log.w(TAG, "Ошибка при загрузке оглавления: " + error);
                    Log.d(TAG, "Создан альтернативный вариант оглавления с " + tocItems.size() + " элементами");
                });
            }
        });
    }

    private void updatePageCountInSupabase(int pageCount) {
        if (bookId == null) return;
        
        supabaseService.updateBookPageCount(bookId, pageCount, new SupabaseService.BookProgressCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully updated page count to " + pageCount);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to update page count: " + error);
            }
        });
    }
    
    private void syncCurrentPageToSupabase() {
        if (bookId == null) {
            Log.e(TAG, "syncCurrentPageToSupabase: bookId is null");
            return;
        }

        // Прекращаем все предыдущие отложенные синхронизации
        if (pendingProgressSync != null) {
            progressHandler.removeCallbacks(pendingProgressSync);
            pendingProgressSync = null;
        }

        // Проверяем, что книга действительно открыта
        if (currentPage < 0 || pages == null || pages.isEmpty()) {
            Log.e(TAG, "syncCurrentPageToSupabase: Invalid state - currentPage=" + currentPage + ", pages size=" + (pages != null ? pages.size() : "null"));
            return;
        }

        // Сохраняем текущие значения для потока
        final int pageToSync = currentPage + 1; // Преобразуем из 0-based в 1-based
        final String bookIdToSync = bookId;

        // Немедленно запускаем синхронизацию в отдельном потоке
        new Thread(() -> {
            try {
                Log.d(TAG, "⏱️ Синхронизация страницы " + pageToSync + " с Supabase...");
                SupabaseService service = new SupabaseService(BookReaderActivity.this);
                service.forceUpdateBookPage(bookIdToSync, pageToSync);
                Log.d(TAG, "✅ Страница " + pageToSync + " успешно синхронизирована");
            } catch (Exception e) {
                Log.e(TAG, "❌ Ошибка при синхронизации страницы " + pageToSync + ": " + e.getMessage());
                
                // Планируем повторную попытку через 3 секунды
                progressHandler.postDelayed(() -> {
                    try {
                        Log.d(TAG, "🔄 Повторная попытка синхронизации страницы " + pageToSync);
                        SupabaseService service = new SupabaseService(BookReaderActivity.this);
                        service.forceUpdateBookPage(bookIdToSync, pageToSync);
                        Log.d(TAG, "✅ Страница " + pageToSync + " успешно синхронизирована со второй попытки");
                    } catch (Exception retryEx) {
                        Log.e(TAG, "❌❌ Критическая ошибка при повторной синхронизации: " + retryEx.getMessage());
                    }
                }, 3000);
            }
        }).start();
    }

    // Проверяет соответствие текущей страницы в Supabase
    private void verifyPageUpdateInSupabase() {
        if (bookId == null) return;
        
        final int expectedPage = currentPage + 1;
        
        Log.d(TAG, "🔍 Проверка обновления страницы в Supabase: ожидается " + expectedPage);
        
        supabaseService.getBookById(bookId, new SupabaseService.BookCallback() {
            @Override
            public void onSuccess(Book book) {
                if (book.getCurrentPage() != expectedPage) {
                    Log.w(TAG, "⚠️ Проверка не удалась: страница в БД (" + book.getCurrentPage() + 
                            ") не соответствует ожидаемой (" + expectedPage + ")");
                    
                    // Повторная синхронизация только если это все еще текущая страница
                    if (currentPage + 1 == expectedPage) {
                        synchronized (syncLock) {
                            if (!syncInProgress) {
                                Log.d(TAG, "🔄 Принудительное обновление страницы " + expectedPage);
                                
                                syncInProgress = true;
                                
                                supabaseService.updateBookProgress(bookId, expectedPage, new SupabaseService.BookProgressCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "✓ Принудительное обновление страницы успешно");
                                        synchronized (syncLock) {
                                            syncInProgress = false;
                                        }
                }

                @Override
                public void onError(String error) {
                                        Log.e(TAG, "❌ Ошибка принудительного обновления: " + error);
                                        synchronized (syncLock) {
                                            syncInProgress = false;
                                        }
                                    }
                                });
                            }
                        }
                    } else {
                        Log.d(TAG, "Пропуск повторной синхронизации, т.к. пользователь перешел на другую страницу");
                    }
                } else {
                    Log.d(TAG, "✓ Подтверждено: страница " + expectedPage + " корректно сохранена в Supabase");
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Ошибка проверки обновления страницы: " + error);
            }
        });
    }

    private void showPage(int pageNumber) {
        if (pages == null || pageNumber < 0 || pageNumber >= pages.size()) {
            Log.e(TAG, "Invalid page number: " + pageNumber);
            return;
        }

        currentPage = pageNumber;
        
        // Проверяем, достигли ли мы конца книги
        if (currentPage == pages.size() - 1) {
            showBookFinishedPrompt();
        }
        
        String content = pages.get(pageNumber);
        
        // Если у нас есть активный поиск, подсвечиваем результаты
        if (!lastSearchQuery.isEmpty()) {
            content = BookFileReader.highlightSearchResults(content, lastSearchQuery);
        }

        // Set WebView scale to ensure content fits properly
        contentWebView.setInitialScale(100);
        
        // Calculate content padding
        int paddingDp = 8; // reduced from 16dp
        int paddingPx = (int) (paddingDp * getResources().getDisplayMetrics().density);
        
        // Get current theme
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentTheme = preferences.getString(KEY_THEME, "light");
        
        // Set theme colors based on preference
        String backgroundColor = "#FFFFFF"; // Default light
        String textColor = "#333333";
        
        switch (currentTheme) {
            case "dark":
                backgroundColor = "#1E1E1E";
                textColor = "#E0E0E0";
                break;
            case "sepia":
                backgroundColor = "#F5E6D3";
                textColor = "#4A3C2C";
                break;
            case "light":
            default:
                backgroundColor = "#FFFFFF";
                textColor = "#333333";
                break;
        }

        String htmlContent = "<html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=1\">" +
            "<style>" +
            "body { " +
            "   font-family: sans-serif; " +
            "   font-size: 40px; " +
            "   line-height: 1.6; " +
            "   padding: " + paddingPx + "px; " +
            "   margin: 0; " +
            "   text-align: justify; " +
            "   hyphens: auto; " +
            "   word-wrap: break-word; " +
            "   max-width: 100%; " +
            "   background-color: " + backgroundColor + "; " +
            "   color: " + textColor + "; " +
            "   -webkit-user-select: text !important; " +
            "   user-select: text !important; " +
            "   -webkit-touch-callout: default !important; " +
            "}" +
            "* { " +
            "   -webkit-user-select: text !important; " +
            "   user-select: text !important; " +
            "   -webkit-touch-callout: default !important; " +
            "}" +
            "h1, h2, h3 { " +
            "   text-align: center; " +
            "   margin: 12px 0; " +
            "   font-size: 40px; " +
            "   color: " + textColor + "; " +
            "}" +
            "p { " +
            "   margin: 8px 0; " +
            "   text-align: justify; " +
            "   text-justify: inter-word; " +
            "   max-width: 100%; " +
            "   font-size: 40px; " +
            "   color: " + textColor + "; " +
            "}" +
            "img { max-width: 100%; height: auto; }" +
            "span.highlight { background-color: yellow; color: black; }" +
            "</style>" +
            "</head><body>" + content + "</body></html>";

        contentWebView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
        updatePageIndicator();
        
        // Apply text selection settings with a delay
        new Handler().postDelayed(() -> {
            // Disable text selection on this page
            disableTextSelection();
        }, 500);
        
        // Update the SeekBar position
        pageProgressBar.setProgress(currentPage);
        
        // Log the page change before Supabase update
        Log.d(TAG, "📖 Changed to page " + (currentPage + 1) + " - updating Supabase...");
        
        // Update page progress in Supabase - do this immediately
        syncCurrentPageToSupabase();
    }
    
    /**
     * Показывает предложение оценить книгу при достижении последней страницы
     */
    private void showBookFinishedPrompt() {
        Toast.makeText(this, "Вы достигли конца книги! Хотите оставить рецензию?", Toast.LENGTH_LONG).show();
        // Здесь можно добавить диалог с предложением оценить книгу и написать рецензию
    }
    
    /**
     * Выполняет поиск по тексту книги
     */
    private void performSearch(String query) {
        if (query.isEmpty() || pages == null || pages.isEmpty()) {
            return;
        }
        
        lastSearchQuery = query;
        searchResults = BookFileReader.searchInBook(pages, query);
        currentSearchIndex = -1;
        
        if (searchResults.isEmpty()) {
            searchResultsCount.setText("Совпадений не найдено");
            btnPrevResult.setEnabled(false);
            btnNextResult.setEnabled(false);
        } else {
            searchResultsCount.setText("Найдено: " + searchResults.size());
            btnPrevResult.setEnabled(true);
            btnNextResult.setEnabled(true);
            // Переходим к первому результату
            navigateToNextSearchResult();
        }
    }
    
    /**
     * Очищает результаты поиска
     */
    private void clearSearch() {
        lastSearchQuery = "";
        searchResults.clear();
        currentSearchIndex = -1;
        searchResultsCount.setText("Совпадений не найдено");
        btnPrevResult.setEnabled(false);
        btnNextResult.setEnabled(false);
        
        // Отображаем текущую страницу без подсветки
        showPage(currentPage);
    }
    
    /**
     * Переходит к следующему результату поиска
     */
    private void navigateToNextSearchResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        
        currentSearchIndex++;
        if (currentSearchIndex >= searchResults.size()) {
            currentSearchIndex = 0; // Циклически возвращаемся к первому результату
        }
        
        // Переходим к странице с результатом
        int resultPage = searchResults.get(currentSearchIndex);
        navigateToPage(resultPage);
        
        // Обновляем индикатор
        searchResultsCount.setText("Результат " + (currentSearchIndex + 1) + " из " + searchResults.size());
    }
    
    /**
     * Переходит к предыдущему результату поиска
     */
    private void navigateToPreviousSearchResult() {
        if (searchResults.isEmpty()) {
            return;
        }
        
        currentSearchIndex--;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchResults.size() - 1; // Циклически переходим к последнему результату
        }
        
        // Переходим к странице с результатом
        int resultPage = searchResults.get(currentSearchIndex);
        navigateToPage(resultPage);
        
        // Обновляем индикатор
        searchResultsCount.setText("Результат " + (currentSearchIndex + 1) + " из " + searchResults.size());
    }
    
    /**
     * Переход к странице без анимации
     */
    private void navigateToPage(int pageNumber) {
        if (pages == null || pageNumber < 0 || pageNumber >= pages.size()) {
            Log.e(TAG, "Invalid page number: " + pageNumber);
            Toast.makeText(this, "Неверный номер страницы", Toast.LENGTH_SHORT).show();
            return;
        }
        showPage(pageNumber);
    }
    
    /**
     * Показывает панель оглавления
     */
    private void showTableOfContents() {
        hidePanels();
        
        // By default, show the chapters tab
        showChapters();
        
        // Set the correct tab colors
        Button btnChaptersTab = findViewById(R.id.btnChaptersTab);
        Button btnQuotesTab = findViewById(R.id.btnQuotesTab);
        btnChaptersTab.setTextColor(Color.BLACK);
        btnQuotesTab.setTextColor(Color.GRAY);
        
        // Show the panel
        tocPanel.setVisibility(View.VISIBLE);
    }
    
    /**
     * Скрывает панель оглавления
     */
    private void hideTocPanel() {
        tocPanel.setVisibility(View.GONE);
    }
    
    /**
     * Показывает панель поиска
     */
    private void showSearchPanel() {
        hidePanels();
        searchPanel.setVisibility(View.VISIBLE);
        searchEditText.requestFocus();
    }
    
    /**
     * Скрывает панель поиска
     */
    private void hideSearchPanel() {
        searchPanel.setVisibility(View.GONE);
        clearSearch();
    }
    
    /**
     * Скрывает все панели интерфейса
     */
    private void hidePanels() {
        topPanel.setVisibility(View.GONE);
        bottomPanel.setVisibility(View.GONE);
        tocPanel.setVisibility(View.GONE);
        searchPanel.setVisibility(View.GONE);
        panelsVisible = false;
    }

    private void prevPage() {
        if (currentPage > 0) {
            showPage(currentPage - 1);
        }
    }

    private void nextPage() {
        if (pages != null && currentPage < pages.size() - 1) {
            showPage(currentPage + 1);
        }
    }

    private void updatePageIndicator() {
        if (pages != null) {
            pageIndicator.setText(String.format("%d/%d", currentPage + 1, pages.size()));
        }
    }

    private void togglePanels() {
        // Add cooldown check to prevent rapid toggling
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTouchTime < TOUCH_COOLDOWN) {
            return;
        }
        lastTouchTime = currentTime;
        
        // Скрываем все панели кроме основных
        tocPanel.setVisibility(View.GONE);
        searchPanel.setVisibility(View.GONE);
        
        // Переключаем видимость основных панелей
        panelsVisible = !panelsVisible;
        int visibility = panelsVisible ? View.VISIBLE : View.GONE;
        topPanel.setVisibility(visibility);
        bottomPanel.setVisibility(visibility);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveFinalPageState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.menu_search) {
            showSearchPanel();
            return true;
        } else if (id == R.id.menu_settings) {
            showSettingsDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Если открыта панель оглавления или поиска, закрываем её
        if (tocPanel.getVisibility() == View.VISIBLE) {
            hideTocPanel();
            return;
        }
        
        if (searchPanel.getVisibility() == View.VISIBLE) {
            hideSearchPanel();
            return;
        }
        
        // Иначе закрываем активность
        saveFinalPageState();
            finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveFinalPageState();
    }
    
    /**
     * Ensures the current page is saved to Supabase when leaving the activity
     */
    private void saveFinalPageState() {
        if (bookId == null) {
            Log.e(TAG, "Cannot save final state: bookId is null");
            return;
        }

        // Получаем актуальные значения
        int finalCurrentPage = currentPage + 1; // Преобразуем из 0-based в 1-based
        int finalPageCount = pages.size();
        
        if (finalCurrentPage <= 0 || finalPageCount <= 0) {
            Log.e(TAG, "Invalid page values: current=" + finalCurrentPage + ", total=" + finalPageCount);
            return;
        }

        Log.d(TAG, "Saving final page state: current=" + finalCurrentPage + ", total=" + finalPageCount);
        
        // Используем принудительную синхронизацию
        SupabaseService supabaseService = new SupabaseService(this);
        
        // Синхронизируем данные в отдельном потоке
        new Thread(() -> {
            try {
                // Принудительно обновляем текущую страницу
                supabaseService.forceUpdateBookPage(bookId, finalCurrentPage);
                
                // Затем обновляем общее количество страниц (если оно изменилось)
                if (pages.size() != finalPageCount) {
                    supabaseService.updateBookPageCount(bookId, finalPageCount, null);
                }
                
                Log.d(TAG, "✅ Финальное состояние успешно сохранено: страница " + finalCurrentPage);
            } catch (Exception e) {
                Log.e(TAG, "❌ Ошибка при сохранении финального состояния: " + e.getMessage(), e);
            }
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Добавляем сохранение состояния при onStop для большей надежности
        saveFinalPageState();
    }
    
    // Верифицируем при загрузке
    @Override
    protected void onResume() {
        super.onResume();
        
        // Apply theme to UI components
        applyThemeToUI();
        

        
        // Проверяем, соответствует ли текущая страница сохраненной в базе
        if (bookId != null && currentPage > 0) {
            supabaseService.verifyAndUpdateBookData(bookId, currentPage);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reader, menu);

        // Remove table of contents and quotes menu items
        MenuItem tocItem = menu.findItem(R.id.menu_toc);
        if (tocItem != null) {
            tocItem.setVisible(false);
        }
        return true;
    }

    private void setTheme(String theme) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit()
                .putString(KEY_THEME, theme)
                .apply();
        
        applyBookTheme(theme);
        recreate();
    }
    
    private void applyBookTheme(String theme) {
        // Apply theme to WebView content
        if (contentWebView != null && pages != null && currentPage >= 0 && currentPage < pages.size()) {
            showPage(currentPage);
        }
    }

    private void applyThemeToUI() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentTheme = prefs.getString(KEY_THEME, "light");
        applyBookTheme(currentTheme);
    }
    
    /**
     * JavaScript interface for communication between the WebView and Android
     */
    private class WebAppInterface {
        @JavascriptInterface
        public void onTextSelected(String text) {
            if (text != null && !text.trim().isEmpty()) {
                selectedText = text.trim();
                isTextSelected = true;
                runOnUiThread(() -> showQuotePanel());
            }
        }
    }
    
    /**
     * Injects JavaScript to disable text selection in the WebView
     */
    private void disableTextSelection() {
        String disableSelectionJs =
            "document.body.style.webkitUserSelect = 'none';" +
            "document.body.style.userSelect = 'none';" +
            "document.body.style.webkitTouchCallout = 'none';" +
            "document.body.setAttribute('contenteditable', 'false');" +
            "document.body.setAttribute('unselectable', 'on');" +
            "document.documentElement.style.webkitUserSelect = 'none';" +
            "document.documentElement.style.userSelect = 'none';" +
            "document.documentElement.style.webkitTouchCallout = 'none';" +
            "var css = '*{-webkit-user-select:none !important;-moz-user-select:none !important;-ms-user-select:none !important;user-select:none !important;}';" +
            "var style = document.createElement('style');" +
            "style.innerHTML = css;" +
            "document.head.appendChild(style);";
            
        contentWebView.evaluateJavascript(disableSelectionJs, null);
    }
    
    /**
     * Shows the quote panel with options to copy or save quote
     */
    private void showQuotePanel() {
        // Hide other panels if visible
        hidePanels();
        
        // Show quote panel
        quotePanel.setVisibility(View.VISIBLE);
    }
    
    /**
     * Hides the quote panel
     */
    private void hideQuotePanel() {
        quotePanel.setVisibility(View.GONE);
    }
    
    /**
     * Clears the text selection in the WebView
     */
    private void clearTextSelection() {
        String js = "window.getSelection().removeAllRanges();";
        contentWebView.evaluateJavascript(js, null);
        isTextSelected = false;
        selectedText = "";
    }
    
    /**
     * Copies the selected text to the clipboard
     */
    private void copySelectedTextToClipboard() {
        if (selectedText.isEmpty()) return;
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Selected Text", selectedText);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Текст скопирован", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Saves the selected quote to the database
     */
    private void saveQuoteToDatabase() {
        if (selectedText.isEmpty() || bookId == null) return;
        
        // Get user ID from SupabaseService
        String userId = supabaseService.getUserId();
        if (userId == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create a new Quote object
        Quote quote = new Quote(
                UUID.randomUUID().toString(),
                bookId,
                userId,
                selectedText,
                currentPage + 1, // Сохраняем как 1-based
                currentPage + 1,
                System.currentTimeMillis()
        );
        
        // Save the quote to Supabase
        supabaseService.saveQuote(quote, new SupabaseService.QuoteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(BookReaderActivity.this, "Цитата сохранена", Toast.LENGTH_SHORT).show();
                    // Reload quotes if we're displaying them
                    if (isShowingQuotes) {
                        loadBookQuotes();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(BookReaderActivity.this, "Ошибка: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Loads the quotes for the current book from the database
     */
    private void loadBookQuotes() {
        if (bookId == null) return;
        
        supabaseService.getQuotesForBook(bookId, new SupabaseService.QuotesLoadCallback() {
            @Override
            public void onSuccess(List<Quote> quotes) {
                bookQuotes = quotes;
                runOnUiThread(() -> {
                    if (isShowingQuotes) {
                        displayQuotes();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(BookReaderActivity.this, "Ошибка загрузки цитат: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Displays the quotes in the table of contents panel
     */
    private void displayQuotes() {
        // Clear existing content
        tocRecyclerView.setAdapter(null);

        // Set title
        ((TextView) tocPanel.findViewById(R.id.tocTitle)).setText("Цитаты");

        // Set up recycler view for quotes
        quotesRecyclerView = tocRecyclerView;
        quotesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Create and set adapter
        QuotesAdapter adapter = new QuotesAdapter(bookQuotes, quote -> {
            // Handle quote click - navigate to the page
            int pageNumber = quote.getStartPage();

            // Ensure page number is valid (convert to 0-based if needed)
            if (pageNumber >= 1 && pages != null && pageNumber <= pages.size()) {
                // TocItem uses 1-based, our pages are 0-based
                navigateToPage(pageNumber - 1);
                hideTocPanel();

                // Optional: highlight the quote text
                new Handler().postDelayed(() -> {
                    highlightQuote(quote.getText());
                }, 500);
            } else {
                Toast.makeText(BookReaderActivity.this,
                        "Не удалось перейти к странице цитаты",
                        Toast.LENGTH_SHORT).show();
            }
        });

        quotesRecyclerView.setAdapter(adapter);

        // Show the panel
        tocPanel.setVisibility(View.VISIBLE);
    }
    /**
     * Adds JavaScript to highlight the saved quote in the text
     */
    private void highlightQuote(String quoteText) {
        if (quoteText == null || quoteText.isEmpty()) return;

        try {
            // Escape special characters for JavaScript
            String escapedText = quoteText
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");

            // More robust highlighting script
            String js =
                    "function highlightText(text) {" +
                            "  var bodyHTML = document.body.innerHTML;" +
                            "  var regex = new RegExp(text.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&'), 'gi');" +
                            "  var highlightedHTML = bodyHTML.replace(regex, function(match) {" +
                            "    return '<span class=\"highlight-quote\" style=\"background-color: yellow;\">' + match + '</span>';" +
                            "  });" +
                            "  document.body.innerHTML = highlightedHTML;" +
                            "}" +
                            "highlightText('" + escapedText + "');";

            contentWebView.evaluateJavascript(js, null);

        } catch (Exception e) {
            Log.e(TAG, "Error highlighting quote: " + e.getMessage());
        }
    }

    /**
     * Shows the chapters view in the table of contents panel
     */
    private void showChapters() {
        isShowingQuotes = false;
        
        // Clear existing content
        tocRecyclerView.setAdapter(null);
        
        // Set title
        ((TextView) tocPanel.findViewById(R.id.tocTitle)).setText("Оглавление");
        
        // Create adapter with the book chapters
        tocAdapter = new TocAdapter(this, tocItems);
        
        // Set up click listener
        tocAdapter.setOnTocItemClickListener(new TocAdapter.OnTocItemClickListener() {
            @Override
            public void onTocItemClick(TocItem item, int position) {
                // Получаем номер страницы (индекс 0-based, но TocItem хранит 1-based)
                int pageToNavigate = item.getPageNumber() - 1;
                
                // Проверяем валидность номера страницы
                if (pageToNavigate >= 0 && pages != null && pageToNavigate < pages.size()) {
                    // Переходим к выбранной странице
                    navigateToPage(pageToNavigate);
                    
                    // Скрываем панель оглавления
                    hideTocPanel();
                    
                    // Логируем переход
                    Log.d(TAG, "Navigated to TOC item: " + item.getTitle() + " (page " + item.getPageNumber() + ")");
                } else {
                    Log.e(TAG, "Invalid page number in TOC: " + item.getPageNumber() + 
                          ", valid range: 1-" + (pages != null ? pages.size() : 0));
                }
            }
        });
        
        tocRecyclerView.setAdapter(tocAdapter);
        
        // Show the panel
        tocPanel.setVisibility(View.VISIBLE);
    }



    /**
     * Shows the settings dialog for reader appearance
     */
    private void showSettingsDialog() {
        // Create theme options dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Настройки чтения");
        
        String[] themes = {"Светлая тема", "Темная тема", "Сепия"};
        builder.setItems(themes, (dialog, which) -> {
            String theme;
            switch (which) {
                case 0:
                    theme = "light";
                    break;
                case 1:
                    theme = "dark";
                    break;
                case 2:
                    theme = "sepia";
                    break;
                default:
                    theme = "light";
                    break;
            }
            setTheme(theme);
            dialog.dismiss();
        });
        
        builder.show();
    }
}
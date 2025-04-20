package com.example.bookworm;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.adapters.ShelfBookAdapter;
import com.example.bookworm.models.Shelf;
import com.example.bookworm.services.SupabaseService;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ShelfDetailActivity extends BaseActivity implements ShelfBookAdapter.ShelfBookListener {
    private static final String TAG = "ShelfDetailActivity";
    private RecyclerView recyclerViewBooks;
    private TextView textViewNoBooks;
    private TextView textViewShelfDescription;
    private ShelfBookAdapter bookAdapter;
    private Shelf shelf;
    private List<Book> books = new ArrayList<>();
    private ProgressBar progressBarLoading;
    private SupabaseService supabaseService;
    private boolean isLoadingBooks = false; // Flag to track when book loading is in progress
    private boolean initialLoadComplete = false; // Flag to track if initial load is done

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shelf_detail);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        recyclerViewBooks = findViewById(R.id.recyclerViewBooks);
        textViewNoBooks = findViewById(R.id.textViewNoBooks);
        textViewShelfDescription = findViewById(R.id.textViewShelfDescription);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        
        supabaseService = new SupabaseService(this);
        
        // Get shelf ID from intent
        String shelfId = getIntent().getStringExtra("SHELF_ID");
        if (shelfId == null) {
            finish();
            return;
        }
        
        // Load shelf details from Supabase
        loadShelfDetails(shelfId);
    }
    
    private void loadShelfDetails(String shelfId) {
        progressBarLoading.setVisibility(View.VISIBLE);
        
        supabaseService.getShelves(new SupabaseService.ShelvesLoadCallback() {
            @Override
            public void onSuccess(List<Shelf> shelves) {
                for (Shelf s : shelves) {
                    if (s.getId().equals(shelfId)) {
                        shelf = s;
                        break;
                    }
                }
                
                if (shelf == null) {
                    Snackbar.make(findViewById(android.R.id.content), "Shelf not found", Snackbar.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                
                // Set up UI
                getSupportActionBar().setTitle(shelf.getName());
                
                if (shelf.getDescription() != null && !shelf.getDescription().isEmpty()) {
                    textViewShelfDescription.setText(shelf.getDescription());
                    textViewShelfDescription.setVisibility(View.VISIBLE);
                } else {
                    textViewShelfDescription.setVisibility(View.GONE);
                }
                
                // Debug logging for book IDs in the shelf
                List<String> bookIdsInShelf = shelf.getBookIds();
                android.util.Log.d(TAG, "Shelf " + shelf.getName() + " (ID: " + shelf.getId() + ") has " + bookIdsInShelf.size() + " book IDs");
                for (String bookId : bookIdsInShelf) {
                    android.util.Log.d(TAG, "Book ID in shelf: " + bookId);
                }
                
                setupRecyclerView();
                
                // Load books from Supabase
                loadBooks();
            }
            
            @Override
            public void onError(String error) {
                progressBarLoading.setVisibility(View.GONE);
                Snackbar.make(findViewById(android.R.id.content), "Error loading shelf: " + error, Snackbar.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupRecyclerView() {
        bookAdapter = new ShelfBookAdapter(this, books, this);
        recyclerViewBooks.setAdapter(bookAdapter);
        recyclerViewBooks.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void loadBooks() {
        // Skip if loading is already in progress
        if (isLoadingBooks) {
            Log.d(TAG, "Book loading already in progress, skipping duplicate call");
            return;
        }
        
        isLoadingBooks = true;
        books.clear();
        recyclerViewBooks.setVisibility(View.GONE);
        textViewNoBooks.setVisibility(View.GONE);
        progressBarLoading.setVisibility(View.VISIBLE);

        List<String> bookIds = shelf.getBookIds();
        
        // Debug logging
        android.util.Log.d("ShelfDetailActivity", "Loading books for shelf: " + shelf.getId() 
            + ", name: " + shelf.getName() 
            + ", book IDs count: " + bookIds.size());
        
        // Check for duplicate IDs in the bookIds list
        List<String> uniqueBookIds = new ArrayList<>(new HashSet<>(bookIds));
        if (uniqueBookIds.size() < bookIds.size()) {
            android.util.Log.d("ShelfDetailActivity", "Found " + (bookIds.size() - uniqueBookIds.size()) + " duplicate book IDs in shelf");
            bookIds = uniqueBookIds;
        }
        
        if (bookIds.isEmpty()) {
            progressBarLoading.setVisibility(View.GONE); // Hide loading if no book IDs
            showNoBooks();
            isLoadingBooks = false;
            initialLoadComplete = true;
            return;
        }

        // Get book IDs for this shelf from Supabase
        supabaseService.getBookIdsForShelf(shelf.getId(), new SupabaseService.BooksIdsCallback() {
            @Override
            public void onSuccess(List<String> bookIds) {
                if (bookIds.isEmpty()) {
                    progressBarLoading.setVisibility(View.GONE);
                    showNoBooks();
                    isLoadingBooks = false;
                    initialLoadComplete = true;
                    return;
                }
                
                // Load each book
                loadBooksFromIds(bookIds);
            }
            
            @Override
            public void onError(String error) {
                progressBarLoading.setVisibility(View.GONE);
                Snackbar.make(findViewById(android.R.id.content), "Error loading books: " + error, Snackbar.LENGTH_SHORT).show();
                isLoadingBooks = false;
                initialLoadComplete = true;
            }
        });
    }
    
    private void loadBooksFromIds(List<String> bookIds) {
        final List<Book> loadedBooks = new ArrayList<>();
        final int[] booksProcessed = {0};
        
        for (String bookId : bookIds) {
            supabaseService.getBookById(bookId, new SupabaseService.BookCallback() {
                @Override
                public void onSuccess(Book book) {
                    booksProcessed[0]++;
                    
                    // Фильтрация: пропускаем аудиокниги
                    String fileFormat = book.getFileFormat();
                    if (fileFormat == null || 
                        !(fileFormat.equalsIgnoreCase("MP3") || 
                          fileFormat.equalsIgnoreCase("AAC") || 
                          fileFormat.equalsIgnoreCase("OGG") || 
                          fileFormat.equalsIgnoreCase("M4A") || 
                          fileFormat.equalsIgnoreCase("M4B") || 
                          fileFormat.equalsIgnoreCase("WAV") || 
                          fileFormat.equalsIgnoreCase("FLAC") || 
                          fileFormat.equalsIgnoreCase("AUDIOBOOK"))) {
                        loadedBooks.add(book);
                        android.util.Log.d("ShelfDetailActivity", "Loaded book: " + book.getTitle());
                    } else {
                        android.util.Log.d("ShelfDetailActivity", "Skipping audiobook: " + book.getTitle());
                    }
                    
                    // Check if all books have been processed
                    if (booksProcessed[0] >= bookIds.size()) {
                        runOnUiThread(() -> {
                            progressBarLoading.setVisibility(View.GONE);
                            
                            if (loadedBooks.isEmpty()) {
                                showNoBooks();
                                android.util.Log.d("ShelfDetailActivity", "Showing no books UI");
                            } else {
                                books.addAll(loadedBooks);
                                bookAdapter.notifyDataSetChanged();
                                showBooks();
                                android.util.Log.d("ShelfDetailActivity", "Showing books UI with " + books.size() + " books");
                            }
                            
                            isLoadingBooks = false;
                            initialLoadComplete = true;
                        });
                    }
                }
                
                @Override
                public void onError(String error) {
                    booksProcessed[0]++;
                    android.util.Log.d("ShelfDetailActivity", "Failed to load book with ID: " + bookId + " from Supabase: " + error);
                    
                    // Check if all books have been processed
                    if (booksProcessed[0] >= bookIds.size()) {
                        runOnUiThread(() -> {
                            progressBarLoading.setVisibility(View.GONE);
                            
                            if (loadedBooks.isEmpty()) {
                                showNoBooks();
                                android.util.Log.d("ShelfDetailActivity", "Showing no books UI");
                            } else {
                                books.addAll(loadedBooks);
                                bookAdapter.notifyDataSetChanged();
                                showBooks();
                                android.util.Log.d("ShelfDetailActivity", "Showing books UI with " + books.size() + " books");
                            }
                            
                            isLoadingBooks = false;
                            initialLoadComplete = true;
                        });
                    }
                }
            });
        }
    }
    
    private void showNoBooks() {
        textViewNoBooks.setVisibility(View.VISIBLE);
        recyclerViewBooks.setVisibility(View.GONE);
    }
    
    private void showBooks() {
        textViewNoBooks.setVisibility(View.GONE);
        recyclerViewBooks.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload books when returning to this activity, but only if initial load is complete
        // and we're not currently loading books
        if (shelf != null && initialLoadComplete && !isLoadingBooks) {
            android.util.Log.d(TAG, "onResume: Reloading books after returning to activity");
            loadBooks();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBookClick(Book book) {
        // Navigate to book details
        Intent intent = new Intent(this, BookActivity.class);
        intent.putExtra("id", book.getId());
        intent.putExtra("title", book.getTitle());
        intent.putExtra("author", book.getAuthor());
        intent.putExtra("description", book.getDescription());
        intent.putExtra("coverPath", book.getCoverPath());
        intent.putExtra("status", book.getStatus());
        intent.putExtra("currentPage", book.getCurrentPage());
        intent.putExtra("totalPages", book.getTotalPages());
        startActivity(intent);
    }

    @Override
    public void onBookMenuClick(Book book, View anchorView) {
        // Show a popup menu for the book with option to remove from shelf
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Удалить \"" + book.getTitle() + "\" с полки?");
        builder.setMessage("Книга будет удалена с этой полки, но останется в вашей библиотеке.");
        builder.setPositiveButton("Удалить", (dialog, which) -> {
            showRemoveBookDialog(book);
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showRemoveBookDialog(Book book) {
        progressBarLoading.setVisibility(View.VISIBLE);
        
        supabaseService.removeBookFromShelf(book.getId(), shelf.getId(), new SupabaseService.BookShelfCallback() {
            @Override
            public void onSuccess() {
                progressBarLoading.setVisibility(View.GONE);
                
                // Update shelf's book IDs 
                shelf.getBookIds().remove(book.getId());
                
                // Remove book from the list and update UI
                for (int i = 0; i < books.size(); i++) {
                    if (books.get(i).getId().equals(book.getId())) {
                        books.remove(i);
                        bookAdapter.notifyItemRemoved(i);
                        break;
                    }
                }
                
                if (books.isEmpty()) {
                    showNoBooks();
                }
                
                Snackbar.make(findViewById(android.R.id.content), 
                    "Книга \"" + book.getTitle() + "\" удалена с полки \"" + shelf.getName() + "\"", 
                    Snackbar.LENGTH_SHORT).show();
            }
            
            @Override
            public void onError(String error) {
                progressBarLoading.setVisibility(View.GONE);
                Snackbar.make(findViewById(android.R.id.content), 
                    "Ошибка при удалении книги: " + error, 
                    Snackbar.LENGTH_SHORT).show();
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
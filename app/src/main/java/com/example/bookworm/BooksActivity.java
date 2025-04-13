package com.example.bookworm;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BooksActivity extends AppCompatActivity {
    private SupabaseAuth supabaseAuth;
    private RecyclerView booksRecyclerView;
    private EditText searchEditText;
    private Button searchButton;
    private BookAdapter bookAdapter;
    private List<Book> books;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books);

        supabaseAuth = new SupabaseAuth(this);

        // Check if user is logged in
        if (!supabaseAuth.isLoggedIn()) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        booksRecyclerView = findViewById(R.id.booksRecyclerView);
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);

        // Initialize books list and adapter
        books = new ArrayList<>();
        bookAdapter = new BookAdapter(books, new BookAdapter.OnBookClickListener() {
            @Override
            public void onBookClick(Book book) {
                Toast.makeText(BooksActivity.this, "Selected: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFavoriteClick(Book book) {
                // Empty implementation
            }
        });
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        booksRecyclerView.setAdapter(bookAdapter);

        // Load initial books
        loadBooks();

        searchButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString().trim();
            if (!query.isEmpty()) {
                searchBooks(query);
            } else {
                loadBooks();
            }
        });
    }

    private void loadBooks() {
        // TODO: Implement loading books from Supabase
        Toast.makeText(this, "Загрузка книг...", Toast.LENGTH_SHORT).show();
    }

    private void searchBooks(String query) {
        // TODO: Implement book search
        Toast.makeText(this, "Поиск книг: " + query, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
} 
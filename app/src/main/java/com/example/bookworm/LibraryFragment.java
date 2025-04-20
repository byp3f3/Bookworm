package com.example.bookworm;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.services.SupabaseService;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibraryFragment extends Fragment {
    private RecyclerView booksRecyclerView;
    private BookAdapter bookAdapter;
    private SupabaseService supabaseService;
    private List<Book> allBooks = new ArrayList<>();
    private EditText searchEditText;
    private String currentSortCriteria = "title";
    private boolean isAscending = true;
    private String selectedStatus = null;

    // Sort buttons
    private Button sortTitleBtn;
    private Button sortAuthorBtn;
    private Button sortStatusBtn;
    private Button sortRatingBtn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library_enhanced, container, false);

        booksRecyclerView = view.findViewById(R.id.books_recycler_view);
        searchEditText = view.findViewById(R.id.search_edit_text);
        sortTitleBtn = view.findViewById(R.id.sort_title_btn);
        sortAuthorBtn = view.findViewById(R.id.sort_author_btn);
        sortStatusBtn = view.findViewById(R.id.sort_status_btn);
        sortRatingBtn = view.findViewById(R.id.sort_rating_btn);

        setupRecyclerView();
        setupSearch();
        setupSortButtons();

        supabaseService = new SupabaseService(requireContext());
        loadBooks();

        return view;
    }

    private void setupSortButtons() {
        sortTitleBtn.setOnClickListener(v -> {
            if (currentSortCriteria.equals("title")) {
                isAscending = !isAscending;
            } else {
                currentSortCriteria = "title";
                isAscending = true;
            }
            selectedStatus = null;
            updateSortButtonStates();
            sortBooks();
        });

        sortAuthorBtn.setOnClickListener(v -> {
            if (currentSortCriteria.equals("author")) {
                isAscending = !isAscending;
            } else {
                currentSortCriteria = "author";
                isAscending = true;
            }
            selectedStatus = null;
            updateSortButtonStates();
            sortBooks();
        });

        sortStatusBtn.setOnClickListener(v -> {
            String[] statuses = {"Читаю", "Прочитано", "В планах"};
            new AlertDialog.Builder(requireContext())
                .setTitle("Выберите статус")
                .setItems(statuses, (dialog, which) -> {
                    selectedStatus = statuses[which];
                    currentSortCriteria = "status";
                    updateSortButtonStates();
                    sortBooks();
                })
                .setNegativeButton("Показать все", (dialog, which) -> {
                    selectedStatus = null;
                    currentSortCriteria = "title";
                    updateSortButtonStates();
                    sortBooks();
                })
                .show();
        });

        sortRatingBtn.setOnClickListener(v -> {
            if (currentSortCriteria.equals("rating")) {
                isAscending = !isAscending;
            } else {
                currentSortCriteria = "rating";
                isAscending = true;
            }
            selectedStatus = null;
            updateSortButtonStates();
            sortBooks();
        });

        updateSortButtonStates();
    }

    private void updateSortButtonStates() {
        // Reset all buttons
        sortTitleBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        sortAuthorBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        sortStatusBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        sortRatingBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        // Set active button with indicator
        int sortIndicator = isAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc;

        switch (currentSortCriteria) {
            case "title":
                sortTitleBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, sortIndicator, 0);
                break;
            case "author":
                sortAuthorBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, sortIndicator, 0);
                break;
            case "status":
                sortStatusBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_filter, 0);
                break;
            case "rating":
                sortRatingBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, sortIndicator, 0);
                break;
        }
    }

    private void setupRecyclerView() {
        int spanCount = 3; // 3 columns in grid
        booksRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        bookAdapter = new BookAdapter(new ArrayList<>());
        booksRecyclerView.setAdapter(bookAdapter);
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBooks(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void sortBooks() {
        if (allBooks == null || allBooks.isEmpty()) return;

        List<Book> sortedBooks = new ArrayList<>(allBooks);

        // First filter by status if selected
        if (selectedStatus != null) {
            sortedBooks.removeIf(book -> !selectedStatus.equals(book.getStatus()));
        }

        // Then sort by the selected criteria
        switch (currentSortCriteria) {
            case "title":
                Collections.sort(sortedBooks, (b1, b2) -> {
                    int compare = b1.getTitle().compareToIgnoreCase(b2.getTitle());
                    return isAscending ? compare : -compare;
                });
                break;
            case "author":
                Collections.sort(sortedBooks, (b1, b2) -> {
                    int compare = b1.getAuthor().compareToIgnoreCase(b2.getAuthor());
                    return isAscending ? compare : -compare;
                });
                break;
            case "rating":
                Collections.sort(sortedBooks, (b1, b2) -> {
                    // Handle rating comparison properly
                    int rating1 = b1.getRating();
                    int rating2 = b2.getRating();
                    
                    // For ascending: lower ratings first (including 0)
                    // For descending: higher ratings first
                    if (isAscending) {
                        return Integer.compare(rating1, rating2);
                    } else {
                        return Integer.compare(rating2, rating1);
                    }
                });
                break;
        }

        bookAdapter.setBooks(sortedBooks);
    }

    private void loadBooks() {
        supabaseService.getAllUserBooks(new SupabaseService.BooksLoadCallback() {
            @Override
            public void onSuccess(List<Book> books) {
                allBooks = books;
                sortBooks();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(getContext(), "Error loading books: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterBooks(String query) {
        if (allBooks == null) return;

        List<Book> filteredBooks = new ArrayList<>();
        String lowerCaseQuery = query.toLowerCase();

        for (Book book : allBooks) {
            if (book.getTitle().toLowerCase().contains(lowerCaseQuery) ||
                    (book.getAuthor() != null && book.getAuthor().toLowerCase().contains(lowerCaseQuery))) {
                filteredBooks.add(book);
            }
        }

        bookAdapter.setBooks(filteredBooks);

        if (filteredBooks.isEmpty() && !query.isEmpty()) {
            Toast.makeText(getContext(), "Книги не найдены", Toast.LENGTH_SHORT).show();
        }
    }

    private static class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
        private List<Book> books;

        public BookAdapter(List<Book> books) {
            this.books = books;
        }

        public void setBooks(List<Book> books) {
            this.books = books;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book, parent, false);
            return new BookViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
            holder.bind(books.get(position));
        }

        @Override
        public int getItemCount() {
            return books.size();
        }

        static class BookViewHolder extends RecyclerView.ViewHolder {
            private final ImageView coverImageView;
            private final TextView titleTextView;
            private final View statusIndicator;
            private final FrameLayout statusBadge;
            private final BookAdapter adapter;

            public BookViewHolder(@NonNull View itemView, BookAdapter adapter) {
                super(itemView);
                this.adapter = adapter;
                coverImageView = itemView.findViewById(R.id.book_cover);
                titleTextView = itemView.findViewById(R.id.book_title);
                statusIndicator = itemView.findViewById(R.id.status_indicator);
                statusBadge = itemView.findViewById(R.id.status_badge);

                // Add click listener to the entire item view
                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        Book book = adapter.books.get(position);
                        Intent intent = new Intent(v.getContext(), BookActivity.class);
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
                        
                        v.getContext().startActivity(intent);
                    }
                });
            }

            public void bind(Book book) {
                // Load cover image
                if (book.getCoverPath() != null && !book.getCoverPath().isEmpty()) {
                    Picasso.get()
                            .load(book.getCoverPath())
                            .fit()
                            .centerInside()
                            .placeholder(R.drawable.ic_book_placeholder)
                            .error(R.drawable.ic_book_placeholder)
                            .into(coverImageView, new Callback() {
                                @Override
                                public void onSuccess() {
                                    statusBadge.setVisibility(View.VISIBLE);
                                }

                                @Override
                                public void onError(Exception e) {
                                    statusBadge.setVisibility(View.VISIBLE);
                                }
                            });
                } else {
                    coverImageView.setImageResource(R.drawable.ic_book_placeholder);
                    statusBadge.setVisibility(View.VISIBLE);
                }

                // Set book title
                titleTextView.setText(book.getTitle());

                // Set status indicator color based on actual status
                int statusColor = R.drawable.status_planned;
                String status = book.getStatus();
                
                // Handle null or empty status
                if (status == null) {
                    // Default to "В планах" if status is null
                    status = "В планах";
                    // Update book object with default status
                    book.setStatus(status);
                }
                
                switch (status) {
                    case "Читаю":
                        statusColor = R.drawable.status_reading;
                        break;
                    case "Прочитано":
                        statusColor = R.drawable.status_finished;
                        break;
                    // For "В планах" and any other status, use default status_planned
                }
                statusIndicator.setBackgroundResource(statusColor);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (supabaseService != null) {
            supabaseService.shutdown();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null && data.getBooleanExtra("refresh", false)) {
            loadBooks();
        }
    }
}
package com.example.bookworm;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private LinearLayout currentlyReadingContainer;
    private LinearLayout emptyCurrentlyReadingContainer;
    private LinearLayout currentlyReadingList;
    private Button showAllButton;
    private Button addBookEmptyButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        currentlyReadingContainer = view.findViewById(R.id.currentlyReadingContainer);
        emptyCurrentlyReadingContainer = view.findViewById(R.id.emptyCurrentlyReadingContainer);
        currentlyReadingList = view.findViewById(R.id.currentlyReadingList);
        showAllButton = view.findViewById(R.id.showAllButton);
        addBookEmptyButton = view.findViewById(R.id.addBookEmptyButton);

        List<Book> currentlyReadingBooks = getCurrentlyReadingBooks();

        if (currentlyReadingBooks.isEmpty()) {
            showEmptyState();
        } else {
            showBooksList(currentlyReadingBooks);
        }

        addBookEmptyButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddBookDialogActivity.class);
            startActivity(intent);
        });

        showAllButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), BookActivity.class);
            startActivity(intent);
        });

        return view;
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

            // TODO: Load book cover using Glide or Picasso
            // bookCover.setImageUrl(book.getCoverUrl());
            
            bookTitle.setText(book.getTitle());
            bookAuthor.setText(book.getAuthor());
            
            int progress = book.getTotalPages() > 0 
                ? (book.getCurrentPage() * 100) / book.getTotalPages() 
                : 0;
            bookProgress.setText(progress + "%");
            progressBar.setProgress(progress);
            
            readingDuration.setText(book.getReadingDays() + " дней");

            CardView bookCard = bookView.findViewById(R.id.bookCard);
            bookCard.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), BookActivity.class);
                intent.putExtra("title", book.getTitle());
                intent.putExtra("author", book.getAuthor());
                intent.putExtra("status", book.getStatus());
                intent.putExtra("currentPage", book.getCurrentPage());
                intent.putExtra("totalPages", book.getTotalPages());
                intent.putExtra("readingDays", book.getReadingDays());
                startActivity(intent);
            });

            currentlyReadingList.addView(bookView);
        }

        // Add "Add book" button at the end
        View addBookView = LayoutInflater.from(getContext()).inflate(R.layout.item_currently_reading_book, currentlyReadingList, false);
        ImageView bookCover = addBookView.findViewById(R.id.bookCover);
        TextView bookTitle = addBookView.findViewById(R.id.bookTitle);
        TextView bookAuthor = addBookView.findViewById(R.id.bookAuthor);
        TextView bookProgress = addBookView.findViewById(R.id.bookProgress);
        TextView readingDuration = addBookView.findViewById(R.id.readingDuration);
        ProgressBar progressBar = addBookView.findViewById(R.id.progressBar);

        bookCover.setImageResource(R.drawable.ic_add_book);
        bookTitle.setText("Добавить книгу");
        bookAuthor.setVisibility(View.GONE);
        bookProgress.setVisibility(View.GONE);
        readingDuration.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        addBookView.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddBookDialogActivity.class);
            startActivity(intent);
        });

        currentlyReadingList.addView(addBookView);

        // Show "Show all" button if more than 5 books
        if (books.size() > 5) {
            showAllButton.setVisibility(View.VISIBLE);
        } else {
            showAllButton.setVisibility(View.GONE);
        }
    }

    private List<Book> getCurrentlyReadingBooks() {
        // TODO: Get books from database
        return new ArrayList<>();
    }
}
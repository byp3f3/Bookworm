package com.example.bookworm;

import android.content.Intent;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.bookworm.services.SupabaseService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {
    private LinearLayout currentlyReadingContainer;
    private LinearLayout emptyCurrentlyReadingContainer;
    private LinearLayout currentlyReadingList;
    private Button showAllButton;
    private Button addBookEmptyButton;
    private ProgressBar loadingProgress;
    private SupabaseService supabaseService;

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
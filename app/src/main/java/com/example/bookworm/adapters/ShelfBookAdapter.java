package com.example.bookworm.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.bookworm.Book;
import com.example.bookworm.BookActivity;
import com.example.bookworm.R;

import java.util.List;

public class ShelfBookAdapter extends RecyclerView.Adapter<ShelfBookAdapter.ShelfBookViewHolder> {
    private final Context context;
    private final List<Book> books;
    private final ShelfBookListener listener;

    public interface ShelfBookListener {
        void onBookClick(Book book);
        void onBookMenuClick(Book book, View anchorView);
    }

    public ShelfBookAdapter(Context context, List<Book> books, ShelfBookListener listener) {
        this.context = context;
        this.books = books;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShelfBookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_book, parent, false);
        return new ShelfBookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfBookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.bind(book);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public class ShelfBookViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewTitle;
        private final TextView textViewAuthor;
        private final ImageView imageViewCover;
        private final ImageView imageViewMenu;
        private final View statusIndicator;
        private final FrameLayout statusBadge;

        public ShelfBookViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.book_title);
            textViewAuthor = itemView.findViewById(R.id.book_author);
            imageViewCover = itemView.findViewById(R.id.book_cover);
            imageViewMenu = itemView.findViewById(R.id.book_menu);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            statusBadge = itemView.findViewById(R.id.status_badge);
        }

        public void bind(Book book) {
            textViewTitle.setText(book.getTitle());
            textViewAuthor.setText(book.getAuthor());
            
            // Set cover image if available
            if (book.getCoverPath() != null && !book.getCoverPath().isEmpty()) {
                Glide.with(context)
                    .load(book.getCoverPath())
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(imageViewCover);
            } else {
                imageViewCover.setImageResource(R.drawable.ic_book_placeholder);
            }
            
            // Set status indicator
            int statusColor = R.drawable.status_planned;
            String status = book.getStatus();
            
            // Handle null or empty status
            if (status == null || status.isEmpty()) {
                status = "В планах";
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
            statusBadge.setVisibility(View.VISIBLE);
            
            // Show menu for shelf books
            imageViewMenu.setVisibility(View.VISIBLE);
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookClick(book);
                } else {
                    // Default behavior if no listener provided
                    Intent intent = new Intent(context, BookActivity.class);
                    intent.putExtra("BOOK_ID", book.getId());
                    context.startActivity(intent);
                }
            });
            
            imageViewMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookMenuClick(book, imageViewMenu);
                }
            });
        }
    }
} 
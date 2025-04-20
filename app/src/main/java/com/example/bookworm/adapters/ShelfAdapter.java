package com.example.bookworm.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.R;
import com.example.bookworm.models.Shelf;

import java.util.List;
import java.util.Locale;

public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder> {
    private final Context context;
    private final List<Shelf> shelves;
    private final ShelfInteractionListener listener;

    public ShelfAdapter(Context context, List<Shelf> shelves, ShelfInteractionListener listener) {
        this.context = context;
        this.shelves = shelves;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shelf, parent, false);
        return new ShelfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        Shelf shelf = shelves.get(position);
        holder.bind(shelf);
    }

    @Override
    public int getItemCount() {
        return shelves.size();
    }

    public class ShelfViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewShelfName;
        private final TextView textViewShelfDescription;
        private final TextView textViewBookCount;
        private final ImageView imageViewMenu;

        public ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewShelfName = itemView.findViewById(R.id.textViewShelfName);
            textViewShelfDescription = itemView.findViewById(R.id.textViewShelfDescription);
            textViewBookCount = itemView.findViewById(R.id.textViewBookCount);
            imageViewMenu = itemView.findViewById(R.id.imageViewMenu);
        }

        public void bind(Shelf shelf) {
            textViewShelfName.setText(shelf.getName());
            
            if (shelf.getDescription() != null && !shelf.getDescription().isEmpty()) {
                textViewShelfDescription.setText(shelf.getDescription());
                textViewShelfDescription.setVisibility(View.VISIBLE);
            } else {
                textViewShelfDescription.setVisibility(View.GONE);
            }
            
            int bookCount = shelf.getBookIds() != null ? shelf.getBookIds().size() : 0;
            android.util.Log.d("ShelfAdapter", "Shelf " + shelf.getName() + " (ID: " + shelf.getId() + ") has " + bookCount + " books");
            
            String bookCountText = formatBookCount(bookCount);
            textViewBookCount.setText(bookCountText);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShelfClick(shelf);
                }
            });
            
            imageViewMenu.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShelfMenuClick(shelf, imageViewMenu);
                }
            });
        }
        
        private String formatBookCount(int count) {
            if (count == 0) {
                return "Нет книг";
            } else if (count == 1) {
                return "1 книга";
            } else if (count >= 2 && count <= 4) {
                return count + " книги";
            } else {
                return count + " книг";
            }
        }
    }

    public interface ShelfInteractionListener {
        void onShelfClick(Shelf shelf);
        void onShelfMenuClick(Shelf shelf, View anchorView);
    }
} 
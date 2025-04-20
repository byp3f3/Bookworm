package com.example.bookworm.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.R;
import com.example.bookworm.models.Shelf;

import java.util.List;

public class ShelfSelectionAdapter extends RecyclerView.Adapter<ShelfSelectionAdapter.ShelfViewHolder> {
    private final Context context;
    private final List<Shelf> shelves;
    private final List<String> selectedShelfIds;
    private final ShelfSelectionListener listener;

    public interface ShelfSelectionListener {
        void onShelfSelectionChanged(Shelf shelf, boolean isSelected);
    }

    public ShelfSelectionAdapter(Context context, List<Shelf> shelves, List<String> selectedShelfIds,
                                 ShelfSelectionListener listener) {
        this.context = context;
        this.shelves = shelves;
        this.selectedShelfIds = selectedShelfIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shelf_selection, parent, false);
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
        private final CheckBox checkBoxSelected;

        public ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewShelfName = itemView.findViewById(R.id.textViewShelfName);
            checkBoxSelected = itemView.findViewById(R.id.checkBoxSelected);
        }

        public void bind(Shelf shelf) {
            textViewShelfName.setText(shelf.getName());
            
            // Check if this shelf is already selected
            boolean isSelected = selectedShelfIds.contains(shelf.getId());
            checkBoxSelected.setChecked(isSelected);
            
            // Set click listeners
            View.OnClickListener clickListener = v -> {
                boolean newState = !checkBoxSelected.isChecked();
                checkBoxSelected.setChecked(newState);
                
                if (listener != null) {
                    listener.onShelfSelectionChanged(shelf, newState);
                }
                
                if (newState) {
                    selectedShelfIds.add(shelf.getId());
                } else {
                    selectedShelfIds.remove(shelf.getId());
                }
            };
            
            itemView.setOnClickListener(clickListener);
            checkBoxSelected.setOnClickListener(v -> {
                boolean isChecked = checkBoxSelected.isChecked();
                
                if (listener != null) {
                    listener.onShelfSelectionChanged(shelf, isChecked);
                }
                
                if (isChecked) {
                    selectedShelfIds.add(shelf.getId());
                } else {
                    selectedShelfIds.remove(shelf.getId());
                }
            });
        }
    }
} 
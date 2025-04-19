package com.example.bookworm.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.R;
import com.example.bookworm.models.TocItem;

import java.util.List;

public class TocAdapter extends RecyclerView.Adapter<TocAdapter.ViewHolder> {

    private List<TocItem> tocItems;
    private Context context;
    private OnTocItemClickListener listener;

    // Интерфейс для обработки нажатий на элементы
    public interface OnTocItemClickListener {
        void onTocItemClick(TocItem item, int position);
    }

    public TocAdapter(Context context, List<TocItem> tocItems) {
        this.context = context;
        this.tocItems = tocItems;
    }

    public void setOnTocItemClickListener(OnTocItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_toc, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TocItem item = tocItems.get(position);
        
        // Установка заголовка
        holder.titleText.setText(item.getTitle());
        
        // Установка номера страницы
        holder.pageText.setText("Стр. " + item.getPageNumber());
        
        // Добавление отступа в зависимости от уровня элемента
        int paddingStart = (item.getLevel() - 1) * 20; // 20dp на каждый уровень вложенности
        holder.itemView.setPadding(paddingStart, holder.itemView.getPaddingTop(), 
                holder.itemView.getPaddingRight(), holder.itemView.getPaddingBottom());
        
        // Обработка нажатия
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTocItemClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tocItems != null ? tocItems.size() : 0;
    }

    // Метод для обновления данных
    public void setTocItems(List<TocItem> tocItems) {
        this.tocItems = tocItems;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView pageText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.tocItemTitle);
            pageText = itemView.findViewById(R.id.tocItemPage);
        }
    }
} 
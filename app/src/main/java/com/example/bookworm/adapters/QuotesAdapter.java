package com.example.bookworm.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.Quote;
import com.example.bookworm.R;

import java.util.List;

public class QuotesAdapter extends RecyclerView.Adapter<QuotesAdapter.QuoteViewHolder> {
    private List<Quote> quotes;
    private OnQuoteClickListener listener;

    public interface OnQuoteClickListener {
        void onQuoteClick(Quote quote);
    }

    public QuotesAdapter(List<Quote> quotes, OnQuoteClickListener listener) {
        this.quotes = quotes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote quote = quotes.get(position);
        holder.bind(quote, listener);
    }

    @Override
    public int getItemCount() {
        return quotes.size();
    }

    static class QuoteViewHolder extends RecyclerView.ViewHolder {
        private TextView quoteText;
        private TextView quotePages;
        private TextView quoteDate;

        public QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            quoteText = itemView.findViewById(R.id.quoteText);
            quotePages = itemView.findViewById(R.id.quotePages);
            quoteDate = itemView.findViewById(R.id.quoteDate);
        }

        public void bind(Quote quote, OnQuoteClickListener listener) {
            quoteText.setText(quote.getText());

            if (quote.getStartPage() == quote.getEndPage()) {
                quotePages.setText("Страница " + quote.getStartPage());
            } else {
                quotePages.setText("Страницы " + quote.getStartPage() + "-" + quote.getEndPage());
            }

            quoteDate.setText(quote.getFormattedDate());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuoteClick(quote);
                }
            });
        }


    }
}
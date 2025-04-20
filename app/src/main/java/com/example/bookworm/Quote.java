package com.example.bookworm;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Quote {
    private String id;
    private String bookId;
    private String userId;
    private String text;
    private int startPage;
    private int endPage;
    private long createdAt;

    public Quote(String id, String bookId, String userId, String text,
                 int startPage, int endPage, long createdAt) {
        this.id = id;
        this.bookId = bookId;
        this.userId = userId;
        this.text = text;
        this.startPage = startPage;
        this.endPage = endPage;
        this.createdAt = createdAt;
    }

    // Геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getStartPage() { return startPage; }
    public void setStartPage(int startPage) { this.startPage = startPage; }

    public int getEndPage() { return endPage; }
    public void setEndPage(int endPage) { this.endPage = endPage; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(createdAt));
    }

    public int getAdjustedPageNumber() {
        return startPage - 1; // Конвертируем 1-based в 0-based
    }
}
package com.example.bookworm;

public class Book {
    private String id;
    private String title;
    private String author;
    private String coverUrl;
    private boolean isFavorite;

    public Book(String id, String title, String author, String coverUrl) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.coverUrl = coverUrl;
        this.isFavorite = false;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
} 
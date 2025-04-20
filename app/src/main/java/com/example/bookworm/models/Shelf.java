package com.example.bookworm.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.bookworm.Book;

public class Shelf {
    private String id;
    private String name;
    private String description;
    private String userId;
    private List<String> bookIds;

    public Shelf() {
        this.id = UUID.randomUUID().toString();
        this.bookIds = new ArrayList<>();
    }

    public Shelf(String name, String description, String userId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.userId = userId;
        this.bookIds = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getBookIds() {
        return bookIds;
    }

    public void setBookIds(List<String> bookIds) {
        this.bookIds = bookIds;
    }

    public void addBookId(String bookId) {
        if (!bookIds.contains(bookId)) {
            bookIds.add(bookId);
        }
    }

    public void removeBookId(String bookId) {
        bookIds.remove(bookId);
    }
} 
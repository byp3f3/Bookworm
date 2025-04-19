package com.example.bookworm.models;

/**
 * Класс представляющий элемент оглавления книги
 */
public class TocItem {
    private String title;       // Заголовок раздела/главы
    private int pageNumber;     // Номер страницы
    private int level;          // Уровень вложенности (1 - глава, 2 - подглава и т.д.)
    private String contentRef;  // Ссылка на содержимое (используется для EPUB)

    public TocItem(String title, int pageNumber) {
        this.title = title;
        this.pageNumber = pageNumber;
        this.level = 1;
    }

    public TocItem(String title, int pageNumber, int level) {
        this.title = title;
        this.pageNumber = pageNumber;
        this.level = level;
    }

    public TocItem(String title, int pageNumber, int level, String contentRef) {
        this.title = title;
        this.pageNumber = pageNumber;
        this.level = level;
        this.contentRef = contentRef;
    }

    public String getTitle() {
        return title;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getLevel() {
        return level;
    }

    public String getContentRef() {
        return contentRef;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setContentRef(String contentRef) {
        this.contentRef = contentRef;
    }

    @Override
    public String toString() {
        return title + " (стр. " + pageNumber + ")";
    }
} 
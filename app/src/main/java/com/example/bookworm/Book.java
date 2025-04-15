package com.example.bookworm;

public class Book {
    private String id;
    private String title;
    private String author;
    private String description;
    private String coverPath;
    private String filePath;
    private String fileFormat;
    private String status;
    private int currentPage;
    private int totalPages;
    private int readingDays;
    private String createdDate;
    private String startDate;
    private String endDate;
    private int rating;
    private String review;

    public Book(String id, String title, String author, String description, String coverPath, 
                String filePath, String status, int currentPage, int totalPages, int readingDays, 
                String startDate, String endDate, int rating, String review, String fileFormat) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.coverPath = coverPath;
        this.filePath = filePath;
        this.status = status;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.readingDays = readingDays;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rating = rating;
        this.review = review;
        this.fileFormat = fileFormat;
    }

    public Book() {

    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getCoverPath() { return coverPath; }
    public String getFilePath() { return filePath; }
    public String getStatus() { return status; }
    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() { return totalPages; }
    public int getReadingDays() { return readingDays; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public int getRating() { return rating; }
    public String getReview() { return review; }

    public String getFileFormat() {
        return fileFormat;
    }

    public String getCreatedDate() {
        return createdDate;
    }


    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverPath(String coverPath) { this.coverPath = coverPath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setStatus(String status) { this.status = status; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public void setReadingDays(int readingDays) { this.readingDays = readingDays; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public void setRating(int rating) { this.rating = rating; }
    public void setReview(String review) { this.review = review; }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public int getReadingProgress() {
        if (totalPages <= 0) return 0;
        return (int) ((currentPage * 100.0) / totalPages);
    }
}
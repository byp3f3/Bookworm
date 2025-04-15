package com.example.bookworm;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "bookworm.db";
    private static final int DATABASE_VERSION = 1;

    // Table name
    public static final String TABLE_BOOKS = "books";

    // Column names
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_AUTHOR = "author";
    public static final String COLUMN_GENRE = "genre";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_CURRENT_PAGE = "current_page";
    public static final String COLUMN_TOTAL_PAGES = "total_pages";
    public static final String COLUMN_START_DATE = "start_date";
    public static final String COLUMN_END_DATE = "end_date";
    public static final String COLUMN_RATING = "rating";
    public static final String COLUMN_REVIEW = "review";
    public static final String COLUMN_COVER_PATH = "cover_path";

    // Create table SQL query
    private static final String CREATE_TABLE_BOOKS = "CREATE TABLE " + TABLE_BOOKS + "("
            + COLUMN_ID + " TEXT PRIMARY KEY,"
            + COLUMN_TITLE + " TEXT,"
            + COLUMN_AUTHOR + " TEXT,"
            + COLUMN_GENRE + " TEXT,"
            + COLUMN_STATUS + " TEXT,"
            + COLUMN_CURRENT_PAGE + " INTEGER,"
            + COLUMN_TOTAL_PAGES + " INTEGER,"
            + COLUMN_START_DATE + " TEXT,"
            + COLUMN_END_DATE + " TEXT,"
            + COLUMN_RATING + " INTEGER,"
            + COLUMN_REVIEW + " TEXT,"
            + COLUMN_COVER_PATH + " TEXT"
            + ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_BOOKS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKS);
        onCreate(db);
    }

    public void updateBookProgress(String bookId, int currentPage) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CURRENT_PAGE, currentPage);
        
        db.update(TABLE_BOOKS, values, COLUMN_ID + " = ?", new String[] { bookId });
        db.close();
    }
    
    public void updateBookRating(String bookId, int rating) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("rating", rating);
        
        db.update(TABLE_BOOKS, values, COLUMN_ID + " = ?", new String[] { bookId });
        db.close();
    }
    
    public void updateBookReview(String bookId, String review) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("review", review);
        
        db.update(TABLE_BOOKS, values, COLUMN_ID + " = ?", new String[] { bookId });
        db.close();
    }

    public void updateBook(String bookId, String title, String author, String genre, String status,
                          int currentPage, int totalPages, String startDate, String endDate, String coverPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_AUTHOR, author);
        values.put(COLUMN_GENRE, genre);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_CURRENT_PAGE, currentPage);
        values.put(COLUMN_TOTAL_PAGES, totalPages);
        values.put(COLUMN_START_DATE, startDate);
        values.put(COLUMN_END_DATE, endDate);
        values.put(COLUMN_COVER_PATH, coverPath);
        db.update(TABLE_BOOKS, values, COLUMN_ID + " = ?", new String[]{bookId});
    }
    
    public String getBookReview(String bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String review = null;
        
        android.database.Cursor cursor = db.query(
            TABLE_BOOKS,
            new String[] { COLUMN_REVIEW },
            COLUMN_ID + " = ?",
            new String[] { bookId },
            null, null, null
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COLUMN_REVIEW);
            if (columnIndex != -1) {
                review = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        
        return review;
    }
} 
package com.example.bookworm;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bookworm.services.BookFileReader;

public class BookReaderActivity extends AppCompatActivity {
    private TextView contentTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        contentTextView = findViewById(R.id.bookContentTextView);
        Uri fileUri = getIntent().getParcelableExtra("fileUri");

        if (fileUri != null) {
            BookFileReader.readBookContent(this, fileUri, new BookFileReader.BookContentCallback() {
                @Override
                public void onContentReady(String content) {
                    contentTextView.setText(content);
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(BookReaderActivity.this, error, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        } else {
            Toast.makeText(this, "Ошибка: файл не найден", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
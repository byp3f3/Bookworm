package com.example.bookworm;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bookworm.services.BookMetadataReader;

import java.util.Map;

public class FilePickerActivity extends AppCompatActivity {
    private static final String TAG = "FilePickerActivity";
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> storagePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        // Инициализация ланчера для запроса разрешений
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openFilePicker();
                } else {
                    Toast.makeText(this, "Для работы с файлами необходимо разрешение", Toast.LENGTH_LONG).show();
                }
            }
        );

        // Инициализация ланчера для запроса разрешения на доступ ко всем файлам (Android 11+)
        storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        openFilePicker();
                    } else {
                        Toast.makeText(this, "Для работы с файлами необходимо разрешение", Toast.LENGTH_LONG).show();
                    }
                }
            }
        );

        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "File picker result received");
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Log.d(TAG, "Selected file URI: " + uri.toString());
                        String mimeType = getContentResolver().getType(uri);
                        Log.d(TAG, "File MIME type: " + mimeType);
                        
                        // Проверяем расширение файла, если MIME тип не определен
                        String fileName = uri.getLastPathSegment();
                        if (mimeType == null && fileName != null) {
                            if (fileName.toLowerCase().endsWith(".pdf")) {
                                mimeType = "application/pdf";
                            } else if (fileName.toLowerCase().endsWith(".fb2")) {
                                mimeType = "application/x-fictionbook+xml";
                            } else if (fileName.toLowerCase().endsWith(".epub")) {
                                mimeType = "application/epub+zip";
                            } else if (fileName.toLowerCase().endsWith(".txt")) {
                                mimeType = "text/plain";
                            }
                            Log.d(TAG, "Inferred MIME type from extension: " + mimeType);
                        }

                        if (isSupportedFileType(mimeType, fileName)) {
                            // Читаем метаданные из файла
                            readMetadataAndProceed(uri);
                        } else {
                            String message = "Неподдерживаемый формат файла: " + (mimeType != null ? mimeType : "неизвестный");
                            Log.w(TAG, message);
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e(TAG, "Selected URI is null");
                        Toast.makeText(this, "Ошибка при выборе файла", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "File picker cancelled or failed");
                }
            }
        );

        Button selectFileButton = findViewById(R.id.selectFileButton);
        selectFileButton.setOnClickListener(v -> checkPermissionsAndOpenFilePicker());
    }

    private void checkPermissionsAndOpenFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                storagePermissionLauncher.launch(intent);
            } else {
                openFilePicker();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 (API 23-29)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                openFilePicker();
            }
        } else {
            // Android 5 и ниже
            openFilePicker();
        }
    }

    private void readMetadataAndProceed(Uri fileUri) {
        // Get the real filename from ContentResolver
        String fileName = null;
        try (Cursor cursor = getContentResolver().query(fileUri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting filename", e);
        }

        // If we couldn't get filename from ContentResolver, fall back to URI
        if (fileName == null) {
            fileName = fileUri.getLastPathSegment();
            // Clean up URI-encoded names
            if (fileName != null) {
                fileName = fileName.replaceAll(".*/", ""); // Remove path
                fileName = fileName.replace("%20", " "); // Replace URL encoding
            }
        }

        final String finalFileName = fileName != null ? fileName : "Unknown";

        // Показываем индикатор загрузки
        Toast.makeText(this, "Чтение метаданных...", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Starting metadata reading for URI: " + fileUri.toString());

        BookMetadataReader.readMetadata(this, fileUri, new BookMetadataReader.MetadataCallback() {
            @Override
            public void onMetadataReady(Map<String, String> metadata) {
                Log.d(TAG, "Metadata ready: " + metadata.toString());

                // Ensure we have at least the filename as title
                if (!metadata.containsKey("title")) {
                    metadata.put("title", getFileNameWithoutExtension(finalFileName));
                }

                // Запускаем AddBookActivity с метаданными
                Intent intent = new Intent(FilePickerActivity.this, AddBookActivity.class);
                intent.putExtra("fileUri", fileUri.toString());

                // Передаем метаданные
                intent.putExtra("title", metadata.get("title"));
                if (metadata.containsKey("author")) {
                    intent.putExtra("author", metadata.get("author"));
                }
                if (metadata.containsKey("description")) {
                    intent.putExtra("description", metadata.get("description"));
                }
                if (metadata.containsKey("pages")) {
                    intent.putExtra("pages", metadata.get("pages"));
                }

                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error reading metadata: " + error);

                // Use filename as fallback title
                Intent intent = new Intent(FilePickerActivity.this, AddBookActivity.class);
                intent.putExtra("fileUri", fileUri.toString());
                intent.putExtra("title", getFileNameWithoutExtension(finalFileName));
                startActivity(intent);
                finish();
            }
        });
    }

    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null) return "Unknown";
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private boolean isSupportedFileType(String mimeType, String fileName) {
        if (mimeType != null) {
            return mimeType.equals("application/pdf") ||
                    mimeType.equals("application/x-fictionbook+xml") ||
                    mimeType.equals("text/x-fictionbook+xml") ||
                    mimeType.equals("application/fb2") ||
                    mimeType.equals("application/epub+zip") ||
                    mimeType.equals("text/plain");
        }

        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            return lowerFileName.endsWith(".pdf") ||
                    lowerFileName.endsWith(".fb2") ||
                    lowerFileName.endsWith(".epub") ||
                    lowerFileName.endsWith(".txt");
        }

        return false;
    }

    private void openFilePicker() {
        Log.d(TAG, "Opening file picker");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/x-fictionbook+xml", // Primary FB2 MIME
                "text/x-fictionbook+xml",        // Alternative FB2 MIME
                "application/fb2",               // Another alternative
                "application/epub+zip",
                "text/plain"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        filePickerLauncher.launch(intent);
    }
}
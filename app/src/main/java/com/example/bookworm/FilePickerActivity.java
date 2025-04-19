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
                            Log.d(TAG, "MIME type is null, checking extension of file: " + fileName);
                            
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
                            // Более детальное сообщение об ошибке
                            StringBuilder message = new StringBuilder("Неподдерживаемый формат файла");
                            
                            if (mimeType != null) {
                                message.append(": ").append(mimeType);
                            }
                            
                            if (fileName != null) {
                                message.append("\nИмя файла: ").append(fileName);
                            }
                            
                            message.append("\n\nПоддерживаются только FB2 и EPUB файлы");
                            
                            Log.w(TAG, message.toString());
                            Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
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
            return mimeType.equals("application/x-fictionbook+xml") ||
                   mimeType.equals("text/x-fictionbook+xml") ||
                   mimeType.equals("application/fb2") ||
                   mimeType.equals("application/xml") ||
                   mimeType.equals("text/xml") ||
                   mimeType.equals("text/plain") ||
                   mimeType.equals("application/octet-stream") ||
                   mimeType.equals("application/epub+zip");
        }

        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            Log.d(TAG, "Checking file extension: " + lowerFileName);
            return lowerFileName.endsWith(".fb2") ||
                   lowerFileName.endsWith(".fb2.zip") ||
                   lowerFileName.endsWith(".epub");
        }

        return false;
    }

    private void openFilePicker() {
        Log.d(TAG, "Opening file picker");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Use a more generic MIME type first to show all documents
        intent.setType("*/*");
        
        // Add all possible FB2 and EPUB MIME types
        String[] mimeTypes = {
                "application/x-fictionbook+xml",  // Primary FB2 MIME
                "text/x-fictionbook+xml",         // Alternative FB2 MIME
                "application/fb2",                // Another alternative
                "application/octet-stream",       // Generic binary format
                "text/plain",                     // Try text/plain as some systems use this for FB2
                "application/xml",                // FB2 is XML-based
                "text/xml",                       // Another XML option
                "application/epub+zip"            // EPUB format
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        // Add all possible flags
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        
        filePickerLauncher.launch(intent);
    }
}
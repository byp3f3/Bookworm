package com.example.bookworm;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.regex.Pattern;

public class PersonalDataActivity extends BaseActivity {
    private static final String TAG = "PersonalDataActivity";
    private EditText usernameEditText;
    private EditText oldPasswordEditText;
    private EditText newPasswordEditText;
    private EditText confirmPasswordEditText;
    private Button saveUsernameButton;
    private Button savePasswordButton;
    private ProgressDialog progressDialog;
    private SupabaseAuth supabaseAuth;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_data);
        Log.d(TAG, "PersonalDataActivity создана");

        try {
            supabaseAuth = new SupabaseAuth(getApplicationContext());
            
            // Явно обновляем токен из хранилища
            String accessToken = supabaseAuth.refreshTokenFromStorage();
            Log.d(TAG, "Принудительное обновление токена: " + (accessToken != null ? "токен найден" : "токен отсутствует"));
            
            // Проверяем наличие токена
            accessToken = supabaseAuth.getAccessToken();
            Log.d(TAG, "Проверка токена при создании активности: " + (accessToken != null ? "токен найден" : "токен отсутствует"));
            
            if (accessToken == null) {
                Log.e(TAG, "Токен доступа отсутствует, перенаправление на экран входа");
                Toast.makeText(this, "Необходимо войти в систему", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return;
            }

            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);

            // Initialize views
            usernameEditText = findViewById(R.id.usernameEditText);
            oldPasswordEditText = findViewById(R.id.oldPasswordEditText);
            newPasswordEditText = findViewById(R.id.newPasswordEditText);
            confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
            saveUsernameButton = findViewById(R.id.saveUsernameButton);
            savePasswordButton = findViewById(R.id.savePasswordButton);

            // Set current username
            String currentUsername = supabaseAuth.getCurrentUsername();
            if (currentUsername != null) {
                usernameEditText.setText(currentUsername);
            }

            setupClickListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing PersonalDataActivity: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка инициализации. Пожалуйста, попробуйте снова", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupClickListeners() {
        saveUsernameButton.setOnClickListener(v -> updateUsername());
        savePasswordButton.setOnClickListener(v -> updatePassword());
    }

    private void updateUsername() {
        try {
            String newUsername = usernameEditText.getText().toString().trim();
            Log.d(TAG, "Save username button clicked");
            Log.d(TAG, "New username: " + newUsername);

            if (newUsername.isEmpty()) {
                usernameEditText.setError("Введите имя пользователя");
                return;
            }

            // Проверяем наличие токена
            String accessToken = supabaseAuth.getAccessToken();
            if (accessToken == null) {
                Log.e(TAG, "Попытка обновления имени пользователя без токена");
                Toast.makeText(this, "Сессия истекла. Пожалуйста, войдите снова", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return;
            }

            Log.d(TAG, "Calling updateUsername");
            supabaseAuth.updateUsername(newUsername, new SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess(String accessToken) {
                    Log.d(TAG, "Username updated successfully");
                    runOnUiThread(() -> {
                        Toast.makeText(PersonalDataActivity.this, "Имя пользователя успешно обновлено", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Username update error: " + errorMessage);
                    runOnUiThread(() -> {
                        if (errorMessage.equals("username")) {
                            usernameEditText.setError("Это имя пользователя уже занято");
                        } else {
                            Toast.makeText(PersonalDataActivity.this, "Ошибка: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating username: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка обновления имени пользователя", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePassword() {
        try {
            String oldPassword = oldPasswordEditText.getText().toString();
            String newPassword = newPasswordEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            if (oldPassword.isEmpty()) {
                Log.d(TAG, "Old password is empty");
                oldPasswordEditText.setError("Введите старый пароль");
                return;
            }

            if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
                Log.d(TAG, "Password pattern validation failed");
                newPasswordEditText.setError("Пароль должен быть не менее 8 символов и содержать хотя бы одну заглавную букву, одну строчную букву и одну цифру");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Log.d(TAG, "Passwords do not match");
                confirmPasswordEditText.setError("Пароли не совпадают");
                return;
            }

            progressDialog.setMessage("Обновление пароля...");
            progressDialog.show();

            Log.d(TAG, "Calling updatePassword");
            supabaseAuth.updatePassword(oldPassword, newPassword, new SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess(String accessToken) {
                    Log.d(TAG, "Password update successful");
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(PersonalDataActivity.this, "Пароль успешно изменен", Toast.LENGTH_SHORT).show();
                        oldPasswordEditText.setText("");
                        newPasswordEditText.setText("");
                        confirmPasswordEditText.setText("");
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Password update error: " + errorMessage);
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        if (errorMessage.contains("old password")) {
                            oldPasswordEditText.setError("Неверный старый пароль");
                        } else {
                            Toast.makeText(PersonalDataActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating password: " + e.getMessage(), e);
            Toast.makeText(this, "Ошибка обновления пароля", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
}
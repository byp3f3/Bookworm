package com.example.bookworm;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

public class PersonalDataActivity extends BaseActivity {
    private EditText usernameEditText;
    private EditText oldPasswordEditText;
    private EditText newPasswordEditText;
    private EditText confirmPasswordEditText;
    private Button saveUsernameButton;
    private Button savePasswordButton;
    private ProgressDialog progressDialog;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");
    private static final String TAG = "PersonalDataActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_data);
        Log.d(TAG, "PersonalDataActivity создана");

        // Use SupabaseAuth from BaseActivity
        String token = getSupabaseAuth().getAccessToken();
        if (token == null) {
            Log.e(TAG, "Токен доступа отсутствует, перенаправление на экран входа");
            Toast.makeText(this, "Необходимо войти в систему", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, SignInActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        usernameEditText = findViewById(R.id.usernameEditText);
        oldPasswordEditText = findViewById(R.id.oldPasswordEditText);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        saveUsernameButton = findViewById(R.id.saveUsernameButton);
        savePasswordButton = findViewById(R.id.savePasswordButton);

        loadUserData();

        setupClickListeners();
    }

    private void loadUserData() {
        Log.d(TAG, "Loading user data");
        String username = getSupabaseAuth().getUsername();

        if (username != null) {
            Log.d(TAG, "Setting username: " + username);
            usernameEditText.setText(username);
        } else {
            Log.w(TAG, "Username is null");
        }
    }

    private void setupClickListeners() {
        saveUsernameButton.setOnClickListener(v -> updateUsername());
        savePasswordButton.setOnClickListener(v -> updatePassword());
    }

    private void updateUsername() {
        String newUsername = usernameEditText.getText().toString().trim();
        Log.d(TAG, "Attempting to update username to: " + newUsername);

        if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
            Log.w(TAG, "Invalid username format");
            Toast.makeText(this, "Имя пользователя должно содержать от 3 до 30 символов (буквы, цифры, ._-)", Toast.LENGTH_LONG).show();
            return;
        }

        progressDialog.setMessage("Обновление имени пользователя...");
        progressDialog.show();

        getSupabaseAuth().updateUsername(newUsername, new SupabaseAuth.SupabaseCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Log.d(TAG, "Username update successful, token: " + (result != null ? result.substring(0, 10) + "..." : "null"));
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    usernameEditText.setText(newUsername);
                    Toast.makeText(PersonalDataActivity.this, "Имя пользователя успешно обновлено", Toast.LENGTH_SHORT).show();
                    loadUserData();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Username update failed: " + error);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(PersonalDataActivity.this, "Ошибка при обновлении имени пользователя: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updatePassword() {
        String oldPassword = oldPasswordEditText.getText().toString();
        String newPassword = newPasswordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();
        Log.d(TAG, "Attempting to update password");

        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            Log.w(TAG, "Invalid password format");
            Toast.makeText(this, "Пароль должен содержать минимум 8 символов, включая заглавные и строчные буквы, и цифры", Toast.LENGTH_LONG).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            Log.w(TAG, "Password confirmation mismatch");
            Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Обновление пароля...");
        progressDialog.show();

        getSupabaseAuth().updatePassword(oldPassword, newPassword, new SupabaseAuth.SupabaseCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Log.d(TAG, "Password update successful");
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    oldPasswordEditText.setText("");
                    newPasswordEditText.setText("");
                    confirmPasswordEditText.setText("");
                    Toast.makeText(PersonalDataActivity.this, "Пароль успешно обновлен", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Password update failed: " + error);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(PersonalDataActivity.this, "Ошибка при обновлении пароля: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
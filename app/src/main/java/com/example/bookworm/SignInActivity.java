package com.example.bookworm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SignInActivity extends BaseActivity {
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private TextView forgotPasswordLinkTextView;
    private TextView registerLinkTextView;
    private SupabaseAuth supabaseAuth;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("BookwormPrefs", MODE_PRIVATE);
        supabaseAuth = new SupabaseAuth(getApplicationContext());

        // Принудительно обновляем токен из хранилища
        String token = supabaseAuth.refreshTokenFromStorage();
        
        // Проверяем, авторизован ли пользователь
        if (supabaseAuth.isLoggedIn()) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_sign_in);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        forgotPasswordLinkTextView = findViewById(R.id.forgotPasswordLinkTextView);
        registerLinkTextView = findViewById(R.id.registerLinkTextView);

        loginButton.setOnClickListener(v -> handleLogin());
        forgotPasswordLinkTextView.setOnClickListener(v -> startActivity(new Intent(this, PasswordRecoveryActivity.class)));
        registerLinkTextView.setOnClickListener(v -> startActivity(new Intent(this, SingUpActivity.class)));
    }

    private void handleLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Показываем индикатор загрузки
        loginButton.setEnabled(false);
        loginButton.setText("Входим...");

        supabaseAuth.signIn(email, password, new SupabaseAuth.SignInCallback() {
            @Override
            public void onSuccess() {
                // Сохраняем состояние авторизации в основных префсах приложения
                preferences.edit().putBoolean("isLoggedIn", true).apply();
                
                // Проверяем, что токен действительно сохранился
                runOnUiThread(() -> {
                    String token = supabaseAuth.getAccessToken();
                    if (token != null) {
                        startMainActivity();
                    } else {
                        Toast.makeText(SignInActivity.this, "Ошибка сохранения токена", Toast.LENGTH_SHORT).show();
                        loginButton.setEnabled(true);
                        loginButton.setText("Войти");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SignInActivity.this, error, Toast.LENGTH_SHORT).show();
                    loginButton.setEnabled(true);
                    loginButton.setText("Войти");
                });
            }
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
}

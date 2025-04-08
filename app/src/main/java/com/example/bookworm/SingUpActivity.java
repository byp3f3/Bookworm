package com.example.bookworm;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Pattern;

public class SingUpActivity extends BaseActivity {
    private TextView signIn;
    private EditText emailEditText, usernameEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private SupabaseAuth supabaseAuth;
    private String email;
    private SharedPreferences preferences;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("BookwormPrefs", MODE_PRIVATE);

        setContentView(R.layout.activity_sign_up);

        emailEditText = findViewById(R.id.emailEditText);
        usernameEditText = findViewById(R.id.usernameEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        registerButton = findViewById(R.id.registerButton);
        signIn = findViewById(R.id.loginLinkTextView);

        supabaseAuth = new SupabaseAuth(this);

        signIn.setOnClickListener(v -> {
            Intent intent = new Intent(SingUpActivity.this, SignInActivity.class);
            startActivity(intent);
        });

        registerButton.setOnClickListener(v -> {
            email = emailEditText.getText().toString().trim();
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            // Validate email
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Введите корректный email");
                return;
            }

            // Validate username
            if (!USERNAME_PATTERN.matcher(username).matches()) {
                usernameEditText.setError("Имя пользователя должно быть от 3 до 30 символов и содержать только буквы, цифры, точки, подчеркивания и дефисы");
                return;
            }

            // Validate password
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                passwordEditText.setError("Пароль должен быть не менее 8 символов и содержать хотя бы одну заглавную букву, одну строчную букву и одну цифру");
                return;
            }

            // Check password confirmation
            if (!password.equals(confirmPassword)) {
                confirmPasswordEditText.setError("Пароли не совпадают");
                return;
            }

            // Register user
            supabaseAuth.register(email, password, username, new SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess(String accessToken) {
                    runOnUiThread(() -> {
                        Toast.makeText(SingUpActivity.this, "Регистрация успешна", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SingUpActivity.this, SignInActivity.class));
                        finish();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        if (errorMessage.contains("email")) {
                            emailEditText.setError("Этот email уже зарегистрирован");
                        } else if (errorMessage.contains("username")) {
                            usernameEditText.setError("Это имя пользователя уже занято");
                        } else {
                            Toast.makeText(SingUpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        });
    }

    private void updateUI() {
        if (supabaseAuth.isLoggedIn()) {
            emailEditText.setEnabled(false);
            usernameEditText.setEnabled(false);
            passwordEditText.setEnabled(false);
            confirmPasswordEditText.setEnabled(false);
            registerButton.setEnabled(false);
        } else {
            emailEditText.setEnabled(true);
            usernameEditText.setEnabled(true);
            passwordEditText.setEnabled(true);
            confirmPasswordEditText.setEnabled(true);
            registerButton.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        supabaseAuth.close();
    }
}

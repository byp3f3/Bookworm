package com.example.bookworm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "BookwormPrefs";
    private static final String KEY_THEME = "theme";

    private LinearLayout themeSettings;
    private TextView lightThemeText;
    private TextView darkThemeText;
    private TextView sepiaThemeText;
    private TextView personalDataText;
    private TextView themeText;
    private SharedPreferences preferences;
    private SupabaseAuth supabaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        supabaseAuth = new SupabaseAuth(getApplicationContext());
        
        // Принудительно обновляем токен из хранилища
        String accessToken = supabaseAuth.refreshTokenFromStorage();
        Log.d(TAG, "Принудительное обновление токена: " + (accessToken != null ? "токен найден" : "токен отсутствует"));

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initializeViews();
        setupClickListeners();
        loadSettings();
    }

    private void initializeViews() {
        themeSettings = findViewById(R.id.themeSettings);
        lightThemeText = findViewById(R.id.lightThemeText);
        darkThemeText = findViewById(R.id.darkThemeText);
        sepiaThemeText = findViewById(R.id.sepiaThemeText);
        personalDataText = findViewById(R.id.personalDataText);
        themeText = findViewById(R.id.themeText);
    }

    private void setupClickListeners() {
        personalDataText.setOnClickListener(v -> {
            // Проверяем наличие токена перед переходом
            String accessToken = supabaseAuth.getAccessToken();
            Log.d(TAG, "Проверка токена перед переходом: " + (accessToken != null ? "токен найден" : "токен отсутствует"));
            
            if (accessToken == null) {
                Log.e(TAG, "Токен доступа отсутствует, перенаправление на экран входа");
                Toast.makeText(this, "Необходимо войти в систему", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return;
            }
            
            Intent intent = new Intent(SettingsActivity.this, PersonalDataActivity.class);
            startActivity(intent);
        });

        themeText.setOnClickListener(v -> {
            boolean isVisible = themeSettings.getVisibility() == View.VISIBLE;
            themeSettings.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        });

        lightThemeText.setOnClickListener(v -> setTheme("light"));
        darkThemeText.setOnClickListener(v -> setTheme("dark"));
        sepiaThemeText.setOnClickListener(v -> setTheme("sepia"));
    }

    private void loadSettings() {
        String theme = preferences.getString(KEY_THEME, "light");
        updateThemeViews(theme);
    }

    private void setTheme(String theme) {
        preferences.edit()
                .putString(KEY_THEME, theme)
                .apply();
        updateThemeViews(theme);
        applyTheme();
        recreate();
    }

    private void updateThemeViews(String selectedTheme) {
        lightThemeText.setSelected(selectedTheme.equals("light"));
        darkThemeText.setSelected(selectedTheme.equals("dark"));
        sepiaThemeText.setSelected(selectedTheme.equals("sepia"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
}
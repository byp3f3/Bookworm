package com.example.bookworm;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "BookwormPrefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_IS_RECREATING = "is_recreating";
    protected SupabaseAuth supabaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        if (!isRecreating()) {
            supabaseAuth = new SupabaseAuth(getApplicationContext());
        }
    }

    protected void applyTheme() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String theme = preferences.getString(KEY_THEME, "light");
        
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                setTheme(R.style.Theme_Bookworm);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                setTheme(R.style.Theme_Bookworm);
                break;
            case "sepia":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                setTheme(R.style.Theme_Bookworm_Sepia);
                break;
        }
    }

    @Override
    public void recreate() {
        // Set the recreating flag before recreating
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_RECREATING, true)
                .apply();
        super.recreate();
    }

    protected boolean isRecreating() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isRecreating = preferences.getBoolean(KEY_IS_RECREATING, false);
        if (isRecreating) {
            // Reset the flag after checking
            preferences.edit().putBoolean(KEY_IS_RECREATING, false).apply();
        }
        return isRecreating;
    }

    protected String getCurrentTheme(){
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getString(KEY_THEME, "light");
    }

    protected SupabaseAuth getSupabaseAuth() {
        if (supabaseAuth == null) {
            supabaseAuth = new SupabaseAuth(getApplicationContext());
        }
        return supabaseAuth;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Only close SupabaseAuth if the activity is finishing and not recreating
        if (isFinishing() && !isRecreating() && supabaseAuth != null) {
            supabaseAuth.close();
            supabaseAuth = null;
        }
    }
} 
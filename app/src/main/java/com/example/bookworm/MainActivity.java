package com.example.bookworm;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends BaseActivity {
    private BottomNavigationView navView;
    private SupabaseAuth supabaseAuth;
    private String currentTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Проверяем авторизацию только если не пересоздаем активность
        if (!isRecreating()) {
            supabaseAuth = new SupabaseAuth(getApplicationContext());
            
            // Принудительно обновляем токен из хранилища
            supabaseAuth.refreshTokenFromStorage();
            
            if (!supabaseAuth.isLoggedIn()) {
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return;
            }
        }


        setContentView(R.layout.activity_main);
        currentTheme = getCurrentTheme(); // Получаем текущую тему (например, из SharedPreferences)
        applyTheme();

        navView = findViewById(R.id.nav_view);
        navView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.navigation_library) {
                selectedFragment = new LibraryFragment();
            } else if (itemId == R.id.navigation_shelves) {
                selectedFragment = new ShelvesFragment();
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.nav_host_fragment, selectedFragment);
                transaction.commit();
                return true;
            }
            return false;
        });

        // Установка начального фрагмента
        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.navigation_home);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String newTheme = getCurrentTheme();
        if (!newTheme.equals(currentTheme)) {
            currentTheme = newTheme;
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
}

package com.example.bookworm;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import com.example.bookworm.fragments.PasswordRecoveryEmailFragment;

public class PasswordRecoveryActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_recovery);

        // Start with the email input fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, new PasswordRecoveryEmailFragment())
                .commit();
    }
} 
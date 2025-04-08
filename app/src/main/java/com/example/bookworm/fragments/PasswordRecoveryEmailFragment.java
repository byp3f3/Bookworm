package com.example.bookworm.fragments;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.bookworm.R;
import com.example.bookworm.SupabaseAuth;

public class PasswordRecoveryEmailFragment extends BaseAuthFragment {
    private EditText emailEditText;
    private Button resetButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_recovery_email, container, false);

        emailEditText = view.findViewById(R.id.emailEditText);
        resetButton = view.findViewById(R.id.resetButton);

        resetButton.setOnClickListener(v -> {
            email = emailEditText.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Введите корректный email");
                return;
            }

            supabaseAuth.resetPassword(email, new SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess(String accessToken) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Код подтверждения отправлен на ваш email", Toast.LENGTH_LONG).show();
                        showVerificationFragment();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    requireActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show());
                }
            });
        });

        return view;
    }

    private void showVerificationFragment() {
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, new PasswordRecoveryVerificationFragment())
                .addToBackStack(null)
                .commit();
    }
} 
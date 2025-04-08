package com.example.bookworm.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bookworm.R;
import com.example.bookworm.SignInActivity;
import com.example.bookworm.SupabaseAuth;

import java.util.regex.Pattern;

public class PasswordRecoveryNewPasswordFragment extends BaseAuthFragment {
    private EditText newPasswordEditText;
    private EditText confirmPasswordEditText;
    private Button updateButton;
    private String verificationCode;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            verificationCode = getArguments().getString("verification_code");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_recovery_new_password, container, false);

        newPasswordEditText = view.findViewById(R.id.newPasswordEditText);
        confirmPasswordEditText = view.findViewById(R.id.confirmPasswordEditText);
        updateButton = view.findViewById(R.id.updateButton);

        updateButton.setOnClickListener(v -> {
            String newPassword = newPasswordEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
                newPasswordEditText.setError("Пароль должен быть не менее 8 символов и содержать хотя бы одну заглавную букву, одну строчную букву и одну цифру");
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                confirmPasswordEditText.setError("Пароли не совпадают");
                return;
            }

            supabaseAuth.verifyPasswordReset(verificationCode, newPassword, new SupabaseAuth.AuthCallback() {
                @Override
                public void onSuccess(String accessToken) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Пароль успешно обновлен", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(requireContext(), SignInActivity.class);
                        startActivity(intent);
                        requireActivity().finish();
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
} 
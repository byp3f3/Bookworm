package com.example.bookworm.fragments;

import android.os.Bundle;
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

public class PasswordRecoveryVerificationFragment extends BaseAuthFragment {
    private EditText codeEditText;
    private Button verifyButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_recovery_verification, container, false);

        codeEditText = view.findViewById(R.id.codeEditText);
        verifyButton = view.findViewById(R.id.verifyButton);

        verifyButton.setOnClickListener(v -> {
            String code = codeEditText.getText().toString().trim();
            if (code.length() != 4) {
                codeEditText.setError("Код должен содержать 4 цифры");
                return;
            }

            showNewPasswordFragment(code);
        });

        return view;
    }

    private void showNewPasswordFragment(String code) {
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        PasswordRecoveryNewPasswordFragment fragment = new PasswordRecoveryNewPasswordFragment();
        Bundle args = new Bundle();
        args.putString("verification_code", code);
        fragment.setArguments(args);
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }
} 
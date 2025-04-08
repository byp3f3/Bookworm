package com.example.bookworm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProfileFragment extends BaseFragment {
    private SupabaseAuth supabaseAuth;
    private TextView emailTextView;
    private TextView usernameTextView;
    private Button logoutButton;
    private ImageButton settingsButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        supabaseAuth = new SupabaseAuth(requireContext());

        emailTextView = view.findViewById(R.id.emailTextView);
        usernameTextView = view.findViewById(R.id.usernameTextView);
        logoutButton = view.findViewById(R.id.logoutButton);
        settingsButton = view.findViewById(R.id.settingsButton);

        // Load user profile
        loadUserProfile();

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> {
            supabaseAuth.signOut();
            Toast.makeText(getContext(), "Вы успешно вышли", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(getActivity(), SignInActivity.class));
            requireActivity().finish();
        });

        return view;
    }

    private void loadUserProfile() {
        String email = supabaseAuth.getCurrentUserEmail();
        if (email != null) {
            emailTextView.setText(email);
        }

        String displayName = supabaseAuth.getCurrentUserDisplayName();
        if (displayName != null) {
            usernameTextView.setText(displayName);
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
}
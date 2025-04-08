package com.example.bookworm.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bookworm.SupabaseAuth;

public abstract class BaseAuthFragment extends Fragment {
    protected SupabaseAuth supabaseAuth;
    protected String email;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supabaseAuth = new SupabaseAuth(requireContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (supabaseAuth != null) {
            supabaseAuth.close();
        }
    }
} 
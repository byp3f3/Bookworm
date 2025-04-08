package com.example.bookworm;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class BaseFragment extends Fragment {
    private static final String PREFS_NAME = "BookwormPrefs";
    private static final String KEY_FONT_SIZE = "font_size";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyFontSize(view);
    }

    protected void applyFontSize(View view) {
        SharedPreferences preferences = requireContext().getSharedPreferences(PREFS_NAME, 0);
        int fontSize = preferences.getInt(KEY_FONT_SIZE, 16);
        
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                applyFontSize(child);
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextSize(fontSize);
        }
    }



} 
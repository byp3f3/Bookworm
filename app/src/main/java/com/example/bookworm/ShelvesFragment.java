package com.example.bookworm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.bookworm.adapters.ShelvesPagerAdapter;
import com.example.bookworm.fragments.BookShelvesFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Arrays;
import java.util.List;

public class ShelvesFragment extends Fragment {
    private TabLayout tabLayout;
    private FloatingActionButton fabAddShelf;
    private ShelvesPagerAdapter pagerAdapter;
    
    private BookShelvesFragment bookShelvesFragment = new BookShelvesFragment();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shelves, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tabLayout = view.findViewById(R.id.tabLayout);
        fabAddShelf = view.findViewById(R.id.fabAddShelf);
        
        // Скрываем TabLayout, так как у нас теперь только одна вкладка
        tabLayout.setVisibility(View.GONE);
        
        // Настраиваем отображение фрагмента книжных полок
        getChildFragmentManager().beginTransaction()
            .replace(R.id.viewPager, bookShelvesFragment)
            .commit();
        
        setupFabButton();
    }
    
    private void setupFabButton() {
        fabAddShelf.setOnClickListener(v -> {
            bookShelvesFragment.showCreateShelfDialog();
        });
    }
} 
package com.example.bookworm.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bookworm.BaseFragment;
import com.example.bookworm.R;
import com.example.bookworm.ShelfDetailActivity;
import com.example.bookworm.SupabaseAuth;
import com.example.bookworm.adapters.ShelfAdapter;
import com.example.bookworm.models.Shelf;
import com.example.bookworm.services.SupabaseService;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class BookShelvesFragment extends BaseFragment implements ShelfAdapter.ShelfInteractionListener {
    private static final String TAG = "BookShelvesFragment";
    private RecyclerView recyclerViewShelves;
    private TextView textViewNoShelves;
    private ShelfAdapter shelfAdapter;
    private List<Shelf> shelves = new ArrayList<>();
    private String currentUserId;
    private SupabaseService supabaseService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_books_shelves, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        recyclerViewShelves = view.findViewById(R.id.recyclerViewShelves);
        textViewNoShelves = view.findViewById(R.id.textViewNoShelves);

        supabaseService = new SupabaseService(requireContext());
        
        // Get current user ID from Supabase
        SupabaseAuth supabaseAuth = new SupabaseAuth(requireContext());
        
        // First check if the user is logged in
        if (!supabaseAuth.isLoggedIn()) {
            Log.e(TAG, "User is not logged in, can't access shelves");
            textViewNoShelves.setText("Вы не авторизованы. Пожалуйста, войдите в систему.");
            textViewNoShelves.setVisibility(View.VISIBLE);
            recyclerViewShelves.setVisibility(View.GONE);
            return;
        }
        
        // Try to get current user ID
        currentUserId = supabaseAuth.getCurrentUserId();
        Log.d(TAG, "Attempting to get current user ID: " + (currentUserId != null ? currentUserId : "null"));
        
        if (currentUserId == null) {
            Log.e(TAG, "Failed to get current user ID despite being logged in");
            
            // Try to get access token and debug it
            String accessToken = supabaseAuth.getAccessToken();
            Log.d(TAG, "Access token exists: " + (accessToken != null && !accessToken.isEmpty()));
            
            textViewNoShelves.setText("Ошибка получения ID пользователя. Попробуйте выйти и войти снова.");
            textViewNoShelves.setVisibility(View.VISIBLE);
            recyclerViewShelves.setVisibility(View.GONE);
            return;
        }
        
        Log.d(TAG, "Current user ID successfully retrieved: " + currentUserId);
        setupRecyclerView();
        loadShelves();
    }

    private void setupRecyclerView() {
        shelfAdapter = new ShelfAdapter(requireContext(), shelves, this);
        recyclerViewShelves.setAdapter(shelfAdapter);
    }

    private void loadShelves() {
        Log.d(TAG, "Loading shelves for user ID: " + currentUserId);
        // Show loading state
        textViewNoShelves.setVisibility(View.GONE);
        recyclerViewShelves.setVisibility(View.GONE);

        supabaseService.getShelves(new SupabaseService.ShelvesLoadCallback() {
            @Override
            public void onSuccess(List<Shelf> loadedShelves) {
                Log.d(TAG, "Loaded " + loadedShelves.size() + " shelves from Supabase");
                for (Shelf shelf : loadedShelves) {
                    Log.d(TAG, "Shelf: " + shelf.getName() + " (ID: " + shelf.getId() + ") has " + 
                          (shelf.getBookIds() != null ? shelf.getBookIds().size() : 0) + " books");
                }
                
                shelves.clear();
                shelves.addAll(loadedShelves);
                shelfAdapter.notifyDataSetChanged();
                
                if (shelves.isEmpty()) {
                    textViewNoShelves.setVisibility(View.VISIBLE);
                    recyclerViewShelves.setVisibility(View.GONE);
                } else {
                    textViewNoShelves.setVisibility(View.GONE);
                    recyclerViewShelves.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading shelves: " + error);
                textViewNoShelves.setText("Ошибка загрузки полок: " + error);
                textViewNoShelves.setVisibility(View.VISIBLE);
                recyclerViewShelves.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh shelves in case they were modified in another activity
        loadShelves();
    }
    
    public void showCreateShelfDialog() {
        showShelfDialog(null);
    }

    private void showShelfDialog(Shelf shelf) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_shelf_form);
        
        TextView textViewDialogTitle = dialog.findViewById(R.id.textViewDialogTitle);
        EditText editTextShelfName = dialog.findViewById(R.id.editTextShelfName);
        EditText editTextShelfDescription = dialog.findViewById(R.id.editTextShelfDescription);
        TextView textViewErrorMessage = dialog.findViewById(R.id.textViewErrorMessage);
        Button buttonCancel = dialog.findViewById(R.id.buttonCancel);
        Button buttonSave = dialog.findViewById(R.id.buttonSave);
        
        boolean isEditing = shelf != null;
        textViewDialogTitle.setText(isEditing ? "Изменить полку" : "Создать полку");
        
        if (isEditing) {
            editTextShelfName.setText(shelf.getName());
            editTextShelfDescription.setText(shelf.getDescription());
        }
        
        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        
        buttonSave.setOnClickListener(v -> {
            String name = editTextShelfName.getText().toString().trim();
            String description = editTextShelfDescription.getText().toString().trim();
            
            if (name.isEmpty()) {
                textViewErrorMessage.setText("Название полки не может быть пустым");
                textViewErrorMessage.setVisibility(View.VISIBLE);
                return;
            }
            
            // Check for duplicate shelf name by searching existing shelves
            boolean isNameExists = false;
            for (Shelf existingShelf : shelves) {
                if (existingShelf.getName().equalsIgnoreCase(name)) {
                    // If editing, allow the shelf to keep its own name
                    if (isEditing && existingShelf.getId().equals(shelf.getId())) {
                        continue;
                    }
                    isNameExists = true;
                    break;
                }
            }
            
            if (isNameExists) {
                textViewErrorMessage.setText("Это имя полки уже занято");
                textViewErrorMessage.setVisibility(View.VISIBLE);
                return;
            }
            
            if (isEditing) {
                // Update existing shelf
                shelf.setName(name);
                shelf.setDescription(description);
                
                supabaseService.updateShelf(shelf, new SupabaseService.ShelfCallback() {
                    @Override
                    public void onSuccess() {
                        loadShelves();
                        dialog.dismiss();
                        Snackbar.make(requireView(), "Полка обновлена", Snackbar.LENGTH_SHORT).show();
                    }
                    
                    @Override
                    public void onError(String error) {
                        textViewErrorMessage.setText("Ошибка обновления полки: " + error);
                        textViewErrorMessage.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                // Create new shelf
                Shelf newShelf = new Shelf(name, description, currentUserId);
                Log.d(TAG, "Creating new shelf: " + name + ", UserID: " + currentUserId);
                
                if (currentUserId == null || currentUserId.isEmpty()) {
                    Log.e(TAG, "Error: currentUserId is null or empty");
                    textViewErrorMessage.setText("Ошибка: Вы не авторизованы");
                    textViewErrorMessage.setVisibility(View.VISIBLE);
                    return;
                }
                
                supabaseService.saveShelf(newShelf, new SupabaseService.ShelfCallback() {
                    @Override
                    public void onSuccess() {
                        loadShelves();
                        dialog.dismiss();
                        Snackbar.make(requireView(), "Полка создана", Snackbar.LENGTH_SHORT).show();
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error creating shelf: " + error);
                        textViewErrorMessage.setText("Ошибка создания полки: " + error);
                        textViewErrorMessage.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
        
        dialog.show();
    }

    private void showDeleteShelfDialog(Shelf shelf) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_delete_confirmation, null);
        
        TextView textViewDeleteTitle = view.findViewById(R.id.textViewDeleteTitle);
        TextView textViewDeleteMessage = view.findViewById(R.id.textViewDeleteMessage);
        Button buttonCancel = view.findViewById(R.id.buttonCancel);
        Button buttonDelete = view.findViewById(R.id.buttonDelete);
        
        textViewDeleteTitle.setText("Удалить полку");
        textViewDeleteMessage.setText(String.format(
                "Вы уверены, что хотите удалить полку \"%s\"? Все книги останутся в вашей библиотеке.",
                shelf.getName()));
        
        builder.setView(view);
        AlertDialog dialog = builder.create();
        
        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        
        buttonDelete.setOnClickListener(v -> {
            // Delete shelf from Supabase
            supabaseService.deleteShelf(shelf.getId(), new SupabaseService.ShelfCallback() {
                @Override
                public void onSuccess() {
                    // Remove from local list and update UI
                    shelves.remove(shelf);
                    shelfAdapter.notifyDataSetChanged();
                    
                    if (shelves.isEmpty()) {
                        textViewNoShelves.setVisibility(View.VISIBLE);
                        recyclerViewShelves.setVisibility(View.GONE);
                    }
                    
                    dialog.dismiss();
                    Snackbar.make(requireView(), "Полка удалена", Snackbar.LENGTH_SHORT).show();
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error deleting shelf: " + error);
                    dialog.dismiss();
                    Snackbar.make(requireView(), "Ошибка удаления полки: " + error, Snackbar.LENGTH_SHORT).show();
                }
            });
        });
        
        dialog.show();
    }

    @Override
    public void onShelfClick(Shelf shelf) {
        // Navigate to shelf detail screen
        Intent intent = new Intent(requireContext(), ShelfDetailActivity.class);
        intent.putExtra("SHELF_ID", shelf.getId());
        startActivity(intent);
    }

    @Override
    public void onShelfMenuClick(Shelf shelf, View anchorView) {
        // Show popup menu for shelf
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchorView);
        popupMenu.inflate(R.menu.menu_shelf);
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_edit_shelf) {
                showShelfDialog(shelf);
                return true;
            } else if (itemId == R.id.menu_delete_shelf) {
                showDeleteShelfDialog(shelf);
                return true;
            }
            return false;
        });
        
        popupMenu.show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (supabaseService != null) {
            supabaseService.shutdown();
        }
    }
} 
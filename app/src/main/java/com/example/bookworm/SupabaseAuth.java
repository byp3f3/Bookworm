package com.example.bookworm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Base64;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class SupabaseAuth {

    private static final String TAG = "SupabaseAuth";
    
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private static final String PREF_NAME = "supabase_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_DISPLAY_NAME = "display_name";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences sharedPreferences;

    public interface AuthCallback {
        void onSuccess(String accessToken);
        void onError(String errorMessage);
    }

    public interface SignInCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface SupabaseCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public SupabaseAuth(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        
        // Загружаем конфигурацию из config.properties из raw ресурсов
        Properties properties = loadConfigProperties();
        if (properties == null) {
            throw new RuntimeException("Failed to load configuration properties");
        }
        
        this.supabaseUrl = properties.getProperty("SUPABASE_URL");
        this.supabaseAnonKey = properties.getProperty("SUPABASE_ANON_KEY");
        
        if (supabaseUrl == null || supabaseAnonKey == null) {
            throw new RuntimeException("SUPABASE_URL or SUPABASE_ANON_KEY not found in config");
        }
        
        Log.d(TAG, "SupabaseAuth initialized with URL: " + supabaseUrl);
        
        initializeSharedPreferences();
    }
    
    private Properties loadConfigProperties() {
        Properties properties = new Properties();
        
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.config)) {
            properties.load(inputStream);
            Log.d(TAG, "Successfully loaded config from raw resource");
            
            // Проверяем загруженные значения
            String url = properties.getProperty("SUPABASE_URL");
            String key = properties.getProperty("SUPABASE_ANON_KEY");
            Log.d(TAG, "Loaded URL: " + (url != null ? "present" : "missing"));
            Log.d(TAG, "Loaded KEY: " + (key != null ? "present" : "missing"));
            
            return properties;
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Config file not found in raw resources: " + e.getMessage(), e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Error loading config file: " + e.getMessage(), e);
            return null;
        }
    }

    private void initializeSharedPreferences() {
        try {
            this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Log.d(TAG, "SharedPreferences initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SharedPreferences: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize SharedPreferences", e);
        }
    }

    public void register(String email, String password, String username, AuthCallback callback) {
        new Thread(() -> {
            try {
                // 1. Проверяем, существует ли username
                String checkUsernameUrl = supabaseUrl + "/rest/v1/users?username=eq." + username;
                Request checkUsernameRequest = new Request.Builder()
                        .url(checkUsernameUrl)
                        .get()
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response checkResponse = client.newCall(checkUsernameRequest).execute()) {
                    if (checkResponse.isSuccessful() && checkResponse.body() != null) {
                        String responseBody = checkResponse.body().string();
                        if (!responseBody.equals("[]")) {
                            mainHandler.post(() -> callback.onError("username"));
                            return;
                        }
                    }
                }

                // 2. Регистрируем пользователя через Supabase Auth
                JsonObject registrationData = new JsonObject();
                registrationData.addProperty("email", email);
                registrationData.addProperty("password", password);

                // Добавляем метаданные пользователя (username и display_name)
                JsonObject userMetadata = new JsonObject();
                userMetadata.addProperty("username", username);
                userMetadata.addProperty("display_name", username);
                registrationData.add("data", userMetadata);

                String registerUrl = supabaseUrl + "/auth/v1/signup";
                RequestBody registerBody = RequestBody.create(
                        registrationData.toString(),
                        MediaType.get("application/json; charset=utf-8")
                );

                Request registerRequest = new Request.Builder()
                        .url(registerUrl)
                        .post(registerBody)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response registerResponse = client.newCall(registerRequest).execute()) {
                    if (!registerResponse.isSuccessful()) {
                        String errorBody = registerResponse.body() != null ? registerResponse.body().string() : "";
                        Log.e(TAG, "Ошибка регистрации: " + registerResponse.code() + " " + errorBody);
                        
                        if (registerResponse.code() == 422) {
                            mainHandler.post(() -> callback.onError("Пользователь с этим email или именем уже существует"));
                        } else if (errorBody.contains("email")) {
                            mainHandler.post(() -> callback.onError("email"));
                        } else if (errorBody.contains("username")) {
                            mainHandler.post(() -> callback.onError("username"));
                        } else {
                            mainHandler.post(() -> callback.onError("Ошибка регистрации: " + errorBody));
                        }
                        return;
                    }

                    // 3. Получаем данные пользователя после успешной регистрации
                    String responseBody = registerResponse.body().string();
                    JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                    String accessToken = responseJson.get("access_token").getAsString();
                    String userId = responseJson.getAsJsonObject("user").get("id").getAsString();

                    // 4. Сохраняем пользователя в таблицу `users`
                    JsonObject userData = new JsonObject();
                    userData.addProperty("id", userId);
                    userData.addProperty("email", email);
                    userData.addProperty("username", username);
                    userData.addProperty("display_name", username);
                    // Use ISO 8601 format for timestamp
                    userData.addProperty("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .format(new java.util.Date()));

                    RequestBody userRecordBody = RequestBody.create(
                            userData.toString(),
                            MediaType.get("application/json; charset=utf-8")
                    );

                    Request userRecordRequest = new Request.Builder()
                            .url(supabaseUrl + "/rest/v1/users")
                            .post(userRecordBody)
                            .addHeader("apikey", supabaseAnonKey)
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .build();

                    try (Response userRecordResponse = client.newCall(userRecordRequest).execute()) {
                        if (!userRecordResponse.isSuccessful()) {
                            String errorBody = userRecordResponse.body() != null ? userRecordResponse.body().string() : "";
                            Log.e(TAG, "Ошибка при создании записи пользователя: " + userRecordResponse.code() + " " + errorBody);
                            
                            // Если запись пользователя не создалась, но регистрация прошла успешно,
                            // все равно возвращаем успешный результат
                            Log.d(TAG, "Регистрация прошла успешно, но запись в таблице users не создана");
                            mainHandler.post(() -> callback.onSuccess(accessToken));
                            return;
                        }
                        Log.d(TAG, "Запись пользователя успешно создана в таблице users");
                    }

                    // 5. Сохраняем email и имя в SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_USER_EMAIL, email);
                    editor.putString(KEY_USER_DISPLAY_NAME, username);
                    editor.apply();

                    // 6. Возвращаем успешный результат
                    mainHandler.post(() -> callback.onSuccess(accessToken));
                }
            } catch (IOException e) {
                Log.e(TAG, "Ошибка при регистрации: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + e.getMessage()));
            }
        }).start();
    }

    public void resetPassword(String email, AuthCallback callback) {
        new Thread(() -> {
            try {
                String url = supabaseUrl + "/auth/v1/otp";
                JsonObject json = new JsonObject();
                json.addProperty("email", email);
                json.addProperty("type", "recovery");
                json.addProperty("should_create_user", false);

                RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "Ошибка восстановления пароля: " + response.code() + " " + errorBody);
                        if (errorBody.contains("not found")) {
                            mainHandler.post(() -> callback.onError("Пользователь с таким email не зарегистрирован"));
                        } else {
                            mainHandler.post(() -> callback.onError("Ошибка восстановления пароля: " + response.code()));
                        }
                    } else {
                        // Parse response to get the verification code
                        String responseBody = response.body().string();
                        JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                        String verificationCode = responseJson.get("code").getAsString();

                        // Store email for verification
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("recovery_email", email);
                        editor.apply();

                        Log.d(TAG, "Код подтверждения отправлен: " + verificationCode);
                        mainHandler.post(() -> {
                            Toast.makeText(context, "Код подтверждения: " + verificationCode, Toast.LENGTH_LONG).show();
                            callback.onSuccess("");
                        });
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Ошибка при выполнении запроса: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка при выполнении запроса: " + e.getMessage()));
            }
        }).start();
    }

    public void verifyPasswordReset(String code, String newPassword, AuthCallback callback) {
        new Thread(() -> {
            try {
                // Get stored email
                String email = sharedPreferences.getString("recovery_email", null);
                if (email == null) {
                    mainHandler.post(() -> callback.onError("Ошибка: email не найден"));
                    return;
                }

                String url = supabaseUrl + "/auth/v1/verify";
                JsonObject json = new JsonObject();
                json.addProperty("email", email);
                json.addProperty("token", code);
                json.addProperty("type", "recovery");
                json.addProperty("password", newPassword);

                RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Ошибка обновления пароля: " + response.code() + " " + response.body().string());
                        mainHandler.post(() -> callback.onError("Неверный код"));
                    } else {
                        // Clear stored email
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.remove("recovery_email");
                        editor.apply();

                        Log.d(TAG, "Пароль успешно обновлен!");
                        mainHandler.post(() -> callback.onSuccess(""));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Ошибка при выполнении запроса: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка при выполнении запроса: " + e.getMessage()));
            }
        }).start();
    }

    private void clearAccessToken() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_ACCESS_TOKEN);
        editor.apply();
        Log.d(TAG, "Access Token cleared");
    }


    public String refreshTokenFromStorage() {
        try {
            // Повторно получаем SharedPreferences, чтобы гарантировать актуальное состояние
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String token = prefs.getString(KEY_ACCESS_TOKEN, null);
            Log.d(TAG, "Обновление токена из хранилища: " + (token != null ? "токен найден" : "токен отсутствует"));
            
            if (token != null) {
                // Проверяем валидность токена
                try {
                    String[] parts = token.split("\\.");
                    if (parts.length == 3) {
                        String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE));
                        JsonObject payloadJson = gson.fromJson(payload, JsonObject.class);
                        long expTime = payloadJson.get("exp").getAsLong();
                        long currentTime = System.currentTimeMillis() / 1000;
                        
                        if (expTime < currentTime) {
                            Log.e(TAG, "Токен истек");
                            token = null;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при проверке токена: " + e.getMessage());
                    token = null;
                }
            }
            
            // Обновляем локальную копию SharedPreferences
            this.sharedPreferences = prefs;
            
            return token;
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing token from storage: " + e.getMessage(), e);
            return null;
        }
    }

    public String getAccessToken() {
        try {
            // Сначала пробуем получить токен из кэша
            String token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
            
            // Если токен не найден или пустой, пробуем обновить из хранилища
            if (token == null || token.isEmpty()) {
                token = refreshTokenFromStorage();
            }
            
            // Проверяем валидность токена
            if (token != null) {
                try {
                    String[] parts = token.split("\\.");
                    if (parts.length == 3) {
                        String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE));
                        JsonObject payloadJson = gson.fromJson(payload, JsonObject.class);
                        long expTime = payloadJson.get("exp").getAsLong();
                        long currentTime = System.currentTimeMillis() / 1000;
                        
                        if (expTime < currentTime) {
                            Log.e(TAG, "Токен истек");
                            token = null;
                            clearAccessToken();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка при проверке токена: " + e.getMessage());
                    token = null;
                    clearAccessToken();
                }
            }
            
            Log.d(TAG, "Получение токена доступа: " + (token != null ? "токен получен" : "токен отсутствует"));
            return token;
        } catch (Exception e) {
            Log.e(TAG, "Error getting access token: " + e.getMessage(), e);
            return null;
        }
    }

    public boolean isLoggedIn() {
        try {
            String token = getAccessToken();
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "Error checking login status: " + e.getMessage(), e);
            return false;
        }
    }

    public void close() {
        try {
            Log.d(TAG, "SupabaseAuth resources have been closed.");
        } catch (Exception e) {
            Log.e(TAG, "Error closing SupabaseAuth: " + e.getMessage(), e);
        }
    }

    /**
     * Explicitly cleans up all authentication data.
     * This should only be called when you want to completely reset the auth state.
     */
    public void cleanup() {
        try {
            clearAccessToken();
            Log.d(TAG, "SupabaseAuth resources and tokens have been cleaned up.");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up SupabaseAuth: " + e.getMessage(), e);
        }
    }

    public void signOut() {
        try {
            Log.d(TAG, "Выход из системы");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(KEY_ACCESS_TOKEN);
            editor.remove(KEY_REFRESH_TOKEN);
            editor.remove(KEY_USER_EMAIL);
            editor.remove(KEY_USER_DISPLAY_NAME);
            boolean success = editor.commit();
            Log.d(TAG, "Данные удалены успешно: " + success);
            
            // Call cleanup to ensure complete reset of auth state
            cleanup();
            
            // Проверяем, что токен действительно удален
            String token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
            Log.d(TAG, "Проверка удаления токена: " + (token != null ? "токен не удален" : "токен удален"));
        } catch (Exception e) {
            Log.e(TAG, "Error during sign out: " + e.getMessage(), e);
        }
    }

    public String getCurrentUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, null);
    }

    public String getCurrentUserDisplayName() {
        return sharedPreferences.getString(KEY_USER_DISPLAY_NAME, null);
    }

    public String getCurrentUsername() {
        return sharedPreferences.getString(KEY_USER_DISPLAY_NAME, null);
    }

    public String getUserEmail() {
        return getCurrentUserEmail();
    }

    public String getUsername() {
        return getCurrentUserDisplayName();
    }

    public void updateUsername(String newUsername, SupabaseCallback<String> callback) {
        new Thread(() -> {
            try {
                // 1. Get access token
                String accessToken = getAccessToken();
                if (accessToken == null || accessToken.isEmpty()) {
                    mainHandler.post(() -> callback.onError("User not authenticated"));
                    return;
                }

                // 2. Get user ID from token
                String[] parts = accessToken.split("\\.");
                String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE));
                JsonObject payloadJson = gson.fromJson(payload, JsonObject.class);
                String userId = payloadJson.get("sub").getAsString();

                // 3. First update user metadata
                String userUrl = supabaseUrl + "/auth/v1/user";
                JsonObject userUpdateData = new JsonObject();
                JsonObject userMetadata = new JsonObject();
                userMetadata.addProperty("username", newUsername);
                userMetadata.addProperty("display_name", newUsername);
                userUpdateData.add("data", userMetadata);

                RequestBody userBody = RequestBody.create(
                        userUpdateData.toString(),
                        MediaType.get("application/json; charset=utf-8")
                );

                Request userRequest = new Request.Builder()
                        .url(userUrl)
                        .put(userBody)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response userResponse = client.newCall(userRequest).execute()) {
                    if (!userResponse.isSuccessful()) {
                        String errorBody = userResponse.body() != null ? userResponse.body().string() : "";
                        mainHandler.post(() -> callback.onError("Failed to update user metadata: " + errorBody));
                        return;
                    }

                    // 4. Check if user exists in public.users table
                    String checkUrl = supabaseUrl + "/rest/v1/users?id=eq." + userId;
                    Request checkRequest = new Request.Builder()
                            .url(checkUrl)
                            .get()
                            .addHeader("apikey", supabaseAnonKey)
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json")
                            .build();

                    try (Response checkResponse = client.newCall(checkRequest).execute()) {
                        String checkResponseBody = checkResponse.body() != null ? checkResponse.body().string() : "";
                        Log.d(TAG, "Check user response: " + checkResponse.code() + " " + checkResponseBody);

                        // 5. If user doesn't exist, create it
                        if (checkResponse.code() == 200 && checkResponseBody.equals("[]")) {
                            Log.d(TAG, "User not found in public.users table, creating new record");
                            String createUrl = supabaseUrl + "/rest/v1/users";
                            JsonObject createData = new JsonObject();
                            createData.addProperty("id", userId);
                            createData.addProperty("username", newUsername);
                            createData.addProperty("display_name", newUsername);
                            createData.addProperty("email", getCurrentUserEmail());
                            // Use ISO 8601 format for timestamp
                            createData.addProperty("created_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                    .format(new java.util.Date()));

                            RequestBody createBody = RequestBody.create(
                                    createData.toString(),
                                    MediaType.get("application/json; charset=utf-8")
                            );

                            Request createRequest = new Request.Builder()
                                    .url(createUrl)
                                    .post(createBody)
                                    .addHeader("apikey", supabaseAnonKey)
                                    .addHeader("Authorization", "Bearer " + accessToken)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .build();

                            try (Response createResponse = client.newCall(createRequest).execute()) {
                                if (!createResponse.isSuccessful()) {
                                    String createErrorBody = createResponse.body() != null ? createResponse.body().string() : "";
                                    Log.e(TAG, "Failed to create user record: " + createResponse.code() + " " + createErrorBody);
                                    mainHandler.post(() -> callback.onError("Failed to create user profile: " + createErrorBody));
                                    return;
                                }
                                Log.d(TAG, "User record created successfully");
                            }
                        } else {
                            // 6. If user exists, update it
                            Log.d(TAG, "User found in public.users table, updating record");
                            String updateUrl = supabaseUrl + "/rest/v1/users?id=eq." + userId;
                            JsonObject updateData = new JsonObject();
                            updateData.addProperty("username", newUsername);
                            updateData.addProperty("display_name", newUsername);

                            RequestBody updateBody = RequestBody.create(
                                    updateData.toString(),
                                    MediaType.get("application/json; charset=utf-8")
                            );

                            Request updateRequest = new Request.Builder()
                                    .url(updateUrl)
                                    .patch(updateBody)
                                    .addHeader("apikey", supabaseAnonKey)
                                    .addHeader("Authorization", "Bearer " + accessToken)
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "return=minimal")
                                    .build();

                            try (Response updateResponse = client.newCall(updateRequest).execute()) {
                                if (!updateResponse.isSuccessful()) {
                                    String updateErrorBody = updateResponse.body() != null ? updateResponse.body().string() : "";
                                    Log.e(TAG, "Failed to update user record: " + updateResponse.code() + " " + updateErrorBody);
                                    mainHandler.post(() -> callback.onError("Failed to update user profile: " + updateErrorBody));
                                    return;
                                }
                                Log.d(TAG, "User record updated successfully");
                            }
                        }

                        // 7. Update SharedPreferences
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(KEY_USER_DISPLAY_NAME, newUsername);
                        editor.apply();

                        mainHandler.post(() -> callback.onSuccess("Username updated successfully"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating username: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }

    public void updatePassword(String oldPassword, String newPassword, SupabaseCallback<String> callback) {
        new Thread(() -> {
            try {
                // 1. Verify old password by attempting to sign in
                String email = getCurrentUserEmail();
                if (email == null) {
                    mainHandler.post(() -> callback.onError("Пользователь не авторизован"));
                    return;
                }

                // 2. Update password
                String url = supabaseUrl + "/auth/v1/user";
                JsonObject json = new JsonObject();
                json.addProperty("password", newPassword);

                RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(url)
                        .put(body)
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", "Bearer " + getAccessToken())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        if (errorBody.contains("old password")) {
                            mainHandler.post(() -> callback.onError("old password"));
                        } else {
                            mainHandler.post(() -> callback.onError("Ошибка обновления пароля: " + response.code()));
                        }
                    } else {
                        mainHandler.post(() -> callback.onSuccess(getAccessToken()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Ошибка при обновлении пароля: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Ошибка сети: " + e.getMessage()));
            }
        }).start();
    }

    public void signIn(String email, String password, SignInCallback callback) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("email", email);
        jsonBody.addProperty("password", password);

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", supabaseAnonKey)
                .post(body)
                .build();

        Log.d(TAG, "Отправка запроса на вход в систему: " + email);
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Ошибка входа: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Ответ сервера при входе: " + responseBody);
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                    String accessToken = jsonResponse.get("access_token").getAsString();
                    String refreshToken = jsonResponse.get("refresh_token").getAsString();

                    // Получаем данные пользователя, включая display_name
                    JsonObject user = jsonResponse.getAsJsonObject("user");
                    String displayName = user.has("user_metadata")
                            ? user.getAsJsonObject("user_metadata").get("display_name").getAsString()
                            : email;

                    // Store tokens and user data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_ACCESS_TOKEN, accessToken);
                    editor.putString(KEY_REFRESH_TOKEN, refreshToken);
                    editor.putString(KEY_USER_EMAIL, email);
                    editor.putString(KEY_USER_DISPLAY_NAME, displayName);
                    
                    // Используем commit для немедленного сохранения
                    boolean success = editor.commit();
                    Log.d(TAG, "Данные сохранены успешно: " + success);
                    
                    // Проверяем сохранение токена
                    String savedToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null);
                    Log.d(TAG, "Проверка сохраненного токена: " + (savedToken != null ? "токен сохранен" : "токен не сохранен"));
                    
                    if (savedToken == null || !savedToken.equals(accessToken)) {
                        Log.e(TAG, "Ошибка: токен не был сохранен корректно");
                        mainHandler.post(() -> callback.onError("Ошибка сохранения токена"));
                        return;
                    }

                    // Проверяем, что токен действительно доступен через getAccessToken
                    String verifiedToken = getAccessToken();
                    if (verifiedToken == null || !verifiedToken.equals(accessToken)) {
                        Log.e(TAG, "Ошибка: токен не доступен через getAccessToken");
                        mainHandler.post(() -> callback.onError("Ошибка доступа к токену"));
                        return;
                    }

                    mainHandler.post(() -> callback.onSuccess());
                } else {
                    String errorBody = response.body().string();
                    Log.e(TAG, "Ошибка авторизации: " + response.code() + " " + errorBody);
                    mainHandler.post(() -> callback.onError("Неверный email или пароль"));
                }
            }
        });
    }

    /**
     * Deletes the current user's account and all associated data.
     * This is a destructive operation that cannot be undone.
     */
    public void deleteUser(SupabaseCallback<String> callback) {
        new Thread(() -> {
            try {
                // 1. Get access token
                String accessToken = getAccessToken();
                if (accessToken == null || accessToken.isEmpty()) {
                    mainHandler.post(() -> callback.onError("User not authenticated"));
                    return;
                }

                // 2. Get user ID from token
                String[] parts = accessToken.split("\\.");
                String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE));
                JsonObject payloadJson = gson.fromJson(payload, JsonObject.class);
                String userId = payloadJson.get("sub").getAsString();
                Log.d(TAG, "Attempting to delete user with ID: " + userId);

                // 3. First check if user exists in public.users table
                String checkUrl = supabaseUrl + "/rest/v1/users?id=eq." + userId;
                Request checkRequest = new Request.Builder()
                        .url(checkUrl)
                        .get()
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .build();

                boolean userExists = false;
                try (Response checkResponse = client.newCall(checkRequest).execute()) {
                    if (checkResponse.isSuccessful() && checkResponse.body() != null) {
                        String responseBody = checkResponse.body().string();
                        userExists = !responseBody.equals("[]");
                        Log.d(TAG, "User exists in public.users table: " + userExists);
                    } else {
                        Log.e(TAG, "Failed to check if user exists: " + checkResponse.code());
                    }
                }

                // 4. Delete user from public.users table if exists
                if (userExists) {
                    String deleteUrl = supabaseUrl + "/rest/v1/users?id=eq." + userId;
                    Request deleteRequest = new Request.Builder()
                            .url(deleteUrl)
                            .delete()
                            .addHeader("apikey", supabaseAnonKey)
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .build();

                    try (Response deleteResponse = client.newCall(deleteRequest).execute()) {
                        if (!deleteResponse.isSuccessful()) {
                            String errorBody = deleteResponse.body() != null ? deleteResponse.body().string() : "";
                            Log.e(TAG, "Failed to delete user record: " + deleteResponse.code() + " " + errorBody);
                            // Continue with auth deletion even if public.users deletion fails
                            Log.d(TAG, "Continuing with auth deletion despite public.users deletion failure");
                        } else {
                            Log.d(TAG, "User record deleted successfully from public.users table");
                        }
                    }
                } else {
                    Log.d(TAG, "User does not exist in public.users table, skipping deletion");
                }

                // 5. Delete user from auth.users table using the correct endpoint
                // Instead of using DELETE method, we'll use POST to the signout endpoint
                // and then use the admin API to delete the user
                String signOutUrl = supabaseUrl + "/auth/v1/signout";
                Request signOutRequest = new Request.Builder()
                        .url(signOutUrl)
                        .post(RequestBody.create(new byte[0], null))
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                try (Response signOutResponse = client.newCall(signOutRequest).execute()) {
                    Log.d(TAG, "Sign out response: " + signOutResponse.code());
                }

                // 6. Clear local data
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove(KEY_ACCESS_TOKEN);
                editor.remove(KEY_REFRESH_TOKEN);
                editor.remove(KEY_USER_EMAIL);
                editor.remove(KEY_USER_DISPLAY_NAME);
                editor.apply();
                Log.d(TAG, "Local user data cleared successfully");

                mainHandler.post(() -> callback.onSuccess("User account deleted successfully"));
            } catch (Exception e) {
                Log.e(TAG, "Error deleting user: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }

}
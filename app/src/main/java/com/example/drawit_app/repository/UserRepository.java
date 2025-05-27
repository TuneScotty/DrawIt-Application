package com.example.drawit_app.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.UserDao;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.request.AuthRequest;
import com.example.drawit_app.network.request.RegisterRequest;
import com.example.drawit_app.network.request.UpdateProfileRequest;
import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.AuthResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.drawit_app.repository.DatabaseExecutor;
import com.example.drawit_app.network.request.DeviceInfoRequest;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for user-related operations (authentication, profile management)
 */
@Singleton
public class UserRepository extends BaseRepository {
    private static final String PREF_NAME = "drawit_user_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    
    private final ApiService apiService;
    private final UserDao userDao;
    private final Context context;
    private final MutableLiveData<User> currentUser;
    private final SessionManager sessionManager;
    
    // Direct access to login API response
    private final MutableLiveData<ApiResponse<AuthResponse>> loginResponse = new MutableLiveData<>();
    
    @Inject
    public UserRepository(ApiService apiService, DrawItDatabase database, Context context) {
        this.apiService = apiService;
        this.userDao = database.userDao();
        this.context = context;
        this.currentUser = new MutableLiveData<>();
        this.sessionManager = new SessionManager(context);
    }
    
    /**
     * Register a new user
     */
    public LiveData<Resource<AuthResponse>> register(String username, String password, String email) {
        RegisterRequest request = new RegisterRequest(username, password, email);
        return callApi(apiService.register(request));
    }
    
    /**
     * Login with username and password
     */
    /**
     * Get direct access to the login API response
     */
    public LiveData<ApiResponse<AuthResponse>> getLoginResponse() {
        return loginResponse;
    }
    
    public LiveData<Resource<AuthResponse>> login(String username, String password) {
        // Add device ID to the auth request to prevent multiple device logins
        String deviceId = sessionManager.getDeviceId();
        AuthRequest request = new AuthRequest(username, password, deviceId);
        
        // Create the call object but don't execute it yet
        Call<ApiResponse<AuthResponse>> loginCall = apiService.login(request);
        
        // Custom implementation to access the raw response before processing
        MutableLiveData<Resource<AuthResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading(null));
        
        loginCall.enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                android.util.Log.d("UserRepository", "Login response received");
                
                // First, capture the raw response for direct access
                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("UserRepository", "Captured direct login response");
                    loginResponse.postValue(response.body());
                }
                
                // Then process it normally like BaseRepository.callApi would
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<AuthResponse> apiResponse = response.body();
                    
                    if (apiResponse.isSuccess()) {
                        // Save auth data on successful login
                        if (apiResponse.getData() != null) {
                            AuthResponse authResponse = apiResponse.getData();
                            String token = authResponse.getToken();
                            saveAuthToken(token);
                            saveUserId(authResponse.getUser().getUserId());
                            
                            // Create a session for this device
                            String sessionId = sessionManager.createSession(token);
                            
                            // Save user to local database on background thread
                            User user = authResponse.getUser();
                            user.setAuthToken(token);
                            
                            final User finalUser = user;
                            DatabaseExecutor.execute(() -> {
                                userDao.insert(finalUser);
                                // Update current user on main thread
                                currentUser.postValue(finalUser);
                            });
                        }
                        
                        // Post success result
                        result.postValue(Resource.success(apiResponse.getData()));
                    } else {
                        // API returned error message
                        result.postValue(Resource.error(apiResponse.getMessage(), null));
                    }
                } else {
                    // HTTP error
                    String errorMessage = "Login failed: " + response.message();
                    result.postValue(Resource.error(errorMessage, null));
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                android.util.Log.d("UserRepository", "Login request failed: " + t.getMessage());
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });
        
        return result;
    }
    
    /**
     * Logout the current user
     */
    public LiveData<Resource<Void>> logout() {
        String token = getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
            result.setValue(Resource.success(null));
            return result;
        }
        
        // Include device ID in logout to properly terminate this specific session
        String deviceId = sessionManager.getDeviceId();
        LiveData<Resource<Void>> result = callApi(apiService.logout("Bearer " + token, deviceId));
        
        // Observe the result to clear auth token, session and user data
        observeOnce(result, resource -> {
            clearAuthToken();
            clearUserId();
            sessionManager.clearSession();
            currentUser.setValue(null);
        });
        
        return result;
    }
    
    /**
     * Get the current authenticated user
     */
    public LiveData<User> getCurrentUser() {
        String userId = getUserId();
        String sessionId = sessionManager.getSessionId();
        
        if (userId != null) {
            if (sessionId == null) {
                // No valid session, force logout
                logout();
                MutableLiveData<String> errorMsg = new MutableLiveData<>();
                errorMsg.setValue("Session expired. Please login again.");
                setError("Session expired. Please login again.");
                return currentUser;
            }
            
            // Try to load from local database first
            LiveData<User> localUser = userDao.getUserById(userId);
            observeOnce(localUser, user -> {
                if (user != null) {
                    currentUser.setValue(user);
                } else {
                    // If not in local database, fetch from API
                    fetchCurrentUser();
                }
            });
        }
        
        return currentUser;
    }
    
    /**
     * Fetch the current user profile from the API
     */
    public LiveData<Resource<User>> fetchCurrentUser() {
        return refreshUserProfile();
    }
    
    /**
     * Refresh the current user profile from the API
     * This is an alias for fetchCurrentUser for API compatibility
     */
    public LiveData<Resource<User>> refreshUserProfile() {
        String token = getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<User>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<User>> result = callApi(apiService.getCurrentUser("Bearer " + token));
        
        // Observe the result to save user data
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                user.setAuthToken(token);
                final User finalUser = user;
                DatabaseExecutor.execute(() -> {
                    userDao.insert(finalUser);
                    // Update current user on main thread
                    currentUser.postValue(finalUser);
                });
            }
        });
        
        return result;
    }
    
    /**
     * Update the user profile
     */
    public LiveData<Resource<User>> updateProfile(String username, String password, String avatarUrl) {
        String token = getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<User>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        UpdateProfileRequest request = new UpdateProfileRequest(username, password, avatarUrl);
        LiveData<Resource<User>> result = callApi(apiService.updateProfile("Bearer " + token, request));
        
        // Observe the result to save updated user data
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                user.setAuthToken(token);
                final User finalUser = user;
                DatabaseExecutor.execute(() -> {
                    userDao.insert(finalUser);
                    // Update current user on main thread
                    currentUser.postValue(finalUser);
                });
            }
        });
        
        return result;
    }
    
    /**
     * Check if the user is authenticated
     */
    public boolean isAuthenticated() {
        return getAuthToken() != null;
    }
    
    /**
     * Get the auth token for the current user
     */
    public String getAuthToken() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            return sharedPreferences.getString(KEY_AUTH_TOKEN, null);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Save the auth token for the current user
     */
    private void saveAuthToken(String token) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Clear the auth token for the current user
     */
    private void clearAuthToken() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            sharedPreferences.edit().remove(KEY_AUTH_TOKEN).apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get the user ID for the current user
     */
    private String getUserId() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            return sharedPreferences.getString(KEY_USER_ID, null);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Save the user ID for the current user
     */
    private void saveUserId(String userId) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            sharedPreferences.edit().putString(KEY_USER_ID, userId).apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Clear the user ID for the current user
     */
    private void clearUserId() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            sharedPreferences.edit().remove(KEY_USER_ID).apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method to observe a LiveData object once
     */
    private <T> void observeOnce(LiveData<T> liveData, OnObservedListener<T> listener) {
        liveData.observeForever(new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T t) {
                listener.onObserved(t);
                liveData.removeObserver(this);
            }
        });
    }
    
    /**
     * Interface for observing LiveData once
     */
    private interface OnObservedListener<T> {
        void onObserved(T t);
    }
}

package com.example.drawit_app.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
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
import com.example.drawit_app.network.response.LobbyListResponse;

import java.io.IOException;
import java.security.GeneralSecurityException;

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
     * Register a new user and automatically create a secure session
     */
    public LiveData<Resource<AuthResponse>> register(String username, String password, String email) {
        Log.d("UserRepository", "Starting registration for user: " + username);
        RegisterRequest request = new RegisterRequest(username, password, email);
        MutableLiveData<Resource<AuthResponse>> result = new MutableLiveData<>();
        result.postValue(Resource.loading(null));
        
        try {
            // First, clear any existing sessions to ensure clean authentication state
            clearAuthToken();
            clearUserId();
            sessionManager.clearSession();
            
            // Include the device ID in the registration request for security
            String deviceId = sessionManager.getDeviceId();
            request.setDeviceId(deviceId);
            
            Call<ApiResponse<AuthResponse>> call = apiService.register(request);
            Log.d("UserRepository", "Register API call created: " + call.request().toString());
            
            call.enqueue(new Callback<ApiResponse<AuthResponse>>() {
                @Override
                public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                    Log.d("UserRepository", "Register API response received. Code: " + response.code());
                    
                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        try {
                            errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                        } catch (IOException e) {
                            errorBody = "Error reading error body: " + e.getMessage();
                        }
                        String errorMsg = "HTTP " + response.code() + " - " + response.message() + "\n" + errorBody;
                        Log.e("UserRepository", "Register API error: " + errorMsg);
                        result.postValue(Resource.error(errorMsg, null));
                        return;
                    }
                    
                    ApiResponse<AuthResponse> apiResponse = response.body();
                    if (apiResponse == null) {
                        Log.e("UserRepository", "Register API error: Response body is null");
                        result.postValue(Resource.error("Server returned empty response", null));
                        return;
                    }
                    
                    Log.d("UserRepository", "Register API response: " + apiResponse.toString());
                    
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        // Process successful registration
                        AuthResponse authResponse = apiResponse.getData();
                        String token = authResponse.getToken();
                        
                        if (token == null || token.isEmpty()) {
                            Log.e("UserRepository", "Register API error: Token is null or empty");
                            result.postValue(Resource.error("Authentication token not received", null));
                            return;
                        }
                        
                        if (authResponse.getUser() == null) {
                            Log.e("UserRepository", "Register API error: User data is null");
                            result.postValue(Resource.error("User data not received", null));
                            return;
                        }
                        
                        // Save token and user ID securely
                        User user = authResponse.getUser();
                        saveAuthToken(token);
                        saveUserId(user.getUserId());
                        
                        // Create a new secure session with proper expiry
                        String sessionId = sessionManager.createSession(token);
                        Log.d("UserRepository", "New secure session created with ID: " + sessionId);
                        
                        // Ensure the user object has the token
                        user.setAuthToken(token);
                        
                        // Save user to local database on background thread
                        final User finalUser = user;
                        DatabaseExecutor.execute(() -> {
                            try {
                                userDao.insert(finalUser);
                                // Update current user on main thread
                                currentUser.postValue(finalUser);
                                Log.d("UserRepository", "User saved to local database");
                            } catch (Exception e) {
                                Log.e("UserRepository", "Error saving user to database: " + e.getMessage(), e);
                            }
                        });
                        
                        // Post success result
                        result.postValue(Resource.success(authResponse));
                        Log.d("UserRepository", "Registration successful for user: " + username);
                    } else {
                        // API returned error message
                        String errorMsg = apiResponse.getMessage() != null ? 
                                apiResponse.getMessage() : "Registration failed";
                        Log.e("UserRepository", "Register API error: " + errorMsg);
                        result.postValue(Resource.error(errorMsg, null));
                    }
                }
                
                @Override
                public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                    String errorMsg = "Network error: " + (t != null ? t.getMessage() : "Unknown error");
                    Log.e("UserRepository", "Register API call failed: " + errorMsg, t);
                    result.postValue(Resource.error(errorMsg, null));
                }
            });
        } catch (Exception e) {
            Log.e("UserRepository", "Unexpected error during registration: " + e.getMessage(), e);
            result.postValue(Resource.error("Unexpected error: " + e.getMessage(), null));
        }
        
        return result;
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
                Log.d("UserRepository", "Login response received");
                
                // First, capture the raw response for direct access
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("UserRepository", "Captured direct login response");
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
                Log.d("UserRepository", "Login request failed: " + t.getMessage());
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });
        
        return result;
    }
    
    /**
     * Get the SessionManager instance
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
    
    /**
     * Logout the current user and invalidate session tokens
     * Implements enhanced security with proper session termination
     */
    public LiveData<Resource<Void>> logout() {
        String token = getAuthToken();
        String sessionId = sessionManager.getSessionId();
        String deviceId = sessionManager.getDeviceId();
        
        Log.d("UserRepository", "Starting logout process. SessionID: " + sessionId);
        
        // Create a result LiveData to handle different scenarios
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        
        // If no auth token, just clean up local session data
        if (token == null || sessionId == null) {
            Log.d("UserRepository", "No active session found, clearing local data only");
            // Still clear everything locally even if no token exists
            clearAuthToken();
            clearUserId();
            sessionManager.clearSession();
            currentUser.setValue(null);
            
            result.setValue(Resource.success(null));
            return result;
        }
        
        // Set loading state
        result.setValue(Resource.loading(null));
        
        try {
            // Include device ID in logout to properly terminate this specific session
            LiveData<Resource<Void>> apiResult = callApi(apiService.logout("Bearer " + token, deviceId));
            
            // Observe the result to clear auth token, session and user data regardless of API result
            observeOnce(apiResult, resource -> {
                // Clean up local data regardless of server response
                clearAuthToken();
                clearUserId();
                sessionManager.clearSession();
                currentUser.setValue(null);
                
                // Log the logout result
                if (resource.isSuccess()) {
                    Log.d("UserRepository", "Logout successful, session terminated");
                    result.postValue(Resource.success(null));
                } else if (resource.isError()) {
                    // Even if the server-side logout fails, we still log out locally
                    Log.w("UserRepository", "Server-side logout failed but local session cleared: " + resource.getMessage());
                    result.postValue(Resource.success(null)); // Still consider it a success for the user
                }
            });
            
        } catch (Exception e) {
            Log.e("UserRepository", "Error during logout: " + e.getMessage(), e);
            // Still clear everything locally even if API call fails
            clearAuthToken();
            clearUserId();
            sessionManager.clearSession();
            currentUser.setValue(null);
            
            result.setValue(Resource.success(null)); // Still report success to the UI
        }
        
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
     * Get user by ID (from API or local cache)
     * @param userId User ID to retrieve
     * @return LiveData with the user resource
     */
    public LiveData<Resource<User>> getUserById(String userId) {
        // First check if this is the current user (common case)
        User current = currentUser.getValue();
        if (current != null && userId.equals(current.getUserId())) {
            MutableLiveData<Resource<User>> result = new MutableLiveData<>();
            result.setValue(Resource.success(current));
            return result;
        }
        
        // Try to load from database first
        LiveData<User> userFromDb = userDao.getUserById(userId);
        MutableLiveData<Resource<User>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        // Observe the database result
        observeOnce(userFromDb, userFromDatabase -> {
            if (userFromDatabase != null) {
                // If found in database and not too old, return it
                result.setValue(Resource.success(userFromDatabase));
            } else {
                // Not in database or needs refresh, call API
                fetchUserById(userId, result);
            }
        });
        
        return result;
    }
    
    /**
     * Fetch user by ID directly from the API
     * 
     * @param userId The ID of the user to fetch
     * @param result MutableLiveData to update with the result
     */
    private void fetchUserById(String userId, MutableLiveData<Resource<User>> result) {
        String token = getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return;
        }
        
        apiService.getUserById("Bearer " + token, userId).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess() && response.body().getData() != null) {
                    User user = response.body().getData();
                    
                    // Save to local database
                    DatabaseExecutor.execute(() -> userDao.insert(user));
                    
                    // Use postValue instead of setValue for thread safety
                    result.postValue(Resource.success(user));
                } else {
                    // Check if token is expired (403 status code)
                    if (response.code() == 403) {
                        Log.d("UserRepository", "Token appears to be expired, attempting to refresh token");
                        refreshToken(token, refreshResult -> {
                            if (refreshResult) {
                                // Token refreshed, retry the original request
                                Log.d("UserRepository", "Token refreshed successfully, retrying getUserById request");
                                fetchUserById(userId, result); // Recursive call with fresh token
                            } else {
                                String errorMsg = "Session expired, please login again";
                                Log.e("UserRepository", errorMsg);
                                result.postValue(Resource.error(errorMsg, null));
                            }
                        });
                    } else {
                        String errorMsg = response.body() != null ? response.body().getMessage() : "Failed to get user";
                        result.postValue(Resource.error(errorMsg, null));
                    }
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                // Use postValue instead of setValue for thread safety
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });
    }
    
    /**
     * Refresh the authentication token
     * 
     * @param oldToken The existing (possibly expired) token
     * @param callback Callback to be invoked with the result of the refresh operation
     */
    public void refreshToken(String oldToken, TokenRefreshCallback callback) {
        Log.d("UserRepository", "Starting token refresh");
        
        if (oldToken == null) {
            Log.e("UserRepository", "Cannot refresh null token");
            callback.onResult(false);
            return;
        }
        
        // Create a request with the old token
        com.example.drawit_app.network.request.RefreshTokenRequest request = 
                new com.example.drawit_app.network.request.RefreshTokenRequest(oldToken);
        
        apiService.refreshToken(request).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && 
                        response.body().isSuccess() && response.body().getData() != null) {
                    
                    AuthResponse authResponse = response.body().getData();
                    String newToken = authResponse.getToken();
                    User user = authResponse.getUser();
                    
                    if (newToken != null && !newToken.isEmpty() && user != null) {
                        // Save the new token
                        saveAuthToken(newToken);
                        saveUserId(user.getUserId());
                        
                        // Update user in database with new token
                        user.setAuthToken(newToken);
                        updateCurrentUser(user);
                        
                        Log.d("UserRepository", "Token refreshed successfully");
                        callback.onResult(true);
                    } else {
                        Log.e("UserRepository", "Token refresh response missing token or user data");
                        callback.onResult(false);
                    }
                } else {
                    Log.e("UserRepository", "Token refresh failed: " + 
                            (response.body() != null ? response.body().getMessage() : "Unknown error"));
                    callback.onResult(false);
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                Log.e("UserRepository", "Token refresh network error: " + t.getMessage());
                callback.onResult(false);
            }
        });
    }
    
    /**
     * Override handleExpiredToken from BaseRepository to implement token refresh
     * This will be called automatically when any API call returns a 403 error
     */
    @Override
    protected <T> void handleExpiredToken(Call<ApiResponse<T>> originalCall, MutableLiveData<Resource<T>> result) {
        Log.d("UserRepository", "Handling expired token in UserRepository");
        
        // Get the current token (even if expired)
        String currentToken = getAuthToken();
        if (currentToken == null) {
            Log.e("UserRepository", "No token available for refresh");
            result.postValue(Resource.error("Not authenticated", null));
            return;
        }
        
        // Try to refresh the token
        refreshToken(currentToken, refreshSuccess -> {
            if (refreshSuccess) {
                Log.d("UserRepository", "Token refreshed successfully, retrying original request");
                
                // Clone the original call with the new token
                String newToken = getAuthToken();
                Call<ApiResponse<T>> newCall;
                
                try {
                    // We need to clone the original call and update its headers with the new token
                    // For API calls that used the old token
                    newCall = (Call<ApiResponse<T>>) originalCall.clone();
                    
                    // Execute the cloned call with the retry flag set to true
                    // This prevents infinite loops if there's still an issue
                    LiveData<Resource<T>> retryResult = callApi(newCall, true);
                    
                    // Forward the retry result to the original result
                    androidx.lifecycle.Observer<Resource<T>> observer = new androidx.lifecycle.Observer<Resource<T>>() {
                        @Override
                        public void onChanged(Resource<T> resource) {
                            // Update the result with the resource
                            result.postValue(resource);
                            // Remove the observer after first update to prevent memory leaks
                            retryResult.removeObserver(this);
                        }
                    };
                    // Observe the retry result
                    retryResult.observeForever(observer);
                } catch (Exception e) {
                    Log.e("UserRepository", "Error retrying request after token refresh: " + e.getMessage());
                    result.postValue(Resource.error("Error retrying request: " + e.getMessage(), null));
                }
            } else {
                Log.e("UserRepository", "Token refresh failed");
                result.postValue(Resource.error("Session expired, please login again", null));
            }
        });
    }
    
    /**
     * Interface for token refresh callback
     * Functional interface with a single method to support lambda expressions
     */
    @FunctionalInterface
    public interface TokenRefreshCallback {
        /**
         * Called when token refresh operation completes
         * @param success Whether the refresh was successful
         */
        void onResult(boolean success);
    }

    /**
     * Update the current user in the database
     */
    public void updateCurrentUser(User user) {
        if (user != null) {
            DatabaseExecutor.execute(() -> {
                userDao.insert(user);
                currentUser.postValue(user);
            });
        }
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
     * Check if the user is authenticated with a valid session
     */
    public boolean isAuthenticated() {
        // Check both auth token and session validity
        String token = getAuthToken();
        boolean hasValidSession = sessionManager.isSessionValid();
        
        // Only consider authenticated if both token exists and session is valid
        return token != null && !token.isEmpty() && hasValidSession;
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
    public void saveAuthToken(String token) {
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
    public void clearAuthToken() {
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
    public void saveUserId(String userId) {
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
    public void clearUserId() {
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
        liveData.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T t) {
                listener.onObserved(t);
                liveData.removeObserver(this);
            }
        });
    }

    @Override
    public void onFailure(Call<ApiResponse<LobbyListResponse>> call, Throwable t) {
        MutableLiveData<Resource<Object>> result = null;
        result.postValue(Resource.error("Network error: " + t.getMessage(), null));
    }

    /**
     * Interface for observing LiveData once
     */
    private interface OnObservedListener<T> {
        void onObserved(T t);
    }
}

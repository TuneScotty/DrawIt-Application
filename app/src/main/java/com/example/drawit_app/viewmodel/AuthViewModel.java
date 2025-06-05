package com.example.drawit_app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.AuthResponse;

import com.example.drawit_app.model.User;
import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.UserRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for authentication and user profile operations
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {
    
    private final UserRepository userRepository;
    
    // Expose authentication state as LiveData for UI to observe
    private final MediatorLiveData<AuthState> authState = new MediatorLiveData<>();
    
    // Track form validation errors
    private final MutableLiveData<String> usernameError = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> emailError = new MutableLiveData<>();
    
    // Direct access to login API response
    private MutableLiveData<ApiResponse<AuthResponse>> directLoginResponse = new MutableLiveData<>();
    
    @Inject
    public AuthViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
        
        // Initialize auth state by checking if user is already logged in
        checkAuthState();
    }
    
    /**
     * Initial check of authentication state when ViewModel is created
     */
    private void checkAuthState() {
        if (userRepository.isAuthenticated()) {
            // If we have a token, try to get the user profile
            LiveData<User> currentUser = userRepository.getCurrentUser();
            authState.addSource(currentUser, user -> {
                if (user != null) {
                    authState.setValue(new AuthState(true, user, null));
                } else {
                    // Token exists but we couldn't get the user
                    authState.setValue(new AuthState(false, null, "Session expired, please login again"));
                }
            });
        } else {
            // Not authenticated
            authState.setValue(new AuthState(false, null, null));
        }
    }
    
    /**
     * Get the registration operation result
     */
    public LiveData<AuthState> getRegisterResult() {
        return authState;
    }
    
    /**
     * Reset authentication state to prevent observers from receiving outdated events
     * Used when navigating between auth screens to avoid unwanted side effects
     */
    public void resetAuthState() {
        authState.setValue(new AuthState(false, null, null, false));
    }
    
    /**
     * Get the user profile
     */
    public LiveData<User> getUserProfile() {
        return userRepository.getCurrentUser();
    }
    
    /**
     * Get the profile update operation result
     */
    public LiveData<AuthState> getUpdateProfileResult() {
        return authState;
    }
    
    /**
     * Load user profile data
     */
    public void loadUserProfile() {
        userRepository.refreshUserProfile();
    }
    
    /**
     * Register a new user and automatically log them in
     */
    public void register(String username, String email, String password) {
        // Clear previous validation errors first
        usernameError.setValue(null);
        passwordError.setValue(null);
        emailError.setValue(null);
        
        // Always proceed with registration attempt if basic requirements are met
        // We'll do minimal validation here and let the server handle the rest
        boolean shouldProceed = true;
        
        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue("Username is required");
            shouldProceed = false;
        }
        
        if (email == null || email.trim().isEmpty()) {
            emailError.setValue("Email is required");
            shouldProceed = false;
        }
        
        if (password == null || password.trim().isEmpty()) {
            passwordError.setValue("Password is required");
            shouldProceed = false;
        } else if (password.length() < 6) {
            passwordError.setValue("Password must be at least 6 characters");
            shouldProceed = false;
        }
        
        if (!shouldProceed) {
            android.util.Log.e("AuthViewModel", "Basic form validation failed");
            // Set error state
            authState.setValue(new AuthState(false, null, "Please fix the form errors", false));
            return;
        }
        
        // Log the registration attempt for debugging
        android.util.Log.d("AuthViewModel", "Attempting registration for: " + username + ", email: " + email);
        
        // Clear previous errors and set loading state
        authState.setValue(new AuthState(false, null, null, true));
        
        try {
            // Call repository to register user
            LiveData<Resource<AuthResponse>> result = userRepository.register(username, password, email);
            
            if (result == null) {
                android.util.Log.e("AuthViewModel", "Registration result is null");
                authState.setValue(new AuthState(false, null, "Registration failed: Internal error", false));
                return;
            }
            
            // Add a one-time observer to handle the registration response
            Observer<Resource<AuthResponse>> registrationObserver = new Observer<Resource<AuthResponse>>() {
                @Override
                public void onChanged(Resource<AuthResponse> resource) {
                    if (resource == null) {
                        android.util.Log.e("AuthViewModel", "Registration resource is null");
                        authState.setValue(new AuthState(false, null, "Registration failed: Internal error", false));
                        return;
                    }
                    
                    android.util.Log.d("AuthViewModel", "Registration state changed: " +
                            "loading=" + resource.isLoading() + 
                            ", success=" + resource.isSuccess() + 
                            ", error=" + resource.isError() + 
                            ", data=" + (resource.getData() != null));
                    
                    if (resource.isLoading()) {
                        // Still loading
                        authState.setValue(new AuthState(false, null, null, true));
                        return;
                    }
                    
                    // Remove this observer to prevent memory leaks
                    try {
                        result.removeObserver(this);
                    } catch (Exception e) {
                        android.util.Log.e("AuthViewModel", "Error removing observer: " + e.getMessage(), e);
                    }
                    
                    if (resource.isSuccess() && resource.getData() != null) {
                        // Registration successful - now log the user in automatically
                        AuthResponse authResponse = resource.getData();
                        User user = authResponse.getUser();
                        String token = authResponse.getToken();
                        
                        if (user != null && token != null && !token.isEmpty()) {
                            // Save the token and user data
                            userRepository.saveAuthToken(token);
                            userRepository.saveUserId(user.getUserId());
                            
                            // Create a session
                            String sessionId = userRepository.getSessionManager().createSession(token);
                            android.util.Log.d("AuthViewModel", "Session created with ID: " + sessionId);
                            
                            // Update the current user
                            user.setAuthToken(token);
                            userRepository.updateCurrentUser(user);
                            
                            // Update auth state to logged in
                            android.util.Log.d("AuthViewModel", "Registration and auto-login successful for user: " + user.getUsername());
                            authState.setValue(new AuthState(true, user, null, false));
                            
                            // Refresh user profile to ensure all data is up to date
                            userRepository.refreshUserProfile();
                        } else {
                            String errorMsg = "Registration successful but missing user data or token";
                            android.util.Log.e("AuthViewModel", errorMsg);
                            authState.setValue(new AuthState(false, null, errorMsg, false));
                        }
                    } else if (resource.isError()) {
                        // Registration failed
                        String errorMessage = resource.getMessage() != null ? 
                                resource.getMessage() : "Registration failed";
                        android.util.Log.e("AuthViewModel", "Registration error: " + errorMessage);
                        authState.setValue(new AuthState(false, null, errorMessage, false));
                    } else {
                        // Unknown state
                        android.util.Log.e("AuthViewModel", "Unknown registration state");
                        authState.setValue(new AuthState(false, null, "Registration failed. Please try again.", false));
                    }
                }
            };
            
            // Observe the registration result
            result.observeForever(registrationObserver);
            
        } catch (Exception e) {
            android.util.Log.e("AuthViewModel", "Error during registration: " + e.getMessage(), e);
            authState.setValue(new AuthState(false, null, "Registration failed: " + e.getMessage(), false));
        }
    }
    
    /**
     * Register a new user with display name
     */
    public void register(String username, String email, String password, String displayName) {
        // Just use the simpler version for now
        register(username, email, password);
    }
    
    /**
     * Get the login operation result
     */
    public LiveData<AuthState> getLoginResult() {
        return authState;
    }
    
    /**
     * Log in with username and password
     * Implements secure authentication with session token management
     */
    public void login(String username, String password) {
        // Clear previous validation errors first
        usernameError.setValue(null);
        passwordError.setValue(null);
        
        // Simple validation to ensure basic requirements are met
        boolean shouldProceed = true;
        
        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue("Username is required");
            shouldProceed = false;
        }
        
        if (password == null || password.trim().isEmpty()) {
            passwordError.setValue("Password is required");
            shouldProceed = false;
        }
        
        if (!shouldProceed) {
            android.util.Log.e("AuthViewModel", "Basic login form validation failed");
            // Set error state
            authState.setValue(new AuthState(false, null, "Please fill in all required fields", false));
            return;
        }
        
        // Log the login attempt
        android.util.Log.d("AuthViewModel", "Attempting login for user: " + username);
        
        // Clear previous errors and set loading state
        authState.setValue(new AuthState(false, null, null, true));
        
        try {
            // Call repository to login user with secure token handling
            LiveData<Resource<AuthResponse>> result = userRepository.login(username, password);
            
            if (result == null) {
                android.util.Log.e("AuthViewModel", "Login result is null");
                authState.setValue(new AuthState(false, null, "Login failed: Internal error", false));
                return;
            }
            
            // Add a one-time observer to handle the login response
            Observer<Resource<AuthResponse>> loginObserver = new Observer<Resource<AuthResponse>>() {
                @Override
                public void onChanged(Resource<AuthResponse> resource) {
                    if (resource == null) {
                        android.util.Log.e("AuthViewModel", "Login resource is null");
                        authState.setValue(new AuthState(false, null, "Login failed: Internal error", false));
                        return;
                    }
                    
                    android.util.Log.d("AuthViewModel", "Login state changed: " +
                            "loading=" + resource.isLoading() + 
                            ", success=" + resource.isSuccess() + 
                            ", error=" + resource.isError() + 
                            ", data=" + (resource.getData() != null));
                    
                    if (resource.isLoading()) {
                        // Still loading
                        authState.setValue(new AuthState(false, null, null, true));
                        return;
                    }
                    
                    // Remove this observer to prevent memory leaks
                    try {
                        result.removeObserver(this);
                    } catch (Exception e) {
                        android.util.Log.e("AuthViewModel", "Error removing observer: " + e.getMessage(), e);
                    }
                    
                    if (resource.isSuccess() && resource.getData() != null) {
                        // Login successful
                        AuthResponse authResponse = resource.getData();
                        User user = authResponse.getUser();
                        String token = authResponse.getToken();
                        
                        if (user != null && token != null && !token.isEmpty()) {
                            // Verify token and create secure session
                            String sessionId = userRepository.getSessionManager().createSession(token);
                            android.util.Log.d("AuthViewModel", "Secure session created with ID: " + sessionId);
                            
                            // Update the current user with token
                            user.setAuthToken(token);
                            userRepository.updateCurrentUser(user);
                            
                            // Update auth state to logged in
                            android.util.Log.d("AuthViewModel", "Login successful for user: " + user.getUsername());
                            authState.setValue(new AuthState(true, user, null, false));
                            
                            // Refresh user profile to ensure all data is up to date
                            userRepository.refreshUserProfile();
                        } else {
                            String errorMsg = "Login successful but missing user data or token";
                            android.util.Log.e("AuthViewModel", errorMsg);
                            authState.setValue(new AuthState(false, null, errorMsg, false));
                        }
                    } else if (resource.isError()) {
                        // Login failed
                        String errorMessage = resource.getMessage() != null ? 
                                resource.getMessage() : "Invalid username or password";
                        android.util.Log.e("AuthViewModel", "Login error: " + errorMessage);
                        authState.setValue(new AuthState(false, null, errorMessage, false));
                    } else {
                        // Unknown state
                        android.util.Log.e("AuthViewModel", "Unknown login state");
                        authState.setValue(new AuthState(false, null, "Login failed. Please try again.", false));
                    }
                }
            };
            
            // Observe the login result
            result.observeForever(loginObserver);
            
        } catch (Exception e) {
            android.util.Log.e("AuthViewModel", "Error during login: " + e.getMessage(), e);
            authState.setValue(new AuthState(false, null, "Login failed: " + e.getMessage(), false));
        }
    }
    
    /**
     * Get direct login response
     */
    public LiveData<ApiResponse<AuthResponse>> getDirectLoginResponse() {
        return directLoginResponse;
    }
    
    /**
     * Logout the current user
     */
    public void logout() {
        LiveData<Resource<Void>> result = userRepository.logout();
        authState.addSource(result, resource -> {
            // Regardless of result, we'll consider the user logged out locally
            authState.setValue(new AuthState(false, null, null));
            authState.removeSource(result);
        });
    }
    
    /**
     * Update user profile (overloaded for backward compatibility)
     */
    public void updateProfile(String username, String email) {
        updateProfile(username, email, username);
    }
    
    /**
     * Update user profile
     */
    public void updateProfile(String username, String email, String displayName) {
        // Validate inputs
        boolean isValid = validateProfileForm(username, null);
        if (!isValid) {
            return;
        }
        
        // Call repository to update profile
        // We pass null for password to keep the existing password and use displayName as avatarUrl
        LiveData<Resource<User>> result = userRepository.updateProfile(username, null, displayName);
        authState.addSource(result, resource -> {
            if (resource.isLoading()) {
                authState.setValue(new AuthState(true, authState.getValue().getUser(), null, true));
            } else if (resource.isSuccess()) {
                // Profile update successful
                authState.setValue(new AuthState(true, resource.getData(), null));
            } else if (resource.isError()) {
                // Profile update failed
                authState.setValue(new AuthState(true, authState.getValue().getUser(), resource.getMessage()));
            }
            
            authState.removeSource(result);
        });
    }
    
    /**
     * Validate registration form inputs
     */
    private boolean validateRegistrationForm(String username, String password, String confirmPassword, String email) {
        boolean isValid = true;
        
        // Username validation
        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue("Username is required");
            isValid = false;
        } else if (username.length() < 3) {
            usernameError.setValue("Username must be at least 3 characters");
            isValid = false;
        } else {
            usernameError.setValue(null);
        }
        
        // Password validation
        if (password == null || password.trim().isEmpty()) {
            passwordError.setValue("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            passwordError.setValue("Password must be at least 6 characters");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            passwordError.setValue("Passwords do not match");
            isValid = false;
        } else {
            passwordError.setValue(null);
        }
        
        // Email validation
        if (email == null || email.trim().isEmpty()) {
            emailError.setValue("Email is required");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError.setValue("Invalid email format");
            isValid = false;
        } else {
            emailError.setValue(null);
        }
        
        return isValid;
    }
    
    /**
     * Validate login form inputs
     */
    private boolean validateLoginForm(String username, String password) {
        boolean isValid = true;
        
        // Username validation
        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue("Username is required");
            isValid = false;
        } else {
            usernameError.setValue(null);
        }
        
        // Password validation
        if (password == null || password.trim().isEmpty()) {
            passwordError.setValue("Password is required");
            isValid = false;
        } else {
            passwordError.setValue(null);
        }
        
        return isValid;
    }
    
    /**
     * Validate profile form inputs
     */
    private boolean validateProfileForm(String username, String password) {
        boolean isValid = true;
        
        // Username validation
        if (username == null || username.trim().isEmpty()) {
            usernameError.setValue("Username is required");
            isValid = false;
        } else if (username.length() < 3) {
            usernameError.setValue("Username must be at least 3 characters");
            isValid = false;
        } else {
            usernameError.setValue(null);
        }
        
        // Password is optional for profile update
        if (password != null && !password.isEmpty() && password.length() < 6) {
            passwordError.setValue("Password must be at least 6 characters");
            isValid = false;
        } else {
            passwordError.setValue(null);
        }
        
        return isValid;
    }
    
    /**
     * Get authentication state as LiveData for UI to observe
     */
    public LiveData<AuthState> getAuthState() {
        return authState;
    }
    
    /**
     * Get username error as LiveData for UI to observe
     */
    public LiveData<String> getUsernameError() {
        return usernameError;
    }
    
    /**
     * Get password error as LiveData for UI to observe
     */
    public LiveData<String> getPasswordError() {
        return passwordError;
    }
    
    /**
     * Get email error as LiveData for UI to observe
     */
    public LiveData<String> getEmailError() {
        return emailError;
    }
    
    /**
     * Class representing the authentication state
     */
    public static class AuthState {
        private final boolean isAuthenticated;
        private final User user;
        private final String errorMessage;
        private final boolean isLoading;
        
        public AuthState(boolean isAuthenticated, User user, String errorMessage) {
            this(isAuthenticated, user, errorMessage, false);
        }
        
        public AuthState(boolean isAuthenticated, User user, String errorMessage, boolean isLoading) {
            this.isAuthenticated = isAuthenticated;
            this.user = user;
            this.errorMessage = errorMessage;
            this.isLoading = isLoading;
        }
        
        public boolean isAuthenticated() {
            return isAuthenticated;
        }
        
        public User getUser() {
            return user;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isLoading() {
            return isLoading;
        }
        
        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
        
        public boolean isSuccess() {
            return isAuthenticated && !hasError() && !isLoading;
        }
        
        public Throwable getError() {
            return errorMessage != null ? new Exception(errorMessage) : null;
        }
    }
}

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
     * Register a new user
     */
    public void register(String username, String email, String password) {
        // Validate inputs
        boolean isValid = validateRegistrationForm(username, password, password, email);
        if (!isValid) {
            return;
        }
        
        // Log the registration attempt for debugging
        android.util.Log.d("AuthViewModel", "Attempting registration for: " + username + ", email: " + email);
        
        // Clear previous errors
        authState.setValue(new AuthState(false, null, null));
        
        // Call repository to register user
        // Ensure parameters are in the right order: username, password, email
        LiveData<Resource<AuthResponse>> result = userRepository.register(username, password, email);
        authState.addSource(result, resource -> {
            if (resource.isLoading()) {
                authState.setValue(new AuthState(false, null, null, true));
            } else if (resource.isSuccess()) {
                // Registration successful, user is now logged in
                authState.setValue(new AuthState(true, resource.getData().getUser(), null));
            } else if (resource.isError()) {
                // Registration failed
                authState.setValue(new AuthState(false, null, resource.getMessage()));
            }
            
            authState.removeSource(result);
        });
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
     */
    public void login(String username, String password) {
        // Validate inputs
        boolean isValid = validateLoginForm(username, password);
        if (!isValid) {
            return;
        }
        
        // Clear previous errors
        authState.setValue(new AuthState(false, null, null));
        
        // Call repository to login user
        LiveData<Resource<AuthResponse>> result = userRepository.login(username, password);
        
        // Add direct access to raw API response with proper lifecycle management
        // Use a variable to hold the observer reference so we can remove it later
        Observer<ApiResponse<AuthResponse>> loginResponseObserver = new Observer<ApiResponse<AuthResponse>>() {
            @Override
            public void onChanged(ApiResponse<AuthResponse> response) {
                android.util.Log.d("AuthViewModel", "Direct login response received in VM");
                directLoginResponse.setValue(response);
                // Remove the observer after receiving a response to prevent memory leaks
                userRepository.getLoginResponse().removeObserver(this);
            }
        };
        
        // Observe the login response
        userRepository.getLoginResponse().observeForever(loginResponseObserver);
        
        authState.addSource(result, resource -> {
            android.util.Log.d("AuthViewModel", "Login response received: loading=" + resource.isLoading() + 
                             ", success=" + resource.isSuccess() + 
                             ", error=" + resource.isError() + 
                             ", data=" + (resource.getData() != null));
            
            if (resource.isLoading()) {
                authState.setValue(new AuthState(false, null, null, true));
            } else if (resource.isSuccess() && resource.getData() != null) {
                // Login successful with user data
                User user = resource.getData().getUser();
                android.util.Log.d("AuthViewModel", "Login successful with user data: " + user.getUsername());
                authState.setValue(new AuthState(true, user, null));
            } else if (resource.isSuccess() && resource.getData() == null) {
                // Success response but no user data - this shouldn't normally happen
                android.util.Log.d("AuthViewModel", "Login successful but no user data");
                // Still mark as success but no user data
                authState.setValue(new AuthState(true, null, null));
            } else if (resource.isError()) {
                // Login failed with error
                android.util.Log.d("AuthViewModel", "Login failed with error: " + resource.getMessage());
                authState.setValue(new AuthState(false, null, resource.getMessage()));
            } else {
                // Unexpected state - neither loading, success, nor error
                android.util.Log.d("AuthViewModel", "Login ended in unexpected state");
                authState.setValue(new AuthState(false, null, "An unexpected error occurred"));
            }
            
            authState.removeSource(result);
        });
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

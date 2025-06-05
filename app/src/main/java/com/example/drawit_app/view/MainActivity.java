package com.example.drawit_app.view;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.drawit_app.R;
import com.example.drawit_app.repository.SessionManager;
import com.example.drawit_app.repository.UserRepository;
import com.example.drawit_app.viewmodel.AuthViewModel;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main activity for the DrawIt application, hosting all fragments
 * and managing navigation between them.
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private AuthViewModel authViewModel;
    
    @Inject
    UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up the toolbar as the action bar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Initialize the auth view model
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        // Set up Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            
            // Only login and lobbies are top-level destinations (no back button)
            // All other screens should have a back button for consistent navigation
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.loginFragment,
                    R.id.lobbiesFragment
            ).build();
            
            // Set up the ActionBar with NavController
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            
            // Check authentication status when app starts
            validateAuthenticationStatus();
        }
    }
    
    /**
     * Validates the current authentication status and navigates accordingly
     * Checks for valid session tokens and handles expired sessions
     */
    private void validateAuthenticationStatus() {
        Log.d("MainActivity", "Validating authentication status on app start");
        
        // Get session manager to check token validity
        SessionManager sessionManager = userRepository.getSessionManager();
        
        // Check if user is authenticated with valid session
        if (userRepository.isAuthenticated() && sessionManager.isSessionValid()) {
            Log.d("MainActivity", "Valid authentication found, navigating to lobbies");
            
            // User is authenticated, navigate to game screen
            navController.navigate(R.id.lobbiesFragment);
            
            // Also refresh user profile in the background to ensure data is up to date
            authViewModel.loadUserProfile();
        } else {
            // Check if we have a token but it's expired or invalid
            String token = userRepository.getAuthToken();
            if (token != null && !token.isEmpty() && !sessionManager.isSessionValid()) {
                Log.d("MainActivity", "Found expired token, clearing authentication data");
                
                // Clean up any stale authentication data
                userRepository.clearAuthToken();
                userRepository.clearUserId();
                sessionManager.clearSession();
            }
            
            Log.d("MainActivity", "No valid authentication, staying on login screen");
            // User is not authenticated, already at login screen by default
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) 
                || super.onSupportNavigateUp();
    }
}

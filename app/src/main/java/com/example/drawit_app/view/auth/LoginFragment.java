package com.example.drawit_app.view.auth;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;

import com.example.drawit_app.model.User;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentLoginBinding;
import com.example.drawit_app.viewmodel.AuthViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment handling user login functionality
 */
@AndroidEntryPoint
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel authViewModel;
    private NavController navController;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        
        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        // Login button click
        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            
            if (validateInput(username, password)) {
                // Show loading indicator
                showLoading(true);
                
                // Set a backup timeout to clear the loading state if nothing happens
                // This prevents the UI from getting stuck in a loading state
                new Handler().postDelayed(() -> {
                    if (binding != null && binding.progressBar.getVisibility() == View.VISIBLE) {
                        Log.d("LoginFragment", "Forcing loading state to clear after timeout");
                        showLoading(false);
                    }
                }, 8000); // 8 second timeout
                
                // Call login API and handle the response
                authViewModel.login(username, password);
            }
        });
        
        // Register prompt click listener
        binding.tvRegisterPrompt.setOnClickListener(v -> 
            navController.navigate(R.id.action_loginFragment_to_registerFragment));
            
        // Register button click listener - using a more direct approach to ensure navigation works
        binding.btnRegister.setOnClickListener(v -> {
            Log.d("LoginFragment", "Register button clicked, navigating to register fragment");
            // Reset the auth state to avoid triggering observers when we navigate
            authViewModel.resetAuthState();
            // Force navigation directly to the register fragment
            navController.navigate(R.id.registerFragment);
        });
        
        // Also fix the text prompt click listener
        binding.tvRegisterPrompt.setOnClickListener(v -> {
            // Reset the auth state to avoid triggering observers when we navigate
            authViewModel.resetAuthState();
            navController.navigate(R.id.registerFragment);
        });
    }
    
    private void observeViewModel() {
        // Single streamlined auth state observer for consistent behavior
        authViewModel.getLoginResult().observe(getViewLifecycleOwner(), result -> {
            // Debug logging to track authentication state
            Log.d("LoginFragment", "Auth state updated: authenticated=" + result.isAuthenticated() + 
                 ", loading=" + result.isLoading() + 
                 ", success=" + result.isSuccess() + 
                 ", hasError=" + result.hasError());
            
            if (result.isLoading()) {
                showLoading(true);
                return;
            }
            
            // Always ensure loading is hidden when not in loading state
            showLoading(false);
            
            // Check authentication result
            if (result.isAuthenticated() && result.getUser() != null) {
                // Successfully authenticated with user data
                User user = result.getUser();
                Log.d("LoginFragment", "Login successful for user: " + user.getUsername());
                
                // Display welcome message
                Toast.makeText(requireContext(), 
                        "Welcome, " + user.getUsername() + "!", 
                        Toast.LENGTH_SHORT).show();
                
                // Navigate to the lobbies screen
                navController.navigate(R.id.action_loginFragment_to_lobbiesFragment);
            } else if (result.hasError()) {
                // Show specific error message
                String errorMessage = result.getErrorMessage();
                Log.d("LoginFragment", "Login error: " + errorMessage);
                
                // Display user-friendly error message
                if (errorMessage != null && errorMessage.contains("credentials")) {
                    // Invalid credentials error
                    Toast.makeText(requireContext(), 
                            "Invalid username or password. Please try again.", 
                            Toast.LENGTH_LONG).show();
                } else if (errorMessage != null && errorMessage.contains("network")) {
                    // Network error
                    Toast.makeText(requireContext(), 
                            "Network error. Please check your connection and try again.", 
                            Toast.LENGTH_LONG).show();
                } else {
                    // Generic error
                    Toast.makeText(requireContext(), 
                            errorMessage != null ? errorMessage : "Login failed. Please try again.", 
                            Toast.LENGTH_LONG).show();
                }
                
                // Reset password field for security
                binding.etPassword.setText("");
            } else if (!result.isLoading() && !result.isAuthenticated()) {
                // This handles the case when login fails but doesn't have an explicit error message
                Log.d("LoginFragment", "Login failed without authentication");
                Toast.makeText(requireContext(), 
                        "Login failed. Please check your credentials and try again.", 
                        Toast.LENGTH_LONG).show();
                        
                // Reset password field for security
                binding.etPassword.setText("");
            }
        });
    }
    
    private boolean validateInput(String username, String password) {
        boolean isValid = true;
        
        if (username.isEmpty()) {
            binding.etUsername.setError("Username cannot be empty");
            isValid = false;
        }
        
        if (password.isEmpty()) {
            binding.etPassword.setError("Password cannot be empty");
            isValid = false;
        }
        
        return isValid;
    }
    
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnLogin.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnLogin.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

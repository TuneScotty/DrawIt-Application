package com.example.drawit_app.view.auth;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;

import com.example.drawit_app.model.User;
import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.AuthResponse;
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
        // Add direct observer on login API response
        authViewModel.getDirectLoginResponse().observe(getViewLifecycleOwner(), response -> {
            Log.d("LoginFragment", "Direct login response received: " + (response != null));
            if (response != null && response.isSuccess() && response.getData() != null) {
                // Force handling of successful login regardless of auth state
                showLoading(false);
                User user = response.getData().getUser();
                if (user != null) {
                    Log.d("LoginFragment", "Forcing navigation with direct response");
                    Toast.makeText(requireContext(), "Welcome " + user.getUsername() + "!", Toast.LENGTH_SHORT).show();
                    navController.navigate(R.id.action_loginFragment_to_lobbiesFragment);
                    return; // Exit early to avoid auth state handling conflicts
                }
            }
        });
        
        // Original auth state observer as backup
        authViewModel.getLoginResult().observe(getViewLifecycleOwner(), result -> {
            // Debug logging to track authentication state
            Log.d("LoginFragment", "Auth state updated: authenticated=" + result.isAuthenticated() + 
                 ", loading=" + result.isLoading() + 
                 ", success=" + result.isSuccess() + 
                 ", hasError=" + result.hasError());
            
            if (result.isLoading()) {
                showLoading(true);
            } else {
                showLoading(false);
                
                // Add explicit debug log about the resource state
                Log.d("LoginFragment", "Processing login response, auth state check completed");
                
                // Check if authentication was successful based on API response data
                if (result.isAuthenticated() && result.getUser() != null) {
                    // Authentication successful with user data - normal case
                    Log.d("LoginFragment", "Login successful with user data, navigating to lobbies");
                    Toast.makeText(requireContext(), "Welcome " + result.getUser().getUsername() + "!", Toast.LENGTH_SHORT).show();
                    navController.navigate(R.id.action_loginFragment_to_lobbiesFragment);
                } else if (result.isSuccess() && !result.isAuthenticated()) {
                    // This handles the case where we got a success response but the AuthState doesn't have authenticated=true
                    // This could happen if the auth state is not properly updated
                    Log.d("LoginFragment", "Success response but not authenticated, checking user repo");
                    
                    // Check if user is stored in repository
                    authViewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
                        if (user != null) {
                            Log.d("LoginFragment", "Found user in repository, proceeding with login");
                            Toast.makeText(requireContext(), "Welcome " + user.getUsername() + "!", Toast.LENGTH_SHORT).show();
                            navController.navigate(R.id.action_loginFragment_to_lobbiesFragment);
                        }
                    });
                } else if (result.hasError()) {
                    // Show error message for invalid credentials
                    Log.d("LoginFragment", "Login error: " + result.getErrorMessage());
                    Toast.makeText(requireContext(), 
                            result.getErrorMessage(), 
                            Toast.LENGTH_LONG).show();
                } else if (!result.isLoading()) {
                    // This handles the case when login fails but doesn't have an explicit error message
                    Log.d("LoginFragment", "Login failed without explicit error message");
                    Toast.makeText(requireContext(), 
                            "Login failed. Please check your credentials and try again.", 
                            Toast.LENGTH_LONG).show();
                }
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

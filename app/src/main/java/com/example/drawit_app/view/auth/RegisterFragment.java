package com.example.drawit_app.view.auth;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
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
import com.example.drawit_app.databinding.FragmentRegisterBinding;
import com.example.drawit_app.viewmodel.AuthViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment handling user registration functionality
 */
@AndroidEntryPoint
public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthViewModel authViewModel;
    private NavController navController;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        
        setupListeners();
    }

    private void setupListeners() {
        // Register button click listener
        binding.btnRegister.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String confirmPassword = binding.etConfirmPassword.getText().toString().trim();
            
            if (validateInput(username, email, password, confirmPassword)) {
                // Show loading indicator
                showLoading(true);
                
                // Log registration attempt
                Log.d("RegisterFragment", "Starting registration process for user: " + username);
                Log.d("RegisterFragment", "Email: " + email);
                
                // Call registration API and handle the response
                authViewModel.register(username, email, password);
                
                // Observe the result just this once
                authViewModel.getRegisterResult().observe(getViewLifecycleOwner(), result -> {
                    // Detailed debug logging for troubleshooting
                    Log.d("RegisterFragment", "Registration result: " +
                          "\nisAuthenticated=" + result.isAuthenticated() +
                          "\nisLoading=" + result.isLoading() +
                          "\nisSuccess=" + result.isSuccess() +
                          "\nhasError=" + result.hasError() +
                          (result.hasError() ? "\nerrorMessage=" + result.getErrorMessage() : "") +
                          (result.getUser() != null ? "\nuser=" + result.getUser().getUsername() : ""));
                    
                    // Hide loading indicator
                    showLoading(false);
                    
                    if (result.isAuthenticated() && result.getUser() != null) {
                        // Registration successful - go directly to the game
                        Toast.makeText(requireContext(), 
                                "Welcome, " + username + "!", 
                                Toast.LENGTH_SHORT).show();
                        // Navigate directly to game lobby or main game screen
                        navController.navigate(R.id.action_registerFragment_to_gameMainActivity);
                    } else if (result.hasError()) {
                        // Show detailed error message
                        Toast.makeText(requireContext(), 
                                result.getErrorMessage() != null ? result.getErrorMessage() : "Registration failed. Please try again.", 
                                Toast.LENGTH_LONG).show();
                    } else if (!result.isLoading() && result.isSuccess()) {
                        // Registration was successful but we may not have a complete user object yet
                        // We'll still try to go to the game
                        Toast.makeText(requireContext(),
                                "Welcome to DrawIt!",
                                Toast.LENGTH_SHORT).show();
                        navController.navigate(R.id.action_registerFragment_to_gameMainActivity);
                    }
                });
            }
        });
        
        // Back to login button click listener
        binding.btnBackToLogin.setOnClickListener(v -> 
            navController.navigate(R.id.action_registerFragment_to_loginFragment));
    }
    
    private boolean validateInput(String username, String email, String password, String confirmPassword) {
        boolean isValid = true;
        
        if (username.isEmpty()) {
            binding.etUsername.setError("Username cannot be empty");
            isValid = false;
        }
        
        if (email.isEmpty()) {
            binding.etEmail.setError("Email cannot be empty");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Invalid email format");
            isValid = false;
        }
        
        if (password.isEmpty()) {
            binding.etPassword.setError("Password cannot be empty");
            isValid = false;
        } else if (password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            isValid = false;
        }
        
        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            isValid = false;
        }
        
        return isValid;
    }
    
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnRegister.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnRegister.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

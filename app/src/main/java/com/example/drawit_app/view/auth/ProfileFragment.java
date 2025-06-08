package com.example.drawit_app.view.auth;

import android.os.Bundle;
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
import com.example.drawit_app.databinding.FragmentProfileBinding;
import com.example.drawit_app.viewmodel.AuthViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying and managing user profile information
 */
@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel authViewModel;
    private NavController navController;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        
        setupListeners();
        observeViewModel();
        loadUserData();
    }
    
    private void setupListeners() {
        // Update profile button click listener
        binding.btnUpdateProfile.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            
            if (validateInput(username, email)) {
                authViewModel.updateProfile(username, email);
            }
        });
        
        // Logout button click listener
        binding.btnLogout.setOnClickListener(v -> {
            authViewModel.logout();
            navController.navigate(R.id.action_profileFragment_to_loginFragment);
        });
        
        // Change avatar button click listener
        binding.btnChangeAvatar.setOnClickListener(v -> {
            // TODO: Implement avatar selection or camera functionality
            Toast.makeText(requireContext(), "Avatar change functionality coming soon", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void observeViewModel() {
        // Observe user profile state
        authViewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                binding.etUsername.setText(user.getUsername());
                binding.etEmail.setText(user.getEmail());
                binding.tvGamesPlayed.setText(String.valueOf(user.getGamesPlayed()));
                binding.tvGamesWon.setText(String.valueOf(user.getGamesWon()));
                
                // Calculate win rate
                int gamesPlayed = user.getGamesPlayed();
                int gamesWon = user.getGamesWon();
                float winRate = gamesPlayed > 0 ? (float) gamesWon / gamesPlayed * 100 : 0;
                // Find the TextView by ID as a fallback until the binding is regenerated
                if (getView() != null) {
                    ((android.widget.TextView) getView().findViewById(R.id.tv_win_rate)).setText(String.format("%.1f%%", winRate));
                }
            }
        });
        
        // Observe profile update state
        authViewModel.getUpdateProfileResult().observe(getViewLifecycleOwner(), result -> {
            if (result.isLoading()) {
                showLoading(true);
            } else {
                showLoading(false);
                
                if (result.isSuccess()) {
                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                } else if (result.getError() != null) {
                    Toast.makeText(requireContext(), 
                            result.getError().getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void loadUserData() {
        // Show loading indicator
        showLoading(true);
        
        // Add a timeout to prevent infinite loading
        new android.os.Handler().postDelayed(() -> {
            if (binding != null && binding.progressBar.getVisibility() == View.VISIBLE) {
                android.util.Log.d("ProfileFragment", "Forcing loading state to clear after timeout");
                showLoading(false);
                // Show a helpful message
                Toast.makeText(requireContext(), "Could not load profile data. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }, 8000); // 8 second timeout
        
        // Load the user profile
        authViewModel.loadUserProfile();
    }
    
    private boolean validateInput(String username, String email) {
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
        
        return isValid;
    }
    
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnUpdateProfile.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnUpdateProfile.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    
    /**
     * Dialog fragment for changing password
     */
    public static class ChangePasswordDialogFragment extends androidx.fragment.app.DialogFragment {
        // Implementation will be added in a separate class
    }
}

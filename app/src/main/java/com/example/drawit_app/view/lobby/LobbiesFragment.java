package com.example.drawit_app.view.lobby;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentLobbiesBinding;
import com.example.drawit_app.databinding.DialogCreateLobbyBinding;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.repository.UserRepository;
import com.example.drawit_app.view.adapter.LobbyAdapter;
import com.example.drawit_app.viewmodel.LobbyViewModel;

import javax.inject.Inject;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying and managing game lobbies
 */
@AndroidEntryPoint
public class LobbiesFragment extends Fragment implements LobbyAdapter.LobbyClickListener {

    private FragmentLobbiesBinding binding;
    private LobbyViewModel lobbyViewModel;
    private NavController navController;
    private LobbyAdapter lobbyAdapter;
    
    @Inject
    UserRepository userRepository;
    

    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLobbiesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        lobbyViewModel = new ViewModelProvider(requireActivity()).get(LobbyViewModel.class);
        
        setupRecyclerView();
        setupListeners();
        observeViewModel();
        
        // Load lobbies when fragment is created
        refreshLobbies();
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_lobbies, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_profile) {
            navController.navigate(R.id.action_lobbiesFragment_to_profileFragment);
            return true;
        } else if (id == R.id.action_drawing_archive) {
            navController.navigate(R.id.action_lobbiesFragment_to_drawingArchiveFragment);
            return true;
        } else if (id == R.id.action_refresh) {
            refreshLobbies();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void setupRecyclerView() {
        // Use the new constructor with UserRepository for automatic host username fetching
        lobbyAdapter = new LobbyAdapter(new ArrayList<>(), this, userRepository, getViewLifecycleOwner());
        binding.rvLobbies.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLobbies.setAdapter(lobbyAdapter);
    }
    
    private void setupListeners() {
        // Create lobby button click listener
        binding.fabCreateLobby.setOnClickListener(v -> showCreateLobbyDialog());
        
        // Swipe refresh listener
        binding.swipeRefresh.setOnRefreshListener(this::refreshLobbies);
    }
    
    private void observeViewModel() {
        // Observe lobbies list
        lobbyViewModel.getLobbiesState().observe(getViewLifecycleOwner(), lobbiesState -> {
            if (lobbiesState == null || lobbiesState.getLobbies() == null) return;
            
            // Get lobbies from state
            ArrayList<Lobby> lobbies = new ArrayList<>(lobbiesState.getLobbies());
            lobbyAdapter.updateLobbies(lobbies);
            
            // Show empty state if no lobbies
            if (lobbies.isEmpty()) {
                binding.tvNoLobbies.setVisibility(View.VISIBLE);
            } else {
                binding.tvNoLobbies.setVisibility(View.GONE);
            }
            
            // Ensure loading indicators are stopped when data is loaded
            binding.swipeRefresh.setRefreshing(false);
            binding.progressBar.setVisibility(View.GONE);
        });
        
        // Observe loading state
        lobbyViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.swipeRefresh.setRefreshing(isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            
            // Set a timeout to stop the loading indicator if it takes too long
            if (isLoading) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (binding != null && binding.swipeRefresh.isRefreshing()) {
                        binding.swipeRefresh.setRefreshing(false);
                        binding.progressBar.setVisibility(View.GONE);
                    }
                }, 10000); // 10-second timeout
            }
        });
        
        // Observe error messages from lobbies state
        lobbyViewModel.getLobbiesState().observe(getViewLifecycleOwner(), lobbiesState -> {
            String errorMsg = lobbiesState != null ? lobbiesState.getErrorMessage() : null;
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Observe lobby events to handle navigation
        lobbyViewModel.getLobbyEvent().observe(getViewLifecycleOwner(), lobbyEvent -> {
            if (lobbyEvent != null && lobbyEvent.type == LobbyViewModel.LobbyEventType.LOBBY_CREATED && lobbyEvent.lobby != null) {
                // Navigate to the lobby detail screen with the newly created lobby
                Lobby createdLobby = lobbyEvent.lobby;
                String lobbyId = createdLobby.getLobbyId();
                String name = createdLobby.getLobbyName();
                
                // Ensure we have a valid lobby name (can't be null for navigation args)
                if (name == null) {
                    name = "Unnamed Lobby";
                    Log.w("LobbiesFragment", "Created lobby has null name, using default");
                }
                
                // Log the values to help debug
                Log.d("LobbiesFragment", "Navigating to lobby: id=" + lobbyId + ", name=" + name);
                
                Bundle args = new Bundle();
                args.putString("lobbyId", lobbyId);
                args.putString("lobbyName", name);
                navController.navigate(R.id.action_lobbiesFragment_to_lobbyDetailFragment, args);
                
                // Reset the event to prevent re-triggering on configuration change
                lobbyViewModel.resetEvent();
            }
        });
    }
    
    private void refreshLobbies() {
        lobbyViewModel.refreshLobbies();
    }
    
    private void showCreateLobbyDialog() {
        DialogCreateLobbyBinding dialogBinding = DialogCreateLobbyBinding.inflate(getLayoutInflater());
        
        // Configure dropdowns with appropriate options
        setupMaxPlayersDropdown(dialogBinding.etMaxPlayers);
        setupRoundsDropdown(dialogBinding.etRounds);
        setupRoundDurationDropdown(dialogBinding.etRoundDuration);
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.create_lobby)
                .setView(dialogBinding.getRoot())
                .create();
                
        // Set up custom dialog buttons instead of using the default AlertDialog buttons
        dialogBinding.btnCreate.setOnClickListener(v -> {
            // Validate inputs when Create button is clicked
            if (validateLobbyInput(dialogBinding)) {
                // Get values and create lobby
                String lobbyName = dialogBinding.etLobbyName.getText().toString().trim();
                int maxPlayers = Integer.parseInt(dialogBinding.etMaxPlayers.getText().toString().trim());
                int rounds = Integer.parseInt(dialogBinding.etRounds.getText().toString().trim());
                int roundDuration = Integer.parseInt(dialogBinding.etRoundDuration.getText().toString().trim());
                
                // Dismiss dialog
                dialog.dismiss();
                
                // Show loading indicator
                binding.progressBar.setVisibility(View.VISIBLE);
                
                // Create the lobby
                createLobby(lobbyName, maxPlayers, rounds, roundDuration);
            }
        });
        
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        // Set text change listeners to clear errors when user types
        dialogBinding.etLobbyName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dialogBinding.tilLobbyName.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        dialog.show();
    }

    private void setupMaxPlayersDropdown(AutoCompleteTextView dropdown) {
        // Create array adapter for max players (2-4 players)
        String[] maxPlayersOptions = {"2", "3", "4"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, maxPlayersOptions);
        dropdown.setAdapter(adapter);
        dropdown.setText("4", false); // Default to 4 players
    }
    
    private void setupRoundsDropdown(AutoCompleteTextView dropdown) {
        // Create array adapter for rounds (1-5 rounds)
        String[] roundsOptions = {"1", "2", "3", "4", "5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, roundsOptions);
        dropdown.setAdapter(adapter);
        dropdown.setText("3", false); // Default to 3 rounds
    }
    
    private void setupRoundDurationDropdown(AutoCompleteTextView dropdown) {
        // Create array adapter for round duration (30-180 seconds, with 30-second increments)
        String[] durationOptions = {"30", "60", "90", "120", "150", "180"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, durationOptions);
        dropdown.setAdapter(adapter);
        dropdown.setText("60", false); // Default to 60 seconds
    }
    
    private boolean validateLobbyInput(DialogCreateLobbyBinding dialogBinding) {
        boolean isValid = true;
        
        // Reset errors
        dialogBinding.tilLobbyName.setError(null);
        dialogBinding.tilMaxPlayers.setError(null);
        dialogBinding.tilRounds.setError(null);
        dialogBinding.tilRoundDuration.setError(null);
        
        // Validate lobby name
        String lobbyName = dialogBinding.etLobbyName.getText().toString().trim();
        if (lobbyName.isEmpty()) {
            dialogBinding.tilLobbyName.setError(getString(R.string.error_lobby_name_required));
            isValid = false;
        } else if (lobbyName.length() < 3) {
            dialogBinding.tilLobbyName.setError(getString(R.string.error_lobby_name_too_short));
            isValid = false;
        } else if (lobbyName.length() > 30) {
            dialogBinding.tilLobbyName.setError(getString(R.string.error_lobby_name_too_long));
            isValid = false;
        }
        
        // Validate max players
        try {
            String maxPlayersStr = dialogBinding.etMaxPlayers.getText().toString().trim();
            if (maxPlayersStr.isEmpty()) {
                dialogBinding.tilMaxPlayers.setError(getString(R.string.error_field_required));
                isValid = false;
            } else {
                int maxPlayers = Integer.parseInt(maxPlayersStr);
                if (maxPlayers < 2 || maxPlayers > 4) {
                    dialogBinding.tilMaxPlayers.setError(getString(R.string.error_invalid_max_players));
                    isValid = false;
                }
            }
        } catch (NumberFormatException e) {
            dialogBinding.tilMaxPlayers.setError(getString(R.string.error_invalid_number));
            isValid = false;
        }
        
        // Validate rounds
        try {
            String roundsStr = dialogBinding.etRounds.getText().toString().trim();
            if (roundsStr.isEmpty()) {
                dialogBinding.tilRounds.setError(getString(R.string.error_field_required));
                isValid = false;
            } else {
                int rounds = Integer.parseInt(roundsStr);
                if (rounds < 1 || rounds > 5) {
                    dialogBinding.tilRounds.setError(getString(R.string.error_invalid_rounds));
                    isValid = false;
                }
            }
        } catch (NumberFormatException e) {
            dialogBinding.tilRounds.setError(getString(R.string.error_invalid_number));
            isValid = false;
        }
        
        // Validate round duration
        try {
            String roundDurationStr = dialogBinding.etRoundDuration.getText().toString().trim();
            if (roundDurationStr.isEmpty()) {
                dialogBinding.tilRoundDuration.setError(getString(R.string.error_field_required));
                isValid = false;
            } else {
                int roundDuration = Integer.parseInt(roundDurationStr);
                if (roundDuration < 30 || roundDuration > 180) {
                    dialogBinding.tilRoundDuration.setError(getString(R.string.error_invalid_duration));
                    isValid = false;
                } else if (roundDuration % 30 != 0) {
                    dialogBinding.tilRoundDuration.setError(getString(R.string.error_duration_increment));
                    isValid = false;
                }
            }
        } catch (NumberFormatException e) {
            dialogBinding.tilRoundDuration.setError(getString(R.string.error_invalid_number));
            isValid = false;
        }
        
        return isValid;
    }
    
    @Override
    public void onLobbyClick(Lobby lobby) {
        // Navigate to lobby detail screen
        String lobbyId = lobby.getLobbyId();
        // Ensure we have a valid lobby name - never pass null to Bundle.putString
        String lobbyName = lobby.getLobbyName();
        if (lobbyName == null) {
            lobbyName = "Unnamed Lobby";
            // Log the issue for debugging
            Log.w("LobbiesFragment", "Found lobby with null name: " + lobbyId);
        }
        
        Bundle args = new Bundle();
        args.putString("lobbyId", lobbyId);
        args.putString("lobbyName", lobbyName);
        navController.navigate(R.id.action_lobbiesFragment_to_lobbyDetailFragment, args);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    
    private void createLobby(String lobbyName, int maxPlayers, int rounds, int roundDuration) {
        binding.progressBar.setVisibility(View.VISIBLE);
        lobbyViewModel.createLobby(lobbyName, maxPlayers, rounds, roundDuration);
    }
}

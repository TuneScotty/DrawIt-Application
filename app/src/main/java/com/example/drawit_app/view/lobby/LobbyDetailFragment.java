package com.example.drawit_app.view.lobby;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashSet;
import java.util.Set;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentLobbyDetailBinding;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.repository.UserRepository;
import com.example.drawit_app.view.adapter.PlayerAdapter;
import com.example.drawit_app.viewmodel.LobbyViewModel;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying and interacting with a specific lobby
 */
@AndroidEntryPoint
public class LobbyDetailFragment extends Fragment implements WebSocketService.LobbyUpdateCallback {

    private FragmentLobbyDetailBinding binding;
    private LobbyViewModel lobbyViewModel;
    private NavController navController;
    private PlayerAdapter playerAdapter;
    private String lobbyId;
    private boolean isHost = false;

    public LobbyDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLobbyDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Set dark background to ensure text is visible
        view.setBackgroundColor(getResources().getColor(R.color.background, null));
        
        navController = Navigation.findNavController(view);
        lobbyViewModel = new ViewModelProvider(requireActivity()).get(LobbyViewModel.class);
        
        // Get lobby ID from arguments
        if (getArguments() != null) {
            lobbyId = getArguments().getString("lobbyId");
        }
        
        if (lobbyId == null) {
            // Invalid state, go back
            Toast.makeText(requireContext(), "Invalid lobby", Toast.LENGTH_SHORT).show();
            navController.navigateUp();
            return;
        }
        
        // Show loading indicator
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Set text colors to ensure visibility
        setTextColors();
        
        setupRecyclerView();
        setupListeners();
        observeViewModel();
        
        // Set WebSocket callback
        lobbyViewModel.setLobbyUpdateCallback(this);
        
        // Join lobby when fragment is created
        lobbyViewModel.joinLobby(lobbyId);
        
        Log.d("LobbyDetailFragment", "Joining lobby: " + lobbyId);
    }
    
    private void setupRecyclerView() {
        // Create adapter with initial empty player list
        ArrayList<User> initialPlayers = new ArrayList<>();
        
        // Add current user as a placeholder
        User currentUser = lobbyViewModel.getCurrentUser();
        if (currentUser != null) {
            initialPlayers.add(currentUser);
        }
        
        playerAdapter = new PlayerAdapter(initialPlayers);
        
        // Set a distinctive background for the RecyclerView to make it visible
        binding.rvPlayers.setBackgroundColor(0x33FFFFFF); // Semi-transparent white with more opacity
        binding.rvPlayers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlayers.setAdapter(playerAdapter);
    }
    
    private void setupListeners() {
        // Start game button click listener
        binding.btnStartGame.setOnClickListener(v -> {
            if (isHost) {
                lobbyViewModel.startGame(lobbyId);
            } else {
                Toast.makeText(requireContext(), R.string.error_not_host, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Add back button listener to handle proper lobby leaving
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Call the proper cleanup method before navigating back
                leaveLobbyAndCleanup();
            }
        });
    }
    
    /**
     * Handles proper lobby leaving, server notification, and auto-deletion if empty
     * This method ensures the player is properly removed from the lobby
     * and if they were the last player, the lobby is deleted from the database
     */
    private void leaveLobbyAndCleanup() {
        // Get current lobby data to check player count
        Lobby currentLobby = lobbyViewModel.getCurrentLobby().getValue();
        User currentUser = lobbyViewModel.getCurrentUser();
        
        if (currentLobby != null) {
            int playerCount = 0;
            if (currentLobby.getPlayers() != null) {
                playerCount = currentLobby.getPlayers().size();
                
                // Subtract 1 for the leaving player (current user)
                if (currentUser != null && currentUser.getUserId() != null) {
                    for (User player : currentLobby.getPlayers()) {
                        if (player.getUserId() != null && player.getUserId().equals(currentUser.getUserId())) {
                            playerCount--;
                            break;
                        }
                    }
                }
            }
            
            Log.d("LobbyDetailFragment", "Leaving lobby: " + currentLobby.getLobbyId() + 
                    ", Player count after leaving: " + playerCount);
            
            // If this is the last player, we'll request server to delete the lobby
            final boolean shouldDeleteLobby = playerCount == 0 || 
                    (isHost && playerCount == 0); // Host leaving empty lobby
            
            // Request server to remove player from lobby
            lobbyViewModel.leaveLobby().observe(getViewLifecycleOwner(), result -> {
                if (result.isSuccess()) {
                    // If last player or host leaving empty lobby, request server deletion
                    if (shouldDeleteLobby) {
                        Log.d("LobbyDetailFragment", "Last player left lobby, requesting deletion");
                        // If your API has a method to delete empty lobbies, call it here
                        lobbyViewModel.deleteLobbyIfEmpty(lobbyId);
                    }
                    
                    // Navigate back regardless of deletion result
                    navController.navigateUp();
                } else {
                    // If error, still leave but show error
                    Toast.makeText(requireContext(), 
                            "Error leaving lobby: " + result.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    navController.navigateUp();
                }
            });
        } else {
            // No lobby data, just navigate back
            lobbyViewModel.leaveLobby();
            navController.navigateUp();
        }
    }
    
    /**
     * Sets text colors for all text views to ensure visibility against dark background
     */
    private void setTextColors() {
        int textColor = 0xFFFFFFFF; // White color
        
        binding.tvHostLabel.setTextColor(textColor);
        binding.tvHostName.setTextColor(textColor);
        binding.tvRoundsLabel.setTextColor(textColor);
        binding.tvRounds.setTextColor(textColor);
        binding.tvDurationLabel.setTextColor(textColor);
        binding.tvDuration.setTextColor(textColor);
        binding.tvPlayersHeading.setTextColor(textColor);
        binding.tvPlayersCount.setTextColor(textColor);
        
        // Set button colors
        binding.btnStartGame.setBackgroundColor(getResources().getColor(R.color.accent, null));
        binding.btnStartGame.setTextColor(textColor);
        
        // Set RecyclerView background to semi-transparent
        binding.rvPlayers.setBackgroundColor(0x66FFFFFF);
    }
    
    private void observeViewModel() {
        // Show loading indicator while waiting for data
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Observe current lobby
        lobbyViewModel.getCurrentLobby().observe(getViewLifecycleOwner(), lobby -> {
            if (lobby != null) {
                Log.d("LobbyDetailFragment", "Received lobby data: " + lobby.getName() + ", players: " + lobby.getPlayers().size());
                
                // Hide progress indicator once we have data
                binding.progressBar.setVisibility(View.GONE);
                
                // CRITICAL: Set background color of the entire fragment
                requireView().setBackgroundColor(getResources().getColor(R.color.background, null));
                
                // Make sure all UI elements are visible with proper colors
                binding.tvHostLabel.setVisibility(View.VISIBLE);
                binding.tvHostLabel.setTextColor(0xFFFFFFFF);
                
                binding.tvHostName.setVisibility(View.VISIBLE);
                binding.tvHostName.setTextColor(0xFFFFFFFF);
                
                binding.tvRoundsLabel.setVisibility(View.VISIBLE);
                binding.tvRoundsLabel.setTextColor(0xFFFFFFFF);
                
                binding.tvRounds.setVisibility(View.VISIBLE);
                binding.tvRounds.setTextColor(0xFFFFFFFF);
                
                binding.tvDurationLabel.setVisibility(View.VISIBLE);
                binding.tvDurationLabel.setTextColor(0xFFFFFFFF);
                
                binding.tvDuration.setVisibility(View.VISIBLE);
                binding.tvDuration.setTextColor(0xFFFFFFFF);
                
                binding.tvPlayersHeading.setVisibility(View.VISIBLE);
                binding.tvPlayersHeading.setTextColor(0xFFFFFFFF);
                
                binding.tvPlayersCount.setVisibility(View.VISIBLE);
                binding.tvPlayersCount.setTextColor(0xFFFFFFFF);
                
                binding.rvPlayers.setVisibility(View.VISIBLE);
                binding.rvPlayers.setBackgroundColor(0x66FFFFFF); // Semi-transparent white background
                
                // Set activity title via the activity's action bar
                AppCompatActivity activity = (AppCompatActivity) requireActivity();
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setTitle(lobby.getName());
                }
                
                // Compute accurate player count with comprehensive checks
                User host = lobby.getHost();
                User currentUser = lobbyViewModel.getCurrentUser();
                
                // Start with the raw player list
                Set<String> countedPlayerIds = new HashSet<>(); // Track unique player IDs
                int playerCount = 0;
                
                // First count all players with valid user IDs in the player list
                if (lobby.getPlayers() != null) {
                    for (User player : lobby.getPlayers()) {
                        if (player != null && player.getUserId() != null && !player.getUserId().isEmpty()) {
                            countedPlayerIds.add(player.getUserId());
                            playerCount++;
                            
                            Log.d("LobbyDetailFragment", "Counted player: " + 
                                (player.getUsername() != null ? player.getUsername() : "unknown") + 
                                " (ID: " + player.getUserId() + ")");
                        }
                    }
                }
                
                // Ensure host is counted if they have a valid ID and aren't already counted
                if (host != null && host.getUserId() != null && !host.getUserId().isEmpty() && 
                    !countedPlayerIds.contains(host.getUserId())) {
                    countedPlayerIds.add(host.getUserId());
                    playerCount++;
                    Log.d("LobbyDetailFragment", "Added host to player count: " + 
                        (host.getUsername() != null ? host.getUsername() : "unknown") + 
                        " (ID: " + host.getUserId() + ")");
                }
                
                // Ensure current user is counted if not already counted
                if (currentUser != null && currentUser.getUserId() != null && !currentUser.getUserId().isEmpty() && 
                    !countedPlayerIds.contains(currentUser.getUserId())) {
                    countedPlayerIds.add(currentUser.getUserId());
                    playerCount++;
                    Log.d("LobbyDetailFragment", "Added current user to player count: " + 
                        (currentUser.getUsername() != null ? currentUser.getUsername() : "unknown") + 
                        " (ID: " + currentUser.getUserId() + ")");
                }
                
                // Ensure we don't exceed max players in display
                int maxPlayers = Math.max(lobby.getMaxPlayers(), playerCount);
                
                // Log the final count
                Log.d("LobbyDetailFragment", "Final player count: " + playerCount + "/" + maxPlayers + 
                      ", Unique player IDs: " + countedPlayerIds.size());
                
                // Set the player count display
                binding.tvPlayersCount.setText(playerCount + "/" + maxPlayers + " Players");
                
                // Get lobby rounds and duration with robust fallback handling
                // Ensure consistent display between host and clients
                
                // Log raw lobby values for debugging
                Log.d("LobbyDetailFragment", "Raw lobby values - numRounds: " + lobby.getNumRounds() + 
                        ", roundDurationSeconds: " + lobby.getRoundDurationSeconds() +
                        ", lobby ID: " + lobby.getLobbyId());
                
                // ==== ROUNDS HANDLING ====
                // Use explicit getter methods for consistency
                int rounds = lobby.getNumRounds();
                if (rounds <= 0) {
                    // Double-check with convenience method
                    rounds = lobby.getRounds();
                    Log.d("LobbyDetailFragment", "Using getRounds() convenience method: " + rounds);
                }
                
                // Apply fallback only if both methods return invalid values
                if (rounds <= 0) {
                    rounds = 3; // Default rounds
                    Log.d("LobbyDetailFragment", "Using absolute fallback for rounds: " + rounds);
                }
                
                // Use clear formatting for display
                binding.tvRounds.setText(String.valueOf(rounds));
                Log.d("LobbyDetailFragment", "Final rounds display value: " + rounds);
                
                // ==== DURATION HANDLING ====
                // Use explicit getter methods for consistency
                int duration = lobby.getRoundDurationSeconds();
                if (duration <= 0) {
                    // Double-check with convenience method
                    duration = lobby.getRoundDuration();
                    Log.d("LobbyDetailFragment", "Using getRoundDuration() convenience method: " + duration);
                }
                
                // Apply fallback only if both methods return invalid values
                if (duration <= 0) {
                    duration = 60; // Default duration
                    Log.d("LobbyDetailFragment", "Using absolute fallback for duration: " + duration);
                }
                
                // Use clear formatting for display
                binding.tvDuration.setText(duration + "s");
                Log.d("LobbyDetailFragment", "Final duration display value: " + duration);
                
                // Create complete player list including host and current user
                ArrayList<User> displayPlayers = new ArrayList<>();
                
                // First check if current user is the host
                boolean currentUserIsHost = false;
                if (currentUser != null && host != null && 
                    currentUser.getUserId() != null && host.getUserId() != null && 
                    currentUser.getUserId().equals(host.getUserId())) {
                    currentUserIsHost = true;
                    isHost = true;
                }
                
                // Make sure host is always first in the list
                if (host != null) {
                    displayPlayers.add(host);
                }
                
                // Add current user if not the host and not already in the list
                if (currentUser != null && !currentUserIsHost) {
                    boolean currentUserAlreadyAdded = false;
                    for (User player : displayPlayers) {
                        if (player.getUserId() != null && currentUser.getUserId() != null && 
                            player.getUserId().equals(currentUser.getUserId())) {
                            currentUserAlreadyAdded = true;
                            break;
                        }
                    }
                    
                    if (!currentUserAlreadyAdded) {
                        displayPlayers.add(currentUser);
                    }
                }
                
                // Add all other players
                if (lobby.getPlayers() != null) {
                    for (User player : lobby.getPlayers()) {
                        boolean alreadyInList = false;
                        for (User existingPlayer : displayPlayers) {
                            if (existingPlayer.getUserId() != null && player.getUserId() != null && 
                                existingPlayer.getUserId().equals(player.getUserId())) {
                                alreadyInList = true;
                                break;
                            }
                        }
                        
                        if (!alreadyInList) {
                            displayPlayers.add(player);
                        }
                    }
                }
                
                // Update the adapter with the final player list
                playerAdapter.updatePlayers(displayPlayers);
                
                // Log the player list for debugging
                StringBuilder playerListLog = new StringBuilder("Players in lobby: ");
                for (User player : displayPlayers) {
                    playerListLog.append(player.getUsername()).append(", ");
                }
                Log.d("LobbyDetailFragment", playerListLog.toString());
                
                // Update host display in UI with comprehensive fallback handling
                String hostUsername = "Unknown Host";
                
                // First attempt: Get username from host object if available
                if (host != null && host.getUsername() != null && !host.getUsername().isEmpty()) {
                    hostUsername = host.getUsername();
                    Log.d("LobbyDetailFragment", "Using host username from host object: " + hostUsername);
                } 
                // Second attempt: If host object doesn't have username but has ID matching a player, get from players list
                else if (host != null && host.getUserId() != null && lobby.getPlayers() != null) {
                    for (User player : lobby.getPlayers()) {
                        if (player != null && player.getUserId() != null && 
                            player.getUsername() != null && !player.getUsername().isEmpty() &&
                            player.getUserId().equals(host.getUserId())) {
                            hostUsername = player.getUsername();
                            Log.d("LobbyDetailFragment", "Found host username in players list: " + hostUsername);
                            break;
                        }
                    }
                }
                // Third attempt: If current user is host (by ID comparison), use current user's username
                else if (currentUserIsHost && currentUser != null && 
                         currentUser.getUsername() != null && !currentUser.getUsername().isEmpty()) {
                    hostUsername = currentUser.getUsername();
                    Log.d("LobbyDetailFragment", "Using current user as host: " + hostUsername);
                }
                
                // Add "(You)" suffix if current user is the host
                if (currentUserIsHost) {
                    binding.tvHostName.setText(hostUsername + " (You)");
                } else {
                    binding.tvHostName.setText(hostUsername);
                }
                
                // Make sure adapter knows who the host is for proper display
                playerAdapter.setHostUsername(hostUsername);
                
                // Log the host info for debugging
                Log.d("LobbyDetailFragment", "Final host display - Username: " + hostUsername + 
                        ", Host ID: " + (host != null ? host.getUserId() : "null") +
                        ", Current user is host: " + currentUserIsHost);
                
                // Update host UI based on whether current user is host
                updateHostUI(isHost);
                binding.btnStartGame.setBackgroundColor(getResources().getColor(R.color.accent, null));
                binding.btnStartGame.setTextColor(0xFFFFFFFF);

                if (activity.getSupportActionBar() != null) {
                    if (lobby.isLocked()) {
                        activity.getSupportActionBar().setSubtitle(getString(R.string.lobby_locked));
                    } else {
                        activity.getSupportActionBar().setSubtitle(null);
                    }
                }
            } else {
                // If lobby is null, keep showing loading indicator
                binding.progressBar.setVisibility(View.VISIBLE);
                
                Log.d("LobbyDetailFragment", "No lobby data received yet");
            }
        });
        
        // Observe game start event
        lobbyViewModel.getGameStartEvent().observe(getViewLifecycleOwner(), gameId -> {
            if (gameId != null && !gameId.isEmpty()) {
                // Navigate to game screen
                Bundle args = new Bundle();
                args.putString("gameId", gameId);
                navController.navigate(R.id.action_lobbyDetailFragment_to_gameFragment, args);
            }
        });
        
        // Observe error messages
        lobbyViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateHostUI(boolean isHost) {
        // Only show start game button for host
        binding.btnStartGame.setVisibility(isHost ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onLobbyStateChanged(LobbyStateMessage message) {
        // This is called from WebSocketService when lobby updates are received
        if (message != null && message.getLobbyPayload() != null) {
            lobbyViewModel.processLobbyUpdate(message.getLobbyPayload().toString());
        }
    }
    
    @Override
    public void onError(String errorMessage) {
        // Handle WebSocket error
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove WebSocket callback
        lobbyViewModel.setLobbyUpdateCallback(null);
        
        // Ensure we properly clean up the lobby if user leaves by navigating away
        // Only call this if the fragment is being destroyed but not when navigating to game
        if (lobbyViewModel.getGameStartEvent().getValue() == null) {
            leaveLobbyAndCleanup();
        }
        
        binding = null;
    }
    

}

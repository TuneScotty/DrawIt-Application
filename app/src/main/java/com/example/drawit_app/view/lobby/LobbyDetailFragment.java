package com.example.drawit_app.view.lobby;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashSet;
import java.util.List;
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
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbiesUpdateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.repository.BaseRepository;
import com.example.drawit_app.repository.UserRepository;
import com.example.drawit_app.view.adapter.PlayerAdapter;
import com.example.drawit_app.viewmodel.LobbyViewModel;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

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
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    com.example.drawit_app.util.WebSocketMessageConverter messageConverter;

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
        
        // Joining lobby
    }
    
    private void setupRecyclerView() {
        // Create adapter with initial empty player list
        ArrayList<User> initialPlayers = new ArrayList<>();
        
        // Add current user as a placeholder
        User currentUser = lobbyViewModel.getCurrentUser();
        if (currentUser != null) {
            initialPlayers.add(currentUser);
        }
        
        // Use enhanced adapter with UserRepository to fetch proper usernames
        playerAdapter = new PlayerAdapter(initialPlayers, userRepository, getViewLifecycleOwner());
        
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
    /**
     * Handles proper lobby leaving, server notification, and auto-deletion if empty
     * Thread-safe implementation with proper observer pattern usage
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
                if (currentUser != null) {
                    for (User player : currentLobby.getPlayers()) {
                        if (player.getUserId() != null && currentUser.getUserId() != null && 
                            player.getUserId().equals(currentUser.getUserId())) {
                            playerCount--;
                            break;
                        }
                    }
                }
            }
            
            Log.d("LobbyDetailFragment", "Leaving lobby: " + currentLobby.getLobbyId() + 
                    ", Player count after leaving: " + playerCount);
            
            // If this is the last player, we'll request server to delete the lobby
            final boolean shouldDeleteLobby = playerCount == 0; // Host leaving empty lobby
            
            // Show loading indicator
            binding.progressBar.setVisibility(View.VISIBLE);
            
            // Call ViewModel method directly - the navigation will be handled by the event observer
            lobbyViewModel.leaveLobby();
            
            // If last player or host leaving empty lobby, request server deletion
            if (shouldDeleteLobby && lobbyId != null) {
                // Last player left, requesting deletion
                lobbyViewModel.deleteLobbyIfEmpty(lobbyId);
            }
            
            // Note: Navigation back will be handled by the lobby event observer
            // in observeViewModel() when it receives the LOBBY_LEFT event
        } else {
            // No lobby data, just leave and navigate back immediately
            lobbyViewModel.leaveLobby();
            navController.navigateUp();
        }
    }
    
    /**
     * Sets text colors for all text views to ensure visibility against dark background
     */
    /**
     * Sets text colors for all text views to ensure visibility against dark background
     * Consolidated method to avoid duplication
     */
    private void setTextColors() {
        int textColor = 0xFFFFFFFF; // White color
        
        // Apply text color to all text elements
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
        
        // Ensure all text elements are visible
        binding.tvHostLabel.setVisibility(View.VISIBLE);
        binding.tvHostName.setVisibility(View.VISIBLE);
        binding.tvRoundsLabel.setVisibility(View.VISIBLE);
        binding.tvRounds.setVisibility(View.VISIBLE);
        binding.tvDurationLabel.setVisibility(View.VISIBLE);
        binding.tvDuration.setVisibility(View.VISIBLE);
        binding.tvPlayersHeading.setVisibility(View.VISIBLE);
        binding.tvPlayersCount.setVisibility(View.VISIBLE);
        binding.rvPlayers.setVisibility(View.VISIBLE);
    }
    
    private void observeViewModel() {
        // Show loading indicator while waiting for data
        binding.progressBar.setVisibility(View.VISIBLE);
        
        // Observe current lobby
        lobbyViewModel.getCurrentLobby().observe(getViewLifecycleOwner(), lobby -> {
            if (lobby != null) {
                // Immediately refresh lobby details from server to ensure we have the latest data
                lobbyViewModel.getLobbyDetails(lobby.getLobbyId());
                
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
                    activity.getSupportActionBar().setTitle(lobby.getLobbyName());
                }
                
                // Get host and current user references for UI updates
                User host = lobby.getHost();
                User currentUser = lobbyViewModel.getCurrentUser();
                
                // Simplified player counting using just the players list
                // The server now properly includes all players in the players list
                int playerCount = 0;
                if (lobby.getPlayers() != null) {
                    playerCount = lobby.getPlayers().size();
                }
                
                // Log player count for debugging
                android.util.Log.d(TAG, "Player count: " + playerCount);
                
                // Ensure we don't exceed max players in display
                int maxPlayers = Math.max(lobby.getMaxPlayers(), playerCount);
                
                // Set the player count display
                binding.tvPlayersCount.setText(playerCount + "/" + maxPlayers + " Players");
                
                // Get lobby rounds and duration with robust fallback handling
                
                // ==== ROUNDS HANDLING ====
                // Use explicit getter methods for consistency
                int rounds = lobby.getNumRounds();
                if (rounds <= 0) {
                    // Double-check with convenience method
                    rounds = lobby.getRounds();
                }
                
                // Apply fallback only if both methods return invalid values
                if (rounds <= 0) {
                    rounds = 3; // Default rounds
                }
                
                // Use clear formatting for display
                binding.tvRounds.setText(String.valueOf(rounds));
                
                // ==== DURATION HANDLING ====
                // Use explicit getter methods for consistency
                int duration = lobby.getRoundDurationSeconds();
                if (duration <= 0) {
                    // Double-check with convenience method
                    duration = lobby.getRoundDuration();
                }
                
                // Apply fallback only if both methods return invalid values
                if (duration <= 0) {
                    duration = 60; // Default duration
                }
                
                // Use clear formatting for display
                binding.tvDuration.setText(duration + "s");
                
                // Create complete player list including host and current user
                ArrayList<User> displayPlayers = new ArrayList<>();
                
                // First check if current user is the host
                boolean currentUserIsHost;
                if (currentUser != null && host != null && 
                    currentUser.getUserId() != null && host.getUserId() != null && 
                    currentUser.getUserId().equals(host.getUserId())) {
                    currentUserIsHost = true;
                    isHost = true;
                } else {
                    currentUserIsHost = false;
                }

                // Simply use the players list directly from the server
                // Since we can now get complete user data via the /users/{userId} endpoint
                displayPlayers.clear();
                
                if (lobby.getPlayers() != null) {
                    // Log player information for debugging
                    Log.d(TAG, "Processing lobby with " + lobby.getPlayers().size() + " players");
                    for (User player : lobby.getPlayers()) {
                        Log.d(TAG, "Player in lobby: " + 
                            (player.getUsername() != null ? player.getUsername() : "null") + 
                            ", ID: " + (player.getUserId() != null ? player.getUserId() : "null"));
                    }
                    
                    // Create a new list to avoid concurrent modification
                    java.util.List<User> players = new java.util.ArrayList<>(lobby.getPlayers());
                    
                    // Move host to the top of the list if present
                    if (host != null && host.getUserId() != null) {
                        // First remove host from the regular players list if present
                        for (int i = 0; i < players.size(); i++) {
                            User player = players.get(i);
                            if (player != null && player.getUserId() != null && 
                                host.getUserId().equals(player.getUserId())) {
                                players.remove(i);
                                break;
                            }
                        }
                        
                        // Add host as the first player
                        displayPlayers.add(host);
                    }
                    
                    // Add all other players
                    displayPlayers.addAll(players);
                    
                    // Log the final display players list
                    Log.d(TAG, "Final displayPlayers list has " + displayPlayers.size() + " players");
                } else {
                    Log.e(TAG, "Player list is null in lobby object!");
                }
                
                // Force adapter to update with a new instance of the list
                Log.d(TAG, "Updating player adapter with " + displayPlayers.size() + " players");
                playerAdapter.updatePlayers(new java.util.ArrayList<>(displayPlayers));
                
                // Update host display in UI using the user ID directly
                if (host != null && host.getUserId() != null && !host.getUserId().isEmpty()) {
                    // Use the userId to get the host data directly from the API
                    android.util.Log.d(TAG, "Fetching host info using userId: " + host.getUserId());
                    
                    // First set a temporary name
                    binding.tvHostName.setText("Loading host...");
                    
                    // Get the host user data from the repository
                    userRepository.getUserById(host.getUserId()).observe(getViewLifecycleOwner(), resource -> {
                        if (resource.getStatus() == BaseRepository.Resource.Status.SUCCESS && resource.getData() != null) {
                            String hostUsername = resource.getData().getUsername();
                            android.util.Log.d(TAG, "Got host username from API: " + hostUsername);
                            
                            // Add "(You)" suffix if current user is the host
                            if (currentUserIsHost) {
                                binding.tvHostName.setText(hostUsername + " (You)");
                            } else {
                                binding.tvHostName.setText(hostUsername);
                            }
                            
                            // Make sure adapter knows who the host is for proper display
                            playerAdapter.setHostUsername(hostUsername);
                        } else if (resource.getStatus() == BaseRepository.Resource.Status.ERROR) {
                            android.util.Log.e(TAG, "Error fetching host data: " + resource.getMessage());
                            // If error, show a default name
                            String fallbackName = currentUserIsHost && currentUser != null ? 
                                currentUser.getUsername() : "Unknown Host";
                                
                            if (currentUserIsHost) {
                                binding.tvHostName.setText(fallbackName + " (You)");
                            } else {
                                binding.tvHostName.setText(fallbackName);
                            }
                            playerAdapter.setHostUsername(fallbackName);
                        }
                    });
                } else {
                    android.util.Log.e(TAG, "No valid host ID found");
                    String fallbackName = "Unknown Host";
                    if (currentUserIsHost && currentUser != null && currentUser.getUsername() != null) {
                        fallbackName = currentUser.getUsername();
                    }
                    
                    // Add "(You)" suffix if current user is the host
                    if (currentUserIsHost) {
                        binding.tvHostName.setText(fallbackName + " (You)");
                    } else {
                        binding.tvHostName.setText(fallbackName);
                    }
                    playerAdapter.setHostUsername(fallbackName);
                }
                
                // Host text is now set directly in the API response handler
                
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
                
                // No lobby data yet
            }
        });
        
        // Observe lobby events for game start and leave events
        lobbyViewModel.getLobbyEvent().observe(getViewLifecycleOwner(), lobbyEvent -> {
            if (lobbyEvent == null) {
                Log.d(TAG, "LobbyDetailFragment: lobbyEvent is null");
                return;
            }
            
            Log.d(TAG, "LobbyDetailFragment: lobbyEvent received: " + lobbyEvent.type + 
                  (lobbyEvent.gameId != null ? ", gameId: " + lobbyEvent.gameId : ""));
            
            switch (lobbyEvent.type) {
                case GAME_STARTED:
                    if (lobbyEvent.gameId != null && !lobbyEvent.gameId.isEmpty()) {
                        Log.d(TAG, "LobbyDetailFragment: Navigating to game screen with gameId: " + lobbyEvent.gameId);
                        // Navigate to game screen
                        try {
                            Bundle args = new Bundle();
                            args.putString("gameId", lobbyEvent.gameId);
                            navController.navigate(R.id.action_lobbyDetailFragment_to_gameFragment, args);
                            Log.d(TAG, "LobbyDetailFragment: Navigation command executed");
                        } catch (Exception e) {
                            Log.e(TAG, "LobbyDetailFragment: Navigation failed", e);
                            Toast.makeText(requireContext(), "Failed to navigate to game: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                        
                        // Reset the event to prevent re-triggering - moved to after navigation
                        lobbyViewModel.resetEvent();
                    } else {
                        Log.e(TAG, "LobbyDetailFragment: Game started but gameId is null or empty");
                    }
                    break;
                    
                case LOBBY_LEFT:
                    Log.d(TAG, "LobbyDetailFragment: Leaving lobby");
                    // User has left the lobby, navigate back
                    navController.navigateUp();
                    lobbyViewModel.resetEvent();
                    break;
            }
        });
        
        // Observe error messages from lobby state
        lobbyViewModel.getLobbyState().observe(getViewLifecycleOwner(), lobbyState -> {
            String errorMsg = lobbyState != null ? lobbyState.getErrorMessage() : null;
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateHostUI(boolean isHost) {
        // Only show start game button for host
        binding.btnStartGame.setVisibility(isHost ? View.VISIBLE : View.GONE);
    }
    
    /**
     * WebSocket callback for lobby state changes
     * Ensures updates are processed on the main thread
     */
    @Override
    public void onLobbyStateChanged(LobbyStateMessage message) {
        // This is called from WebSocketService when lobby updates are received
        Log.d("LobbyDetailFragment", "⚡ WebSocket update received: onLobbyStateChanged with message: " + 
             (message != null ? "valid" : "null"));
             
        if (message != null && message.getLobbyPayload() != null) {
            // Process message on main thread for UI updates
            if (getActivity() == null || !isAdded()) {
                Log.w("LobbyDetailFragment", "⚡ Fragment detached, cannot process WebSocket update");
                return;
            }
            
            // Get event type for appropriate handling
            String eventType = message.getLobbyPayload().getEvent();
            Log.d("LobbyDetailFragment", "⚡ WebSocket event type: " + (eventType != null ? eventType : "null"));
            
            // First, update the repository state to ensure data consistency
            if (lobbyViewModel != null) {
                Log.d("LobbyDetailFragment", "⚡ Forwarding update to repository first");
                lobbyViewModel.processLobbyUpdate(message.getLobbyPayload().toString());
            }
            
            // Process the UI update on the main thread for thread safety
            requireActivity().runOnUiThread(() -> {
                // Safety check for fragment still being attached
                if (!isAdded()) return;
                
                // Skip processing if no lobby payload
                if (message.getLobbyPayload().getLobby() == null) {
                    Log.w("LobbyDetailFragment", "⚡ Received WebSocket update with null lobby payload");
                    return;
                }
                
                // Get received lobby ID and current lobby ID
                String receivedLobbyId = message.getLobbyPayload().getLobby().getLobbyId();
                String currentLobbyId = lobbyId;
                
                // Verify lobby ID match
                if (currentLobbyId == null || !currentLobbyId.equals(receivedLobbyId)) {
                    Log.d("LobbyDetailFragment", "⚡ Ignoring update for different lobby: current=" + 
                         currentLobbyId + ", received=" + receivedLobbyId);
                    return;
                }
                
                Log.d("LobbyDetailFragment", "⚡ Processing WebSocket update for lobby: " + receivedLobbyId);
                
                // Get players from the message
                List<User> updatedPlayers = message.getLobbyPayload().getPlayers();
                if (updatedPlayers == null || updatedPlayers.isEmpty()) {
                    Log.d("LobbyDetailFragment", "⚡ No players in payload, checking lobby object");
                    
                    // Try to get players directly from the lobby object as fallback
                    Lobby updatedLobby = message.getLobbyPayload().getLobby();
                    if (updatedLobby != null && updatedLobby.getPlayers() != null) {
                        updatedPlayers = updatedLobby.getPlayers();
                        Log.d("LobbyDetailFragment", "⚡ Using players from lobby object: " + updatedPlayers.size());
                    } else {
                        Log.w("LobbyDetailFragment", "⚡ No players available in WebSocket update");
                    }
                } else {
                    Log.d("LobbyDetailFragment", "⚡ Using players from payload: " + updatedPlayers.size());
                }
                
                // Debug log all players
                if (updatedPlayers != null) {
                    for (User player : updatedPlayers) {
                        Log.d("LobbyDetailFragment", "⚡ Player in update: " + player.getUserId() + " - " + player.getUsername());
                    }
                }
                
                // Always update UI directly from WebSocket data, regardless of ViewModel updates
                if (updatedPlayers != null && !updatedPlayers.isEmpty()) {
                    Log.d("LobbyDetailFragment", "⚡ Updating UI with " + updatedPlayers.size() + " players");
                    
                    // Get host ID for proper display
                    String hostId = message.getLobbyPayload().getLobby().getHostId();
                    Log.d("LobbyDetailFragment", "⚡ Host ID: " + hostId);
                    
                    // CRITICAL: Update player adapter with new player list
                    updatePlayerAdapter(updatedPlayers, hostId);
                    
                    // Force adapter notification
                    playerAdapter.notifyDataSetChanged();
                    
                    // Update player count display
                    int maxPlayers = message.getLobbyPayload().getLobby().getMaxPlayers();
                    updatePlayerCountDisplay(updatedPlayers.size(), maxPlayers);
                    
                    // Force layout to refresh
                    binding.rvPlayers.post(() -> binding.rvPlayers.invalidate());
                }
            });
        } else {
            Log.w("LobbyDetailFragment", "⚡ Received null message or null payload in onLobbyStateChanged");
        }
    }
    
    /**
     * Update player adapter directly with new player data
     * This is a helper method to ensure consistent player list updates
     */
    /**
     * Update player adapter directly with new player data
     * This is a helper method to ensure consistent player list updates
     */
    private void updatePlayerAdapter(List<User> players, String hostId) {
        if (players == null) {
            Log.w("LobbyDetailFragment", "⚡ Cannot update adapter with null players list");
            return;
        }
        
        Log.d("LobbyDetailFragment", "⚡ Updating player adapter with " + players.size() + " players");
        
        // Log every player we received
        for (int i = 0; i < players.size(); i++) {
            User player = players.get(i);
            if (player != null) {
                Log.d("LobbyDetailFragment", "⚡ Player[" + i + "] ID=" + player.getUserId() + 
                     ", username=" + player.getUsername());
            } else {
                Log.d("LobbyDetailFragment", "⚡ Player[" + i + "] is NULL");
            }
        }
        
        // Create a copy of the players list to work with
        List<User> displayPlayers = new ArrayList<>();
        User hostUser = null;
        
        // Check if hostId is valid
        if (hostId == null || hostId.isEmpty()) {
            Log.w("LobbyDetailFragment", "⚡ HostId is null or empty!");
        } else {
            Log.d("LobbyDetailFragment", "⚡ Looking for host with ID: " + hostId);
        }
        
        // Find host in player list
        if (hostId != null) {
            for (User player : players) {
                if (player != null && player.getUserId() != null) {
                    Log.d("LobbyDetailFragment", "⚡ Comparing player ID " + player.getUserId() + " with host ID " + hostId);
                    if (player.getUserId().equals(hostId)) {
                        hostUser = player;
                        Log.d("LobbyDetailFragment", "⚡ Found host in player list: " + 
                            (player.getUsername() != null ? player.getUsername() : "null"));
                        break;
                    }
                }
            }
        }
        
        // Add host at the top if found
        if (hostUser != null) {
            displayPlayers.add(hostUser);
            Log.d("LobbyDetailFragment", "⚡ Added host to display list: " + 
                (hostUser.getUsername() != null ? hostUser.getUsername() : "null"));
            
            // Add all non-host players
            for (User player : players) {
                if (player != null && player.getUserId() != null && 
                    !player.getUserId().equals(hostId)) {
                    displayPlayers.add(player);
                    Log.d("LobbyDetailFragment", "⚡ Added regular player to display list: " + 
                        (player.getUsername() != null ? player.getUsername() : "null"));
                }
            }
        } else {
            // No host found, just add all players
            Log.w("LobbyDetailFragment", "⚡ Host not found in player list, adding all players");
            displayPlayers.addAll(players);
        }
        
        Log.d("LobbyDetailFragment", "⚡ Final display list contains " + displayPlayers.size() + " players");
        
        // Check if adapter is still valid before updating
        if (playerAdapter != null) {
            // Update the adapter with the new list
            playerAdapter.updatePlayers(displayPlayers);
            Log.d("LobbyDetailFragment", "⚡ playerAdapter.updatePlayers called with " + displayPlayers.size() + " players");
        } else {
            Log.w("LobbyDetailFragment", "⚡ playerAdapter is null, skipping update");
            return;
        }
        
        // Check if binding is still valid before accessing views
        if (binding != null && binding.rvPlayers != null) {
            // Force a layout refresh
            binding.rvPlayers.post(() -> {
                // Double-check again in case binding becomes null between checks
                if (binding != null && binding.rvPlayers != null) {
                    Log.d("LobbyDetailFragment", "⚡ Forcing layout refresh for RecyclerView");
                    binding.rvPlayers.requestLayout();
                }
            });
        } else {
            Log.w("LobbyDetailFragment", "⚡ binding or binding.rvPlayers is null, skipping layout refresh");
        }
        
        // If we have a host user, update the adapter and UI accordingly
        if (hostUser != null && binding != null) {
            if (hostUser.getUsername() != null) {
                if (playerAdapter != null) {
                    playerAdapter.setHostUsername(hostUser.getUsername());
                }
                if (binding.tvHostName != null) {
                    binding.tvHostName.setText(hostUser.getUsername());
                    Log.d("LobbyDetailFragment", "⚡ Updated host username in UI: " + hostUser.getUsername());
                }
            } else {
                Log.d("LobbyDetailFragment", "⚡ Host username is null, fetching asynchronously");
                // Try to fetch host username asynchronously
                userRepository.getUserById(hostId).observe(getViewLifecycleOwner(), resource -> {
                    if (resource.isSuccess() && resource.getData() != null && 
                        resource.getData().getUsername() != null) {
                        String hostUsername = resource.getData().getUsername();
                        playerAdapter.setHostUsername(hostUsername);
                        binding.tvHostName.setText(hostUsername);
                        Log.d("LobbyDetailFragment", "⚡ Async fetched host username: " + hostUsername);
                    } else {
                        Log.w("LobbyDetailFragment", "⚡ Failed to fetch host username");
                    }
                });
            }
        } else {
            Log.w("LobbyDetailFragment", "⚡ No host user to update in UI");
        }
        
        // Update the player count UI
        String playerCountText = displayPlayers.size() + "/" + 
            (lobbyViewModel.getCurrentLobby().getValue() != null ? 
            lobbyViewModel.getCurrentLobby().getValue().getMaxPlayers() : "?") + " Players";
        binding.tvPlayersCount.setText(playerCountText);
        Log.d("LobbyDetailFragment", "⚡ Updated player count: " + playerCountText);
    }

    /**
     * Updates the player count display in the UI
     * This method can be called separately from updatePlayerAdapter to refresh just the count
     * 
     * @param playerCount The current number of players
     * @param maxPlayers The maximum number of players allowed
     */
    private void updatePlayerCountDisplay(int playerCount, int maxPlayers) {
        if (binding == null) return;
        
        // Ensure we don't show invalid counts
        int displayMaxPlayers = Math.max(maxPlayers, playerCount);
        
        // Set the player count text
        String playerCountText = playerCount + "/" + displayMaxPlayers + " Players";
        binding.tvPlayersCount.setText(playerCountText);
        Log.d("LobbyDetailFragment", "Player count UI updated: " + playerCountText);
    }

    /**
     * WebSocket callback for lobbies update messages
     * This is called when a lobbies_update message is received from the WebSocket
     */
    @Override
    public void onLobbiesUpdated(LobbiesUpdateMessage message) {
        // First convert any raw JSON objects to properly typed objects using WebSocketMessageConverter
        // This ensures we don't have ClassCastExceptions later even if the fragment is detached
        if (message != null && message.getLobbiesPayload() != null) {
            List<Lobby> lobbies = null;
            try {
                // Use the WebSocketMessageConverter to safely convert raw lobby objects
                lobbies = messageConverter.convertToLobbyList(message.getLobbiesPayload().getLobbies());
                Log.d("LobbyDetailFragment", "Converted " + (lobbies != null ? lobbies.size() : 0) + " lobbies using WebSocketMessageConverter");
            } catch (Exception e) {
                Log.e("LobbyDetailFragment", "Error converting lobbies: " + e.getMessage(), e);
                return;
            }
            
            final List<Lobby> finalLobbies = lobbies;
            
            // Check if the fragment is still attached to the activity before proceeding
            if (!isAdded() || getActivity() == null) {
                Log.w("LobbyDetailFragment", "Fragment detached, skipping UI update from lobbies_update");
                return;
            }
            
            // Now safely update the UI on the main thread
            getActivity().runOnUiThread(() -> {
                // Double-check that we're still attached and have valid data
                if (!isAdded() || getActivity() == null || finalLobbies == null || lobbyId == null || binding == null) {
                    Log.w("LobbyDetailFragment", "Fragment detached or missing data during UI update");
                    return;
                }
                
                Log.d("LobbyDetailFragment", "Received lobbies_update message, updating UI");
                
                // Find our lobby in the update list
                for (Lobby lobby : finalLobbies) {
                    if (lobby != null && lobbyId.equals(lobby.getLobbyId())) {
                        Log.d("LobbyDetailFragment", "Found our lobby in the update, updating UI");
                        
                        // Update player list
                        updatePlayerAdapter(lobby.getPlayers(), lobby.getHostId());
                        
                        // Update player count
                        updatePlayerCountDisplay(
                            lobby.getPlayers() != null ? lobby.getPlayers().size() : 0,
                            lobby.getMaxPlayers());
                        
                        // Update host UI if needed
                        User tbdUser = userRepository.getCurrentUser().getValue();
                        String userId = tbdUser != null ? tbdUser.getUserId() : null;
                        updateHostUI(userId != null && userId.equals(lobby.getHostId()));
                        break;
                    }
                }
            });
        } else {
            Log.w("LobbyDetailFragment", "Received null message or payload in onLobbiesUpdated");
        }
    }
    
    /**
     * WebSocket callback for error handling
     * Ensures errors are shown on the main thread and safely handles detached fragments
     */
    @Override
    public void onError(String errorMessage) {
        // Handle WebSocket error on the main thread for thread safety
        if (errorMessage != null && !errorMessage.isEmpty()) {
            // Check if fragment is still attached to avoid IllegalStateException
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // Double-check still attached before showing toast
                    if (isAdded()) {
                        Log.e("LobbyDetailFragment", "WebSocket error: " + errorMessage);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("LobbyDetailFragment", "Cannot show WebSocket error: fragment detached");
                    }
                });
            } else {
                // Fragment is detached, just log the error
                Log.e("LobbyDetailFragment", "WebSocket error while detached: " + errorMessage);
            }
        }
    }
    
    /**
     * WebSocket callback for game state changes
     * Handles transition to the game screen when game starts
     */
    @Override
    public void onGameStateChanged(GameStateMessage message) {
        // Super detailed logging to trace game state flow
        Log.w("LobbyDetailFragment", "🔍 onGameStateChanged CALLED with message: " + (message != null ? "non-null" : "NULL"));
        
        if (message != null) {
            Log.d("LobbyDetailFragment", "🔍 GamePayload: " + (message.getGamePayload() != null ? "non-null" : "NULL"));
            
            if (message.getGamePayload() != null) {
                Log.d("LobbyDetailFragment", "🔍 Game: " + (message.getGamePayload().getGame() != null ? "non-null" : "NULL"));
                Log.d("LobbyDetailFragment", "🔍 Event: " + message.getGamePayload().getEvent());
            }
        }
        
        if (message != null && message.getGamePayload() != null && message.getGamePayload().getGame() != null) {
            String gameId = message.getGamePayload().getGame().getGameId();
            String event = message.getGamePayload().getEvent();
            
            Log.i("LobbyDetailFragment", "⭐ GAME STATE MESSAGE - GameID: " + gameId + ", Event: " + 
                  (event != null ? event : "null") + ", Current thread: " + Thread.currentThread().getName());
            
            // Handle different game events
            if (event != null && event.equals("started")) {
                Log.i("LobbyDetailFragment", "🎮 GAME STARTED event received via WebSocket, gameId: " + gameId);
                
                // Check if fragment is still attached
                boolean isFragmentAdded = isAdded();
                boolean hasActivity = getActivity() != null;
                Log.d("LobbyDetailFragment", "🔍 Fragment state - isAdded: " + isFragmentAdded + 
                      ", hasActivity: " + hasActivity + ", navController: " + (navController != null ? "non-null" : "NULL"));
                
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Navigate to game screen
                        if (isAdded() && gameId != null && !gameId.isEmpty()) {
                            Log.i("LobbyDetailFragment", "🚀 NAVIGATING to game screen with gameId: " + gameId);
                            try {
                                Bundle args = new Bundle();
                                args.putString("gameId", gameId);
                                navController.navigate(R.id.action_lobbyDetailFragment_to_gameFragment, args);
                                Log.i("LobbyDetailFragment", "✅ Navigation completed successfully");
                            } catch (Exception e) {
                                Log.e("LobbyDetailFragment", "❌ Navigation FAILED: " + e.getMessage(), e);
                            }
                        } else {
                            Log.e("LobbyDetailFragment", "❌ Cannot navigate - isAdded: " + isAdded() + 
                                  ", gameId valid: " + (gameId != null && !gameId.isEmpty()));
                        }
                    });
                } else {
                    Log.e("LobbyDetailFragment", "❌ Cannot navigate - fragment detached or activity null");
                }
            } else {
                Log.i("LobbyDetailFragment", "ℹ️ Game event received but not 'started': " + event);
            }
        } else {
            Log.w("LobbyDetailFragment", "⚠️ Received null or incomplete game state message");
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove WebSocket callback
        lobbyViewModel.setLobbyUpdateCallback(null);
        
        // Ensure we properly clean up the lobby if user leaves by navigating away
        // Only call this if the fragment is being destroyed but not when navigating to game
        LobbyViewModel.LobbyEvent event = lobbyViewModel.getLobbyEvent().getValue();
        boolean isGameStarted = event != null && event.type == LobbyViewModel.LobbyEventType.GAME_STARTED;
        
        // Add log to see if fragment is being destroyed during game navigation
        Log.d("LobbyDetailFragment", "🔄 onDestroyView called, isGameStarted: " + isGameStarted);
        
        if (!isGameStarted) {
            leaveLobbyAndCleanup();
        }
        
        binding = null;
    }
    

}

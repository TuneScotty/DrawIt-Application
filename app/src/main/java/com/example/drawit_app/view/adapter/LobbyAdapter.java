package com.example.drawit_app.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.ItemLobbyBinding;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;

import java.util.List;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.UserRepository;

/**
 * Adapter for displaying a list of game lobbies
 */
public class LobbyAdapter extends RecyclerView.Adapter<LobbyAdapter.LobbyViewHolder> {

    private List<Lobby> lobbies;
    private final LobbyClickListener listener;
    private UserRepository userRepository;
    private LifecycleOwner lifecycleOwner;

    public LobbyAdapter(List<Lobby> lobbies, LobbyClickListener listener) {
        this.lobbies = lobbies;
        this.listener = listener;
    }
    
    public LobbyAdapter(List<Lobby> lobbies, LobbyClickListener listener, 
                       UserRepository userRepository, LifecycleOwner lifecycleOwner) {
        this.lobbies = lobbies;
        this.listener = listener;
        this.userRepository = userRepository;
        this.lifecycleOwner = lifecycleOwner;
    }

    @NonNull
    @Override
    public LobbyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLobbyBinding binding = ItemLobbyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LobbyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LobbyViewHolder holder, int position) {
        Lobby lobby = lobbies.get(position);
        System.out.println("Binding lobby: " + lobby);
        holder.bind(lobby);
    }

    @Override
    public int getItemCount() {
        return lobbies.size();
    }

    public void updateLobbies(List<Lobby> newLobbies) {
        this.lobbies = newLobbies;
        notifyDataSetChanged();
    }

    public class LobbyViewHolder extends RecyclerView.ViewHolder {
        private final ItemLobbyBinding binding;

        public LobbyViewHolder(ItemLobbyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Lobby lobby) {
            // Check for null lobby early to prevent crashes
            if (lobby == null) {
                android.util.Log.e("LobbyAdapter", "Cannot bind null lobby");
                binding.tvLobbyName.setText("Unknown Lobby");
                binding.tvHostName.setText("Host: Unknown");
                binding.tvPlayerCount.setText("Players: 0/0");
                binding.tvRounds.setText("Rounds: 0");
                binding.tvRoundDuration.setText("Duration: 0s");
                binding.iconLock.setVisibility(android.view.View.GONE);
                return;
            }
            
            try {
                // Get proper lobby name - try all possible methods
                String lobbyName = lobby.getLobbyName();
                if (lobbyName == null || lobbyName.isEmpty()) {
                    lobbyName = lobby.getLobbyName();
                }
                
                // Only use fallback if we absolutely have no name from any source
                if (lobbyName == null || lobbyName.isEmpty()) {
                    lobbyName = "Lobby " + lobby.getLobbyId();
                }
                binding.tvLobbyName.setText(lobbyName);
                
                // Get actual host data using the Lobby's getHost method
                User host = lobby.getHost();
                
                // Get actual host data
                String hostId = lobby.getHostId();  // String ID of the host from lobby details

                String hostUsernameToDisplay = "Unknown Host";
        android.util.Log.i("LobbyAdapter", "Binding lobby: " + lobby.getLobbyName() + " (ID: " + lobby.getLobbyId() + "), Target Host ID: " + hostId);

                // Priority 1: Check players list in the lobby object (JSON for this is good)
                if (hostId != null && lobby.getPlayers() != null) {
            android.util.Log.i("LobbyAdapter", "  Priority 1: Checking players list. Size: " + lobby.getPlayers().size());
            for (User playerInList : lobby.getPlayers()) {
                if (playerInList != null) {
                    android.util.Log.i("LobbyAdapter", "    Inspecting player in list - ID: " + playerInList.getUserId() + ", Username: " + playerInList.getUsername());
                    if (hostId.equals(playerInList.getUserId())) {
                        android.util.Log.i("LobbyAdapter", "      Found potential host in list: " + playerInList.getUserId() + " with username '" + playerInList.getUsername() + "'");
                    }
                }
            }
                    for (User player : lobby.getPlayers()) {
                        if (player != null && hostId.equals(player.getUserId())) {
                            String pUsername = player.getUsername();
                            if (pUsername != null && !pUsername.isEmpty() && !pUsername.startsWith("#")) {
                        android.util.Log.i("LobbyAdapter", "      SUCCESS (Priority 1): Using username '" + pUsername + "' for host " + hostId);
                                hostUsernameToDisplay = pUsername;
                                break; // Found valid username for host in players list
                            }
                        }
                    }
                }

                // Priority 2: Check the dedicated host object from the lobby
                // This is only if the players list didn't yield a valid username
                if (hostUsernameToDisplay.equals("Unknown Host") && host != null) {
            android.util.Log.i("LobbyAdapter", "  Priority 2: Checking User object from lobby.getHost(). UserID: " + host.getUserId() + ", Username: " + host.getUsername());
                    String hUsername = host.getUsername();
                    // Ensure username is valid and not a placeholder ID
                    if (hUsername != null && !hUsername.isEmpty() && !hUsername.startsWith("#")) {
                android.util.Log.i("LobbyAdapter", "      SUCCESS (Priority 2): Using username '" + hUsername + "' for host " + hostId);
                        hostUsernameToDisplay = hUsername;
                    }
                }
                
                // After all checks, display the determined username or "Unknown Host".
                // Priority 3: If we have a UserRepository and LifecycleOwner, fetch from API
                if (hostUsernameToDisplay.equals("Unknown Host") && 
                    userRepository != null && lifecycleOwner != null && hostId != null) {
                    
                    android.util.Log.i("LobbyAdapter", "  Priority 3: Fetching host from API. HostID: " + hostId);
                    
                    // Show loading state
                    binding.tvHostName.setText("Host: Loading...");
                    
                    LiveData<Resource<User>> userResource = userRepository.getUserById(hostId);
                    String finalHostUsernameToDisplay = hostUsernameToDisplay;
                    userResource.observe(lifecycleOwner, new Observer<Resource<User>>() {
                        @Override
                        public void onChanged(Resource<User> resource) {
                            if (resource.isSuccess() && resource.getData() != null) {
                                String fetchedUsername = resource.getData().getUsername();
                                android.util.Log.i("LobbyAdapter", "      SUCCESS (Priority 3): API returned username '" + 
                                                  fetchedUsername + "' for host " + hostId);
                                                  
                                if (fetchedUsername != null && !fetchedUsername.isEmpty() && !fetchedUsername.startsWith("#")) {
                                    binding.tvHostName.setText("Host: " + fetchedUsername);
                                } else {
                                    binding.tvHostName.setText("Host: " + finalHostUsernameToDisplay);
                                }
                                
                                // Remove observer after we get the data
                                userResource.removeObserver(this);
                            } else if (resource.isError()) {
                                android.util.Log.e("LobbyAdapter", "      FAILED (Priority 3): API error: " + resource.getMessage());
                                binding.tvHostName.setText("Host: " + finalHostUsernameToDisplay);
                                
                                // Remove observer on error
                                userResource.removeObserver(this);
                            }
                            // Don't remove observer on loading state
                        }
                    });
                } else {
                    // Fallback: Display whatever host name we have
                    binding.tvHostName.setText("Host: " + hostUsernameToDisplay);
                }
        android.util.Log.i("LobbyAdapter", "Final host display name for lobby " + lobby.getLobbyId() + ": '" + hostUsernameToDisplay + "'");
                
                // Calculate ACCURATE player count including the host
                int playerCount = 0;
                java.util.Set<String> countedPlayerIds = new java.util.HashSet<>();
                
                // Count players in the player list
                if (lobby.getPlayers() != null) {
                    for (User player : lobby.getPlayers()) {
                        if (player != null && player.getUserId() != null && !player.getUserId().isEmpty()) {
                            countedPlayerIds.add(player.getUserId());
                            playerCount++;
                        }
                    }
                    
                    // Player list exists, count is correct
                }
                
                // Make sure host is counted if not already in the player list
                if (host != null && host.getUserId() != null && !host.getUserId().isEmpty() && 
                    !countedPlayerIds.contains(host.getUserId())) {
                    playerCount++;
                }
                
                // Ensure we always show the player count accurately, even if zero
                binding.tvPlayerCount.setText("Players: " + playerCount + "/" + lobby.getMaxPlayers());
                
                // Get round settings
                int rounds = lobby.getNumRounds();
                // Fallback to getRounds if needed
                if (rounds <= 0) {
                    rounds = lobby.getRounds();
                }
                if (rounds > 0) {
                    binding.tvRounds.setText("Rounds: " + rounds);
                }
                
                // Get round duration
                int duration = lobby.getRoundDurationSeconds();
                // Fallback to getRoundDuration if needed
                if (duration <= 0) {
                    duration = lobby.getRoundDuration();
                }
                if (duration > 0) {
                    binding.tvRoundDuration.setText("Duration: " + duration + "s");
                }
                
                // Show lock icon if lobby is locked
                binding.iconLock.setVisibility(lobby.isLocked() ? View.VISIBLE : View.GONE);
                
                // Set click listener on join button
                binding.btnJoinLobby.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLobbyClick(lobby);
                    }
                });
                
                // Also set click listener for the entire item
                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onLobbyClick(lobby);
                    }
                });
                
                // Lobby data fully bound
            } catch (Exception e) {
                // Robust error handling to prevent crashes
                android.util.Log.e("LobbyAdapter", "Error binding lobby data", e);
            }
        }
    }

    public interface LobbyClickListener {
        void onLobbyClick(Lobby lobby);
    }
}

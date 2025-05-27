package com.example.drawit_app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.LobbyDao;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.request.CreateLobbyRequest;
import com.example.drawit_app.network.request.JoinLobbyRequest;
import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.LobbyListResponse;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Repository for lobby-related operations
 */
@Singleton
public class LobbyRepository extends BaseRepository {
    
    private final ApiService apiService;
    private final LobbyDao lobbyDao;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final Executor diskIO = Executors.newSingleThreadExecutor();
    
    private final MediatorLiveData<List<Lobby>> availableLobbies = new MediatorLiveData<>();
    private final MutableLiveData<Lobby> currentLobby = new MutableLiveData<>();
    
    @Inject
    public LobbyRepository(ApiService apiService, DrawItDatabase database, 
                          UserRepository userRepository, WebSocketService webSocketService) {
        this.apiService = apiService;
        this.lobbyDao = database.lobbyDao();
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        
        // Initialize WebSocket callback for lobby updates
        setupWebSocketCallback();
        
        // Add local database as a source of available lobbies
        availableLobbies.addSource(lobbyDao.getAvailableLobbies(), lobbies -> {
            if (lobbies != null) {
                availableLobbies.setValue(lobbies);
            }
        });
    }
    
    /**
     * Set up WebSocket callback for real-time lobby updates
     */
    private void setupWebSocketCallback() {
        webSocketService.setLobbyUpdateCallback(new WebSocketService.LobbyUpdateCallback() {
            @Override
            public void onLobbyStateChanged(LobbyStateMessage message) {
                // Update the local database and current lobby based on the message
                if (message != null && message.getLobbyPayload() != null && message.getLobbyPayload().getLobby() != null) {
                    Lobby updatedLobby = message.getLobbyPayload().getLobby();
                    
                    // Automatically delete empty lobbies
                    if (updatedLobby.getPlayers() == null || updatedLobby.getPlayers().isEmpty()) {
                        Log.d("LobbyRepository", "Deleting empty lobby: " + updatedLobby.getLobbyId());
                        // If this is an update about an empty lobby, remove it and notify the server
                        executeInBackground(() -> {
                            // Delete from local database
                            lobbyDao.deleteLobbyById(updatedLobby.getLobbyId());
                            
                            // Also call the server to delete this empty lobby
                            deleteEmptyLobby(updatedLobby.getLobbyId());
                        });
                        return;
                    }
                    
                    // Update in database
                    executeInBackground(() -> {
                        lobbyDao.insert(updatedLobby);
                    });
                    
                    // Update current lobby if it's the same one
                    Lobby currentLobbyValue = currentLobby.getValue();
                    if (currentLobbyValue != null && currentLobbyValue.getLobbyId().equals(updatedLobby.getLobbyId())) {
                        currentLobby.postValue(updatedLobby);
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Handle error
                setError(errorMessage);
            }
        });
    }
    
    /**
     * Get all available (unlocked) lobbies
     */
    public LiveData<List<Lobby>> getAvailableLobbies() {
        refreshLobbies();
        return availableLobbies;
    }
    
    /**
     * Refresh the list of lobbies from the server
     */
    public LiveData<Resource<LobbyListResponse>> refreshLobbies() {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<LobbyListResponse>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        // Create a loading result to return immediately
        MutableLiveData<Resource<LobbyListResponse>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        // Make the API call
        apiService.getLobbies("Bearer " + token).enqueue(new retrofit2.Callback<ApiResponse<LobbyListResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<LobbyListResponse>> call, retrofit2.Response<ApiResponse<LobbyListResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LobbyListResponse> apiResponse = response.body();
                    
                    if (apiResponse.isSuccess() && apiResponse.getData() != null && apiResponse.getData().getLobbies() != null) {
                        List<Lobby> lobbies = apiResponse.getData().getLobbies();
                        
                        // Log how many lobbies were returned from the API
                        Log.d("LobbyRepository", "Received " + lobbies.size() + " lobbies from the server");
                        for (Lobby lobby : lobbies) {
                            Log.d("LobbyRepository", "Lobby: " + lobby.getLobbyId() + ", Name: " + lobby.getLobbyName() + ", Players: " + 
                                  (lobby.getPlayers() != null ? lobby.getPlayers().size() : 0));
                        }
                        
                        // Filter out empty lobbies (no players)
                        List<Lobby> nonEmptyLobbies = new ArrayList<>();
                        for (Lobby lobby : lobbies) {
                            // Only keep lobbies that have at least one player
                            if (lobby.getPlayers() != null && !lobby.getPlayers().isEmpty()) {
                                nonEmptyLobbies.add(lobby);
                                Log.d("LobbyRepository", "Keeping lobby " + lobby.getLobbyId() + " with " + 
                                      lobby.getPlayers().size() + " players");
                            } else {
                                // Automatically delete empty lobbies
                                Log.d("LobbyRepository", "Skipping empty lobby: " + lobby.getLobbyId());
                                // Also notify the server to delete this empty lobby
                                final String emptyLobbyId = lobby.getLobbyId();
                                executeInBackground(() -> deleteEmptyLobby(emptyLobbyId));
                            }
                        }
                        
                        // Process lobby data on background thread
                        executeInBackground(() -> {
                            // First, clear all existing lobbies
                            lobbyDao.deleteAllLobbiesDirectly();
                            
                            // Insert all non-empty lobbies
                            for (Lobby lobby : nonEmptyLobbies) {
                                lobbyDao.insert(lobby);
                            }
                            
                            // Log how many lobbies were saved to the database
                            Log.d("LobbyRepository", "Saved " + nonEmptyLobbies.size() + " lobbies to the database");
                        });
                        
                        // Post success result with data
                        result.postValue(Resource.success(apiResponse.getData()));
                    } else {
                        // API returned error message
                        String errorMsg = apiResponse.getMessage() != null ? apiResponse.getMessage() : "Unknown API error";
                        Log.e("LobbyRepository", "API Error: " + errorMsg);
                        result.postValue(Resource.error(errorMsg, null));
                    }
                } else {
                    // HTTP error
                    String errorMsg = "Failed to get lobbies: " + response.message();
                    Log.e("LobbyRepository", errorMsg);
                    result.postValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<LobbyListResponse>> call, Throwable t) {
                String errorMsg = "Network error: " + t.getMessage();
                Log.e("LobbyRepository", errorMsg, t);
                result.postValue(Resource.error(errorMsg, null));
            }
        });
        
        
        return result;
    }
    
    /**
     * Get details for a specific lobby
     */
    public LiveData<Resource<Lobby>> getLobbyDetails(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<Lobby>> result = callApi(apiService.getLobbyDetails("Bearer " + token, lobbyId));
        
        // Observe the result to update local database and current lobby
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Lobby lobby = resource.getData();
                lobbyDao.insert(lobby);
                currentLobby.setValue(lobby);
            }
        });
        
        return result;
    }
    
    /**
     * Create a new lobby
     */
    public LiveData<Resource<Lobby>> createLobby(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        CreateLobbyRequest request = new CreateLobbyRequest(lobbyName, maxPlayers, numRounds, roundDurationSeconds);
        return callApi(apiService.createLobby("Bearer " + token, request));
    }
    
    /**
     * Join an existing lobby
     */
    public LiveData<Resource<Lobby>> joinLobby(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<Lobby>> result = callApi(apiService.joinLobby("Bearer " + token, lobbyId, new JoinLobbyRequest()));
        
        // Observe the result to update local database and current lobby
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Lobby lobby = resource.getData();
                
                // Execute database operations on background thread to avoid main thread freezes
                executeInBackground(() -> {
                    // Insert into database safely on background thread
                    lobbyDao.insert(lobby);
                    
                    // UI updates must be done on main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Update current lobby LiveData (main thread safe)
                        currentLobby.setValue(lobby);
                        
                        // Connect to the lobby via WebSocket
                        webSocketService.joinLobby(lobby.getLobbyId());
                    });
                });
            }
        });
        
        return result;
    }
    
    /**
     * Leave the current lobby
     */
    public LiveData<Resource<Void>> leaveLobby() {
        String token = userRepository.getAuthToken();
        if (token == null || currentLobby.getValue() == null) {
            MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not in a lobby or not authenticated", null));
            return result;
        }
        
        String lobbyId = currentLobby.getValue().getLobbyId();
        LiveData<Resource<Void>> result = callApi(apiService.leaveLobby("Bearer " + token, lobbyId));
        
        // Observe the result to update local database and current lobby
        observeOnce(result, resource -> {
            if (resource.isSuccess()) {
                // Disconnect from the lobby via WebSocket
                webSocketService.leaveLobby(lobbyId);
                currentLobby.setValue(null);
            }
        });
        
        return result;
    }
    
    /**
     * Update lobby settings (host only)
     */
    public LiveData<Resource<Lobby>> updateLobbySettings(int numRounds, int roundDurationSeconds) {
        String token = userRepository.getAuthToken();
        if (token == null || currentLobby.getValue() == null) {
            MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not in a lobby or not authenticated", null));
            return result;
        }
        
        Lobby currentLobbyValue = currentLobby.getValue();
        CreateLobbyRequest request = new CreateLobbyRequest(
                currentLobbyValue.getLobbyName(),
                currentLobbyValue.getMaxPlayers(),
                numRounds,
                roundDurationSeconds
        );
        
        LiveData<Resource<Lobby>> result = callApi(apiService.updateLobbySettings(
                "Bearer " + token,
                currentLobbyValue.getLobbyId(),
                request
        ));
        
        // Observe the result to update local database and current lobby
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Lobby lobby = resource.getData();
                lobbyDao.insert(lobby);
                currentLobby.setValue(lobby);
            }
        });
        
        return result;
    }
    
    /**
     * Toggle the lock status of the current lobby (host only)
     */
    public LiveData<Resource<Lobby>> toggleLobbyLock() {
        String token = userRepository.getAuthToken();
        if (token == null || currentLobby.getValue() == null) {
            MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not in a lobby or not authenticated", null));
            return result;
        }
        
        Lobby currentLobbyValue = currentLobby.getValue();
        boolean newLockStatus = !currentLobbyValue.isLocked();
        
        LiveData<Resource<Lobby>> result = callApi(apiService.toggleLobbyLock(
                "Bearer " + token,
                currentLobbyValue.getLobbyId(),
                newLockStatus
        ));
        
        // Observe the result to update local database and current lobby
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Lobby lobby = resource.getData();
                lobbyDao.insert(lobby);
                currentLobby.setValue(lobby);
            }
        });
        
        return result;
    }
    
    /**
     * Get the current lobby the user is in
     */
    public LiveData<Lobby> getCurrentLobby() {
        return currentLobby;
    }
    
    /**
     * Set an external callback for lobby updates
     * @param callback The callback to receive lobby updates
     */
    public void setLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        webSocketService.setLobbyUpdateCallback(callback);
    }
    
    /**
     * Process a lobby update message from WebSocket
     * @param lobbyData JSON data for lobby update
     */
    public void processLobbyUpdate(String lobbyData) {
        // This would typically parse the data and handle it
        // For now, we'll just log it
        setError("Received lobby update: " + (lobbyData != null ? lobbyData.substring(0, Math.min(20, lobbyData.length())) + "..." : "null"));
    }
    
    /**
     * Start a game in a lobby (host only)
     * @param lobbyId ID of the lobby to start game in
     * @return LiveData with game ID on success
     */
    public LiveData<Resource<String>> startGame(String lobbyId) {
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        String token = userRepository.getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        // In a real implementation, this would call an API
        // For now, we'll simulate a successful response
        result.setValue(Resource.success("game_" + lobbyId));
        
        return result;
    }
    
    /**
     * Toggle the lock state of a lobby (host only)
     * @param lobbyId ID of the lobby to toggle lock state
     * @param lock True to lock, false to unlock
     * @return LiveData with updated lobby on success
     */
    public LiveData<Resource<Lobby>> toggleLobbyLock(String lobbyId, boolean lock) {
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        String token = userRepository.getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        // Get current lobby
        Lobby lobby = currentLobby.getValue();
        if (lobby == null || !lobby.getLobbyId().equals(lobbyId)) {
            result.setValue(Resource.error("Invalid lobby ID", null));
            return result;
        }
        
        // Update lock state
        lobby.setLocked(lock);
        currentLobby.postValue(lobby);
        
        // In a real implementation, this would call an API
        // For now, we'll simulate a successful response
        result.setValue(Resource.success(lobby));
        
        return result;
    }
    
    /**
     * Helper method to observe a LiveData object once
     */
    private <T> void observeOnce(LiveData<T> liveData, OnObservedListener<T> listener) {
        liveData.observeForever(new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T t) {
                listener.onObserved(t);
                liveData.removeObserver(this);
            }
        });
    }
    
    /**
     * Interface for observing LiveData once
     */
    private interface OnObservedListener<T> {
        void onObserved(T t);
    }
    
    /**
     * Helper method to execute database operations on a background thread
     */
    private void executeInBackground(Runnable operation) {
        diskIO.execute(operation);
    }
    
    /**
     * Delete an empty lobby on the server if current user is the host
     * @param lobbyId ID of the empty lobby to delete
     */
    private void deleteEmptyLobby(String lobbyId) {
        // Guard against null lobbyId
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.e("LobbyRepository", "Cannot delete lobby with null or empty ID");
            return;
        }
        
        String token = userRepository.getAuthToken();
        if (token == null) {
            Log.e("LobbyRepository", "Cannot delete lobby: No auth token");
            return;
        }
        
        try {
            // Make a DELETE request to remove the empty lobby
            LiveData<Resource<Void>> result = callApi(apiService.deleteLobby("Bearer " + token, lobbyId));
            
            observeOnce(result, resource -> {
                if (resource.isSuccess()) {
                    // Successfully deleted empty lobby
                    Log.d("LobbyRepository", "Successfully deleted empty lobby: " + lobbyId);
                    executeInBackground(() -> {
                        lobbyDao.deleteLobbyById(lobbyId);
                    });
                } else {
                    Log.e("LobbyRepository", "Failed to delete empty lobby: " + 
                          (resource.getMessage() != null ? resource.getMessage() : "Unknown error"));
                }
            });
        } catch (Exception e) {
            Log.e("LobbyRepository", "Exception while trying to delete empty lobby: " + e.getMessage());
        }
    }
}

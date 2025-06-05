package com.example.drawit_app.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.LobbyDao;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.request.CreateLobbyRequest;
import com.example.drawit_app.network.request.JoinLobbyRequest;
import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.LobbyListResponse;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Streamlined repository for lobby-related operations
 */
@Singleton
public class LobbyRepository extends BaseRepository {
    
    private static final String TAG = "LobbyRepository";
    
    // Core dependencies
    private final ApiService apiService;
    private final LobbyDao lobbyDao;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();
    
    // LiveData state
    private final MediatorLiveData<List<Lobby>> availableLobbies = new MediatorLiveData<>();
    private final MutableLiveData<Lobby> currentLobby = new MutableLiveData<>();
    
    // WebSocket callback
    private WebSocketService.LobbyUpdateCallback externalCallback;
    
    @Inject
    public LobbyRepository(ApiService apiService, DrawItDatabase database,
                           UserRepository userRepository, WebSocketService webSocketService) {
        this.apiService = apiService;
        this.lobbyDao = database.lobbyDao();
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        
        // Set up WebSocket connection for real-time updates
        setupWebSocketCallback();
        
        // Observe local database for available lobbies
        availableLobbies.addSource(lobbyDao.getAvailableLobbies(), lobbies -> {
            if (lobbies != null) {
                availableLobbies.setValue(lobbies);
            }
        });
    }

    public LobbyRepository(ApiService apiService, LobbyDao lobbyDao, UserRepository userRepository, WebSocketService webSocketService) {
        this.apiService = apiService;
        this.lobbyDao = lobbyDao;
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
    }

    /**
     * Set up WebSocket callback for real-time lobby updates
     */
    private void setupWebSocketCallback() {
        webSocketService.setLobbyUpdateCallback(new WebSocketService.LobbyUpdateCallback() {
            @Override
            public void onLobbyStateChanged(LobbyStateMessage message) {
                if (message != null && message.getLobbyPayload() != null && 
                    message.getLobbyPayload().getLobby() != null) {
                    
                    Lobby updatedLobby = message.getLobbyPayload().getLobby();
                    
                    // Handle empty lobbies
                    if (updatedLobby.getPlayers() == null || updatedLobby.getPlayers().isEmpty()) {
                        Log.d(TAG, "Empty lobby detected: " + updatedLobby.getLobbyId());
                        executeInBackground(() -> {
                            lobbyDao.deleteLobbyById(updatedLobby.getLobbyId());
                        });
                        return;
                    }
                    
                    // Update local database
                    executeInBackground(() -> {
                        lobbyDao.insert(updatedLobby);
                    });
                    
                    // Update current lobby if it's the same one
                    Lobby currentLobbyValue = currentLobby.getValue();
                    if (currentLobbyValue != null && 
                        currentLobbyValue.getLobbyId().equals(updatedLobby.getLobbyId())) {
                        currentLobby.postValue(updatedLobby);
                    }
                    
                    // Forward to external callback if present
                    if (externalCallback != null) {
                        externalCallback.onLobbyStateChanged(message);
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "WebSocket error: " + errorMessage);
                
                // Forward to external callback
                if (externalCallback != null) {
                    externalCallback.onError(errorMessage);
                }
            }
        });
    }
    
    /**
     * Get all available lobbies
     */
    public LiveData<List<Lobby>> getAvailableLobbies() {
        // Refresh data from server
        refreshLobbies();
        return availableLobbies;
    }
    
    /**
     * Refresh the list of lobbies from the server
     */
    public LiveData<Resource<LobbyListResponse>> refreshLobbies() {
        String token = userRepository.getAuthToken();
        if (token == null) {
            return createErrorLiveData("Not authenticated");
        }
        
        MutableLiveData<Resource<LobbyListResponse>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        apiService.getLobbies("Bearer " + token).enqueue(new retrofit2.Callback<ApiResponse<LobbyListResponse>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<LobbyListResponse>> call, 
                                  retrofit2.Response<ApiResponse<LobbyListResponse>> response) {
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    List<Lobby> lobbies = response.body().getData().getLobbies();
                    Log.d(TAG, "Received " + lobbies.size() + " lobbies from server");
                    
                    // Update database in background
                    executeInBackground(() -> {
                        lobbyDao.deleteAllLobbiesDirectly();
                        for (Lobby lobby : lobbies) {
                            lobbyDao.insert(lobby);
                        }
                    });
                    
                    result.postValue(Resource.success(response.body().getData()));
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to get lobbies";
                    Log.e(TAG, errorMsg);
                    result.postValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<LobbyListResponse>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
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
            return createErrorLiveData("Not authenticated");
        }
        
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        apiService.getLobbyDetails("Bearer " + token, lobbyId).enqueue(new retrofit2.Callback<ApiResponse<Lobby>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Lobby>> call, 
                                  retrofit2.Response<ApiResponse<Lobby>> response) {
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    Lobby lobby = response.body().getData();
                    
                    // Update database
                    executeInBackground(() -> {
                        lobbyDao.insert(lobby);
                    });
                    
                    // Update current lobby if it's the same one
                    Lobby currentLobbyValue = currentLobby.getValue();
                    if (currentLobbyValue != null && currentLobbyValue.getLobbyId().equals(lobbyId)) {
                        currentLobby.postValue(lobby);
                    }
                    
                    result.postValue(Resource.success(lobby));
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to get lobby details";
                    Log.e(TAG, errorMsg);
                    result.postValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Lobby>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
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
            return createErrorLiveData("Not authenticated");
        }
        
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        // Create request
        CreateLobbyRequest request = new CreateLobbyRequest(lobbyName, maxPlayers, numRounds, roundDurationSeconds);
        
        // Execute on background thread to avoid ANR
        executeInBackground(() -> {
            try {
                Log.d(TAG, "Creating lobby: " + lobbyName + ", rounds: " + numRounds + 
                      ", duration: " + roundDurationSeconds);
                
                // Make synchronous API call
                retrofit2.Response<ApiResponse<Lobby>> response = 
                    apiService.createLobby("Bearer " + token, request).execute();
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    Lobby lobby = response.body().getData();
                    
                    // Save to database
                    lobbyDao.insert(lobby);
                    
                    // Update current lobby
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        currentLobby.setValue(lobby);
                        result.setValue(Resource.success(lobby));
                    });
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to create lobby";
                    postError(result, errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating lobby", e);
                postError(result, "Network error: " + e.getMessage());
            }
        });
        
        return result;
    }
    
    /**
     * Join an existing lobby
     */
    public LiveData<Resource<Lobby>> joinLobby(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            return createErrorLiveData("Not authenticated");
        }
        
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        JoinLobbyRequest request = new JoinLobbyRequest();
        
        apiService.joinLobby("Bearer " + token, lobbyId, request).enqueue(new retrofit2.Callback<ApiResponse<Lobby>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Lobby>> call, 
                                  retrofit2.Response<ApiResponse<Lobby>> response) {
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    Lobby lobby = response.body().getData();
                    
                    // Update database and current lobby
                    executeInBackground(() -> {
                        lobbyDao.insert(lobby);
                        
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> {
                            currentLobby.setValue(lobby);
                            result.setValue(Resource.success(lobby));
                            
                            // Connect to WebSocket for this lobby
                            webSocketService.joinLobby(lobbyId);
                        });
                    });
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to join lobby";
                    Log.e(TAG, errorMsg);
                    result.setValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Lobby>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                result.setValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });
        
        return result;
    }
    
    /**
     * Leave the current lobby
     */
    public LiveData<Resource<Void>> leaveLobby() {
        String token = userRepository.getAuthToken();
        Lobby lobby = currentLobby.getValue();
        
        if (token == null || lobby == null) {
            return createErrorLiveData("Not in a lobby or not authenticated");
        }
        
        String lobbyId = lobby.getLobbyId();
        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        apiService.leaveLobby("Bearer " + token, lobbyId).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call, 
                                  retrofit2.Response<ApiResponse<Void>> response) {
                
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    // Disconnect from WebSocket
                    webSocketService.leaveLobby(lobbyId);
                    
                    // Clear current lobby
                    currentLobby.setValue(null);
                    
                    result.setValue(Resource.success(null));
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to leave lobby";
                    Log.e(TAG, errorMsg);
                    result.setValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Void>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                result.setValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });
        
        return result;
    }
    
    /**
     * Update lobby settings (host only)
     */
    public LiveData<Resource<Lobby>> updateLobbySettings(int numRounds, int roundDurationSeconds) {
        String token = userRepository.getAuthToken();
        Lobby lobby = currentLobby.getValue();
        
        if (token == null || lobby == null) {
            return createErrorLiveData("Not in a lobby or not authenticated");
        }
        
        String lobbyId = lobby.getLobbyId();
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        // Create request with the same values as the current lobby except for the settings we're changing
        CreateLobbyRequest request = new CreateLobbyRequest(
            lobby.getLobbyName(), 
            lobby.getMaxPlayers(), 
            numRounds, 
            roundDurationSeconds
        );
        
        apiService.updateLobbySettings("Bearer " + token, lobbyId, request)
            .enqueue(new retrofit2.Callback<ApiResponse<Lobby>>() {
                @Override
                public void onResponse(retrofit2.Call<ApiResponse<Lobby>> call, 
                                      retrofit2.Response<ApiResponse<Lobby>> response) {
                    
                    if (response.isSuccessful() && response.body() != null && 
                        response.body().isSuccess() && response.body().getData() != null) {
                        
                        Lobby updatedLobby = response.body().getData();
                        
                        // Ensure settings are set even if server doesn't return them
                        updatedLobby.setNumRounds(numRounds);
                        updatedLobby.setRoundDurationSeconds(roundDurationSeconds);
                        
                        // Update database and current lobby
                        executeInBackground(() -> {
                            lobbyDao.insert(updatedLobby);
                            
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> {
                                currentLobby.setValue(updatedLobby);
                                result.setValue(Resource.success(updatedLobby));
                            });
                        });
                    } else {
                        String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                         response.body().getMessage() : "Failed to update lobby settings";
                        Log.e(TAG, errorMsg);
                        result.setValue(Resource.error(errorMsg, null));
                    }
                }
                
                @Override
                public void onFailure(retrofit2.Call<ApiResponse<Lobby>> call, Throwable t) {
                    Log.e(TAG, "Network error: " + t.getMessage(), t);
                    result.setValue(Resource.error("Network error: " + t.getMessage(), null));
                }
            });
        
        return result;
    }
    
    /**
     * Start a game in a lobby (host only)
     */
    public LiveData<Resource<String>> startGame(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            return createErrorLiveData("Not authenticated");
        }
        
        MutableLiveData<Resource<String>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        apiService.startGame("Bearer " + token, lobbyId).enqueue(new retrofit2.Callback<ApiResponse<Game>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Game>> call,
                                  retrofit2.Response<ApiResponse<Game>> response) {
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    Game game = response.body().getData();
                    result.setValue(Resource.success(game.getGameId()));
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ? 
                                     response.body().getMessage() : "Failed to start game";
                    Log.e(TAG, errorMsg);
                    result.setValue(Resource.error(errorMsg, null));
                }
            }
            
            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Game>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                result.setValue(Resource.error("Network error: " + t.getMessage(), null));
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
     */
    public void setLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        this.externalCallback = callback;
    }
    
    // Helper methods
    
    /**
     * Execute an operation in the background
     */
    private void executeInBackground(Runnable operation) {
        backgroundExecutor.execute(operation);
    }
    
    /**
     * Create a LiveData with an error state
     */
    private <T> MutableLiveData<Resource<T>> createErrorLiveData(String errorMessage) {
        MutableLiveData<Resource<T>> result = new MutableLiveData<>();
        result.setValue(Resource.error(errorMessage, null));
        return result;
    }
    
    /**
     * Post an error to a LiveData on the main thread
     */
    private <T> void postError(MutableLiveData<Resource<T>> liveData, String errorMessage) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            liveData.setValue(Resource.error(errorMessage, null));
        });
    }
}

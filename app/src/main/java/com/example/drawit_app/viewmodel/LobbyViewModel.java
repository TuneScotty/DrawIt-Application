package com.example.drawit_app.viewmodel;

import android.util.Log;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;


import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.LobbiesState;
import com.example.drawit_app.model.LobbyState;
import com.example.drawit_app.model.User;
import com.example.drawit_app.api.WebSocketService;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.api.message.LobbiesUpdateMessage;
import com.example.drawit_app.api.message.LobbyStateMessage;
import com.example.drawit_app.api.response.LobbyListResponse;
import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.LobbyRepository;
import com.example.drawit_app.repository.UserRepository;
import com.example.drawit_app.util.LobbySettingsManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;


@HiltViewModel
public class LobbyViewModel extends ViewModel {

    private static final String TAG = "LobbyViewModel";
    private static final long REFRESH_COOLDOWN_MS = 2000; // 2 second cooldown

    // Core dependencies
    private final LobbyRepository lobbyRepository;
    private final WebSocketService webSocketService;
    private final UserRepository userRepository;
    private final LobbySettingsManager settingsManager;

    // Debounce for preventing API spam
    private long lastRefreshTime = 0;
    
    // Store the current WebSocket callback to manage lifecycle
    private WebSocketService.LobbyUpdateCallback currentCallback = null;

    /**
     * Remove a previously registered lobby update callback
     * @param callback The callback to remove
     */
    public void removeLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        Log.d(TAG, "Removing lobby update callback: " + callback);
        if (callback != null && callback.equals(this.currentCallback)) {
            this.currentCallback = null;
        }
        lobbyRepository.removeLobbyUpdateCallback(callback);
    }

    // Simplified LiveData state - REDUCED from 6 to 4 state objects
    private final MediatorLiveData<LobbiesState> lobbiesState = new MediatorLiveData<>();
    private final MediatorLiveData<LobbyState> lobbyState = new MediatorLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private final MutableLiveData<LobbyEvent> lobbyEvent = new MutableLiveData<>();

    private static class SingleUseObserver<T> implements Observer<T> {
        private final Observer<T> wrappedObserver;
        private LiveData<T> liveData;
        
        SingleUseObserver(Observer<T> observer) {
            this.wrappedObserver = observer;
        }
        
        @Override
        public void onChanged(T t) {
            wrappedObserver.onChanged(t);
            if (liveData != null) {
                liveData.removeObserver(this);
                liveData = null;
            }
        }
        
        // Called by observeForever to set the LiveData reference
        void setLiveData(LiveData<T> liveData) {
            this.liveData = liveData;
        }
    }

    /**
     * Event types for lobby operations
     * Following pattern of using enums instead of multiple LiveData objects
     */
    public enum LobbyEventType {
        LOBBY_CREATED,
        GAME_STARTED,
        LOBBY_LEFT
    }

    public static class LobbyEvent {
        public final LobbyEventType type;
        public final Lobby lobby;
        public final String gameId;

        private LobbyEvent(LobbyEventType type, Lobby lobby, String gameId) {
            this.type = type;
            this.lobby = lobby;
            this.gameId = gameId;
        }

        public static LobbyEvent lobbyCreated(Lobby lobby) {
            return new LobbyEvent(LobbyEventType.LOBBY_CREATED, lobby, null);
        }

        public static LobbyEvent gameStarted(String gameId) {
            return new LobbyEvent(LobbyEventType.GAME_STARTED, null, gameId);
        }

        public static LobbyEvent lobbyLeft() {
            return new LobbyEvent(LobbyEventType.LOBBY_LEFT, null, null);
        }
    }

    @Inject
    public LobbyViewModel(
            LobbyRepository lobbyRepository,
            UserRepository userRepository,
            WebSocketService webSocketService,
            @ApplicationContext Context context) {
        
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        this.settingsManager = new LobbySettingsManager(context);
        
        // Connect WebSocketService and LobbyRepository to break circular dependency
        webSocketService.setLobbyRepository(lobbyRepository);
        Log.i(TAG, "✅ Successfully connected WebSocketService and LobbyRepository via setter injection");
        
        initializeStates();
        setupObservers();
    }

    /**
     * Initialize states with empty values
     * Following pattern of explicit initialization
     */
    private void initializeStates() {
        lobbiesState.setValue(new LobbiesState(null, null, false));
        lobbyState.setValue(new LobbyState(null, null, false));
    }

    /**
     * SIMPLIFIED observer setup - REDUCED from complex nested observers
     * Following pattern of single responsibility observer methods
     */
    private void setupObservers() {
        setupLobbiesObserver();
        setupCurrentLobbyObserver();
    }

    private void setupLobbiesObserver() {
        lobbiesState.addSource(lobbyRepository.getAvailableLobbies(), this::handleLobbiesUpdate);
    }

    private void setupCurrentLobbyObserver() {
        lobbyState.addSource(lobbyRepository.getCurrentLobby(), this::handleCurrentLobbyUpdate);
    }

    /**
     * Handle lobbies list updates with proper error handling
     * Following pattern of null-safe operations and clear logging
     */
    private void handleLobbiesUpdate(List<Lobby> lobbies) {
        if (lobbies == null) {
            Log.d(TAG, "Received null lobbies list");
            lobbiesState.setValue(new LobbiesState(null, "No lobbies available", false));
            return;
        }

        Log.d(TAG, "Updated lobbies list with " + lobbies.size() + " entries");
        LobbiesState currentState = lobbiesState.getValue();
        lobbiesState.setValue(new LobbiesState(
                lobbies,
                null, // Clear any previous error
                false
        ));
    }

    /**
     * Handle current lobby updates with settings application
     * Following pattern of business logic in dedicated methods
     */
    private void handleCurrentLobbyUpdate(Lobby lobby) {
        if (lobby != null) {
            // Apply settings and ensure we have a valid lobby state
            settingsManager.applySettings(lobby);
            
            // More visible logging of player changes for debugging
            Log.i(TAG, "⭐⭐⭐ LOBBY UI UPDATE ⭐⭐⭐");
            Log.i(TAG, "Lobby ID: " + lobby.getLobbyId() + ", Name: " + lobby.getLobbyName());
            
            // Log detailed information about players to help with debugging
            if (lobby.getPlayers() != null) {
                Log.i(TAG, "🔄 PLAYER LIST UPDATE: " + lobby.getLobbyId() + 
                      " with " + lobby.getPlayers().size() + " players:");
                for (User player : lobby.getPlayers()) {
                    Log.i(TAG, "   👤 Player: " + player.getUsername() + " (ID: " + player.getUserId() + ")");
                }
            } else {
                Log.i(TAG, "⚠️ WARNING: Lobby " + lobby.getLobbyId() + " has NO PLAYERS (null list)");
            }
            
            // Create defensive copy of player list to ensure proper UI updates
            if (lobby.getPlayers() != null) {
                // Force a new ArrayList instance to guarantee observer notification
                lobby.setPlayers(new ArrayList<>(lobby.getPlayers()));
            }
        }

        // Always create a completely new LobbyState object to ensure observers detect the change
        LobbyState currentState = lobbyState.getValue();
        Log.i(TAG, "🔔 Setting lobbyState with " + 
              (lobby != null && lobby.getPlayers() != null ? lobby.getPlayers().size() : 0) + " players");
        
        lobbyState.setValue(new LobbyState(
                lobby,
                null, // Clear any previous error
                false
        ));
    }

    /**
     * Refresh lobbies with debounce mechanism
     * SIMPLIFIED from original 30+ lines to focused logic
     */
    public void refreshLobbies() {
        if (!shouldRefresh()) {
            return;
        }

        updateRefreshTime();
        setLoadingState(true);

        Log.d(TAG, "Refreshing lobbies");

        SingleUseObserver<Resource<LobbyListResponse>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);

            if (resource.isError()) {
                handleError("Failed to refresh lobbies: " + resource.getMessage());
            } else if (resource.isSuccess() && resource.getData() != null) {
                // Extract the lobby list from the response and update state
                Log.d(TAG, "Lobbies refreshed successfully");
                LobbyListResponse response = resource.getData();
                if (response != null && response.getLobbies() != null) {
                    handleLobbiesUpdate(response.getLobbies());
                }
                clearError();
            }
        });
        
        // Call the repository method and attach our observer
        LiveData<Resource<LobbyListResponse>> result = lobbyRepository.refreshLobbies();
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Debounce helper methods
     * Following pattern of extracting validation logic
     */
    private boolean shouldRefresh() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime < REFRESH_COOLDOWN_MS) {
            Log.d(TAG, "Skipping refresh - cooldown active");
            return false;
        }
        return true;
    }

    private void updateRefreshTime() {
        lastRefreshTime = System.currentTimeMillis();
    }

    /**
     * Create lobby with simplified validation and error handling
     * REDUCED from 50+ lines to focused logic
     */
    public void createLobby(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        if (!validateLobbyCreation(lobbyName, maxPlayers, numRounds, roundDurationSeconds)) {
            return; // Error already set by validation
        }

        setLoadingState(true);

        SingleUseObserver<Resource<Lobby>> observer = new SingleUseObserver<>(resource -> {
                    setLoadingState(false);

                    if (resource.isSuccess() && resource.getData() != null) {
                        handleLobbyCreated(resource.getData(), numRounds, roundDurationSeconds);
                    } else if (resource.isError()) {
                        handleError("Failed to create lobby: " + resource.getMessage());
                    }
                });
                
        LiveData<Resource<Lobby>> result = lobbyRepository.createLobby(lobbyName, maxPlayers, numRounds, roundDurationSeconds);
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Handle successful lobby creation
     * Following pattern of extracting success handling logic
     */
    private void handleLobbyCreated(Lobby newLobby, int numRounds, int roundDurationSeconds) {
        // Store settings locally
        settingsManager.storeSettings(newLobby.getLobbyId(), numRounds, roundDurationSeconds);

        // Apply settings immediately
        newLobby.setNumRounds(numRounds);
        newLobby.setRoundDurationSeconds(roundDurationSeconds);

        // Trigger event for navigation
        lobbyEvent.setValue(LobbyEvent.lobbyCreated(newLobby));

        Log.d(TAG, "Created lobby: " + newLobby.getLobbyId());
        clearError();
        
        // Force refresh the lobby list without debounce to ensure
        // the new lobby appears immediately in the list
        forceRefreshLobbies();
    }
    
    /**
     * Force refresh lobbies without debounce check
     * Used after creating a lobby to ensure immediate list update
     */
    private void forceRefreshLobbies() {
        // Skip debounce by directly updating refresh time
        updateRefreshTime();
        setLoadingState(true);
        
        Log.d(TAG, "Force refreshing lobbies after lobby creation");
        
        SingleUseObserver<Resource<LobbyListResponse>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);

            if (resource.isError()) {
                // Just log the error but don't show to user since we're auto-refreshing
                Log.e(TAG, "Failed to auto-refresh lobbies: " + resource.getMessage());
            } else if (resource.isSuccess() && resource.getData() != null) {
                LobbyListResponse response = resource.getData();
                if (response != null && response.getLobbies() != null) {
                    handleLobbiesUpdate(response.getLobbies());
                    Log.d(TAG, "Auto-refresh successful, found " + response.getLobbies().size() + " lobbies");
                }
            }
        });
        
        // This is a best-effort operation, we don't need to track the result in UI
        // The server will check if the lobby is actually empty
        LiveData<Resource<LobbyListResponse>> result = lobbyRepository.refreshLobbies();
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Join lobby with simplified error handling
     * REDUCED from 40+ lines to focused logic
     */
    public void joinLobby(String lobbyId) {
        if (lobbyId == null || lobbyId.trim().isEmpty()) {
            handleError("Invalid lobby ID");
            return;
        }

        setLoadingState(true);

        SingleUseObserver<Resource<Lobby>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);

            if (resource.isSuccess() && resource.getData() != null) {
                Log.d(TAG, "Successfully joined lobby: " + lobbyId);
                clearError();
            } else if (resource.isError()) {
                handleError("Failed to join lobby: " + resource.getMessage());
            }
        });
        
        LiveData<Resource<Lobby>> result = lobbyRepository.joinLobby(lobbyId);
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Leave current lobby
     * SIMPLIFIED from complex state management
     */
    public void leaveLobby() {
        setLoadingState(true);

        SingleUseObserver<Resource<Void>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);

            if (resource.isSuccess()) {
                lobbyEvent.setValue(LobbyEvent.lobbyLeft());
                Log.d(TAG, "Left lobby successfully");
                clearError();
            } else if (resource.isError()) {
                handleError("Failed to leave lobby: " + resource.getMessage());
            }
        });
        
        LiveData<Resource<Void>> result = lobbyRepository.leaveLobby();
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Starts a game for the given lobby using WebSocket messaging
     * This method triggers a WebSocket message to start the game instead of creating a persistent
     * game object via REST API. The actual game navigation happens when the start_game WebSocket
     * message is received and processed by WebSocketService.
     * 
     * @param lobbyId ID of the lobby to start a game for
     */
    public void startGame(String lobbyId) {
        if (lobbyId == null) {
            handleError("Cannot start game - no lobby ID");
            return;
        }

        setLoadingState(true);
        Log.d(TAG, "🎮 Starting game for lobby " + lobbyId + " using WebSocket messaging");

        SingleUseObserver<Resource<String>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);

            if (resource.isSuccess() && resource.getData() != null) {
                String tempGameId = resource.getData();
                // Note: For non-host players, we deliberately do not clear the lobby state here.
                // They are expected to wait for a WebSocket game start message to navigate to the game fragment.
                clearError();

                // Trigger game start event for all players. The WebSocket confirmation should override this in non-host clients.
                lobbyEvent.setValue(LobbyEvent.gameStarted(tempGameId));
                Log.d(TAG, "🎲 Game start message sent with temporary ID: " + tempGameId + ". Waiting for WebSocket confirmation.");

                // Schedule a fallback timeout, which will only trigger for the host in case the WebSocket confirmation is delayed.
                scheduleGameStartTimeout(tempGameId);
            } else if (resource.isError()) {
                handleError("Failed to start game: " + resource.getMessage());
            }
        });
        
        LiveData<Resource<String>> result = lobbyRepository.startGame(lobbyId);
        result.observeForever(observer);
        observer.setLiveData(result);
    }
    
    /**
     * Schedules a timeout to check if game start WebSocket message was received
     * This provides an additional safety net for navigation
     */
    private void scheduleGameStartTimeout(String gameId) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Fallback navigation: Only trigger for the host to prevent non-host players from being erroneously navigated away from the lobby
            boolean isHost = false;
            if(getCurrentLobby().getValue() != null && getCurrentUser() != null) {
                isHost = getCurrentLobby().getValue().getHostId().equals(getCurrentUser().getUserId());
            }
            if (isHost && getCurrentLobby().getValue() != null) {
                Log.d(TAG, "⏰ Game start timeout reached for host. Manually triggering navigation as fallback.");
                lobbyEvent.setValue(LobbyEvent.gameStarted(gameId));
            } else {
                // For non-host players, do nothing. They should receive a proper WebSocket message for game start.
                Log.d(TAG, "⏰ Game start timeout reached but current user is not host. No fallback navigation triggered.");
            }
        }, 2000); // 2 second timeout
    }

    /**
     * Update lobby settings
     * SIMPLIFIED validation and error handling
     */
    public void updateLobbySettings(int numRounds, int roundDurationSeconds) {
        Lobby currentLobby = getCurrentLobby().getValue();
        if (currentLobby == null) {
            handleError("No current lobby to update");
            return;
        }

        if (!validateSettings(numRounds, roundDurationSeconds)) {
            return; // Error already set
        }

        setLoadingState(true);

        SingleUseObserver<Resource<Lobby>> observer = new SingleUseObserver<>(resource -> {
                    setLoadingState(false);

                    if (resource.isSuccess()) {
                        Log.d(TAG, "Updated lobby settings successfully");
                        clearError();
                    } else if (resource.isError()) {
                        handleError("Failed to update settings: " + resource.getMessage());
                    }
                });
                
        LiveData<Resource<Lobby>> result = lobbyRepository.updateLobbySettings(numRounds, roundDurationSeconds);
        result.observeForever(observer);
        observer.setLiveData(result);
    }

    /**
     * Attempts to delete a lobby if it's empty
     * Thread-safe implementation with proper observer lifecycle management
     * @param lobbyId ID of the lobby to potentially delete
     */
    public void deleteLobbyIfEmpty(String lobbyId) {
        if (lobbyId == null) {
            Log.w(TAG, "Cannot delete null lobby");
            return;
        }
        
        // Use SingleUseObserver for proper lifecycle management
        SingleUseObserver<Resource<Boolean>> observer = new SingleUseObserver<>(resource -> {
            if (resource.isSuccess()) {
                Log.d(TAG, "Empty lobby deletion request sent: " + lobbyId);
            } else {
                Log.w(TAG, "Failed to delete empty lobby: " + resource.getMessage());
            }
        });
        
        // This is a best-effort operation, we don't need to track the result in UI
        // The server will check if the lobby is actually empty
        LiveData<Resource<Boolean>> result = lobbyRepository.deleteLobbyIfEmpty(lobbyId);
        result.observeForever(observer);
        observer.setLiveData(result);
    }
    
    /**
     * Get detailed information about a specific lobby
     * 
     * @param lobbyId ID of the lobby to fetch details for
     */
    public void getLobbyDetails(String lobbyId) {
        if (lobbyId == null) {
            Log.w(TAG, "Cannot get details for null lobby ID");
            handleError("Invalid lobby ID");
            return;
        }
        
        setLoadingState(true);
        
        SingleUseObserver<Resource<Lobby>> observer = new SingleUseObserver<>(resource -> {
            setLoadingState(false);
            
            if (resource.isSuccess() && resource.getData() != null) {
                Log.d(TAG, "Successfully retrieved lobby details for: " + lobbyId);
                handleCurrentLobbyUpdate(resource.getData());
                clearError();
            } else if (resource.isError()) {
                handleError("Failed to get lobby details: " + resource.getMessage());
            }
        });
        
        LiveData<Resource<Lobby>> result = lobbyRepository.getLobbyById(lobbyId);
        result.observeForever(observer);
        observer.setLiveData(result);
    }


    /**
     * Comprehensive lobby creation validation
     * Following pattern of single validation method with early returns
     */
    private boolean validateLobbyCreation(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        if (lobbyName == null || lobbyName.trim().isEmpty()) {
            handleError("Lobby name cannot be empty");
            return false;
        }

        if (maxPlayers < 2 || maxPlayers > 10) {
            handleError("Max players must be between 2 and 10");
            return false;
        }

        return validateSettings(numRounds, roundDurationSeconds);
    }

    /**
     * Settings validation with clear error messages
     * Following pattern of focused validation methods
     */
    private boolean validateSettings(int numRounds, int roundDurationSeconds) {
        if (numRounds < 1 || numRounds > 10) {
            handleError("Number of rounds must be between 1 and 10");
            return false;
        }

        if (roundDurationSeconds < 30 || roundDurationSeconds > 300) {
            handleError("Round duration must be between 30 and 300 seconds");
            return false;
        }

        return true;
    }


    /**
     * Centralized loading state management
     * Following pattern of single responsibility methods
     */
    private void setLoadingState(boolean loading) {
        isLoading.setValue(loading);

        // Update states to reflect loading
        if (loading) {
            LobbiesState currentLobbiesState = lobbiesState.getValue();
            if (currentLobbiesState != null) {
                lobbiesState.setValue(new LobbiesState(
                        currentLobbiesState.getLobbies(),
                        null, // Clear error during loading
                        true
                ));
            }

            LobbyState currentLobbyState = lobbyState.getValue();
            if (currentLobbyState != null) {
                lobbyState.setValue(new LobbyState(
                        currentLobbyState.getLobby(),
                        null, // Clear error during loading
                        true
                ));
            }
        }
    }

    /**
     * Centralized error handling
     * Following pattern of consistent error management
     */
    private void handleError(String message) {
        Log.e(TAG, "Error: " + message);
        errorMessage.setValue(message);

        // Update states with error
        LobbiesState currentLobbiesState = lobbiesState.getValue();
        if (currentLobbiesState != null) {
            lobbiesState.setValue(new LobbiesState(
                    currentLobbiesState.getLobbies(),
                    message,
                    false
            ));
        }

        LobbyState currentLobbyState = lobbyState.getValue();
        if (currentLobbyState != null) {
            lobbyState.setValue(new LobbyState(
                    currentLobbyState.getLobby(),
                    message,
                    false
            ));
        }
    }

    private void clearError() {
        errorMessage.setValue(null);
    }


    /**
     * Set a callback for lobby update events
     * This properly manages the callback lifecycle
     * @param callback The callback to be notified of lobby updates
     */
    public void setLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        Log.d(TAG, "Setting lobby update callback in ViewModel: " + (callback != null ? "provided" : "null"));
        
        // If callback is null, just clear the current one
        if (callback == null) {
            if (this.currentCallback != null) {
                lobbyRepository.removeLobbyUpdateCallback(this.currentCallback);
                this.currentCallback = null;
            }
            return;
        }
        
        // Create a wrapper callback that properly implements ALL methods of the interface
        WebSocketService.LobbyUpdateCallback wrapperCallback = new WebSocketService.LobbyUpdateCallback() {
            @Override
            public void onLobbyStateChanged(LobbyStateMessage message) {
                // Forward to the original callback
                if (callback != null) {
                    callback.onLobbyStateChanged(message);
                }
            }
            
            @Override
            public void onLobbiesUpdated(LobbiesUpdateMessage message) {
                // Forward to the original callback
                callback.onLobbiesUpdated(message);

                Log.d(TAG, "ViewModel wrapper received and forwarded lobbies_update message");
            }
            
            @Override
            public void onGameStateChanged(GameStateMessage message) {
                // Forward to the original callback
                callback.onGameStateChanged(message);

                // Handle game state changes for ALL players (host and non-host)
                if (message != null && message.getGamePayload() != null && 
                    message.getGamePayload().getGame() != null) {
                    
                    Game game = message.getGamePayload().getGame();
                    String gameId = game.getGameId();
                    
                    if (!gameId.isEmpty()) {
                        Log.i(TAG, "⭐ Game state WebSocket message received in wrapper for game: " + gameId);
                        
                        // Check for explicit "started" event OR active game state
                        String event = message.getGamePayload().getEvent();
                        Game.GameState gameState = game.getGameState();
                        
                        // Handle both explicit "started" events AND games with ACTIVE state
                        boolean isGameStarting = ("started".equals(event)) || 
                                               (gameState == Game.GameState.ACTIVE);
                        
                        if (isGameStarting) {
                            Log.i(TAG, "🎮 Game starting detected! Event: " + event + 
                                  ", State: " + gameState + ", GameId: " + gameId);
                                  
                            // Trigger navigation to game screen for all players including non-hosts
                            // This ensures ALL players navigate to the game when it starts
                            lobbyEvent.postValue(LobbyEvent.gameStarted(gameId));
                            Log.i(TAG, "Set GAME_STARTED event with gameId: " + gameId + 
                                  " (player navigation via WebSocket)");
                        } else {
                            Log.d(TAG, "Game state update received but not a game start. Event: " + 
                                  event + ", State: " + gameState);
                        }
                    }
                }
            }
            
            @Override
            public void onError(String errorMessage) {
                // Forward to the original callback
                callback.onError(errorMessage);
            }
        };
        
        // Store the wrapper callback reference for lifecycle management
        this.currentCallback = wrapperCallback;
        
        // Register wrapper with repository
        lobbyRepository.setLobbyUpdateCallback(wrapperCallback);
        Log.d(TAG, "Registered wrapper callback with repository");
    }
    
    /**
     * Called when ViewModel is cleared, unregisters any active callbacks
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel onCleared, removing WebSocket callback");
        
        // Clean up the callback when ViewModel is destroyed
        if (currentCallback != null) {
            lobbyRepository.removeLobbyUpdateCallback(currentCallback);
            currentCallback = null;
        }
    }

    /**
     * Process lobby update from WebSocket message
     * Ensures both repository and local state are updated
     * @param lobbyData JSON string containing lobby update data
     */
    public void processLobbyUpdate(String lobbyData) {
        Log.d(TAG, "ViewModel processing lobby update: " + lobbyData);
        
        // Let repository process the update first (this updates the repository's LiveData)
        lobbyRepository.processLobbyUpdate(lobbyData);
        
        // Now refresh our local LiveData by forcing a refresh of the current lobby
        Lobby currentLob = getCurrentLobby().getValue();
        if (currentLob != null) {
            Log.d(TAG, "Refreshing current lobby details after WebSocket update: " + currentLob.getLobbyId());
            getLobbyDetails(currentLob.getLobbyId());
        } else {
            Log.d(TAG, "No current lobby to refresh after WebSocket update");
        }
    }

    public LiveData<LobbiesState> getLobbiesState() {
        return lobbiesState;
    }

    public LiveData<LobbyState> getLobbyState() {
        return lobbyState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<LobbyEvent> getLobbyEvent() {
        return lobbyEvent;
    }
    
    /**
     * Retrieve the current game ID from the repository
     * Used to ensure consistent game transitions for all players
     *
     * @return The current game ID or null if not set
     */
    public String getCurrentGameId() {
        return lobbyRepository != null ? lobbyRepository.getCurrentGameId() : null;
    }

    public LiveData<Lobby> getCurrentLobby() {
        return lobbyRepository.getCurrentLobby();
    }

    public User getCurrentUser() {
        return userRepository.getCurrentUser().getValue();
    }

    /**
     * Reset event to prevent re-triggering
     * Following pattern of explicit state reset methods
     */
    public void resetEvent() {
        lobbyEvent.setValue(null);
    }

    /**
     * Helper method to determine if the current user is the host of the lobby
     * @return true if the current user is the host, false otherwise
     */
    public boolean isHost() {
        // Get current lobby state from LiveData (assuming lobbyState holds LobbyState that includes the Lobby object)
        Lobby lobby = null;
        if (lobbyState.getValue() != null) {
            lobby = lobbyState.getValue().getLobby();
        }
        
        if (lobby == null) {
            Log.w(TAG, "isHost: No current lobby available");
            return false;
        }
        
        // Assuming the Lobby object provides a getHostId() method and userRepository has getCurrentUser()
        if (userRepository.getCurrentUser() == null || userRepository.getCurrentUser().getValue() == null) {
            Log.w(TAG, "isHost: No current user available");
            return false;
        }
        
        String currentUserId = userRepository.getCurrentUser().getValue().getUserId();
        boolean hostStatus = lobby.getHostId() != null && lobby.getHostId().equals(currentUserId);
        Log.d(TAG, "isHost: Returning " + hostStatus + " for lobby hostId: " + lobby.getHostId() + ", currentUserId: " + currentUserId);
        return hostStatus;
    }
}
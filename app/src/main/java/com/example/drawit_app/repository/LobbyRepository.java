package com.example.drawit_app.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;


import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.LobbyDao;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import java.io.IOException;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.request.CreateLobbyRequest;
import com.example.drawit_app.network.request.JoinLobbyRequest;
import com.example.drawit_app.network.response.LobbyListResponse;
import com.example.drawit_app.util.AppExecutors;
import com.example.drawit_app.util.LobbySettingsManager;
import com.example.drawit_app.util.WebSocketMessageConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

/**
 * Repository for lobby-related operations with WebSocket support
 * for real-time updates and local caching to reduce API calls
 */
@Singleton
public class LobbyRepository extends BaseRepository {

    // Required by BaseRepository
    @Override
    public void onFailure(retrofit2.Call<com.example.drawit_app.network.response.ApiResponse<com.example.drawit_app.network.response.LobbyListResponse>> call, Throwable t) {
        Log.e(TAG, "LobbyListResponse API call failed: " + t.getMessage(), t);
        //setError("Failed to load lobbies: " + t.getMessage()); // Example of using setError from BaseRepository
    }

    private static final String TAG = "LobbyRepository";
    private static final long CACHE_EXPIRY_MS = 10000; // 10 seconds cache expiry

    // Core dependencies
    private final ApiService apiService;
    private final LobbyDao lobbyDao;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    // Using AppExecutors for thread handling
    private final AppExecutors appExecutors;
    private final LobbySettingsManager settingsManager;
    private final WebSocketMessageConverter messageConverter;

    // LiveData state
    private final MediatorLiveData<List<Lobby>> availableLobbies = new MediatorLiveData<>();
    private final MutableLiveData<Lobby> currentLobby = new MutableLiveData<>();

    // WebSocket callback
    private WebSocketService.LobbyUpdateCallback externalCallback;

    // Cache for lobby data to reduce API calls
    private final Map<String, CachedLobby> lobbyCache = new HashMap<>();

    /**
     * Class to hold cached lobby data with timestamp
     */
    private static class CachedLobby {
        final Lobby lobby;
        final long timestamp;

        CachedLobby(Lobby lobby) {
            this.lobby = lobby;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }

        /**
         * Get the cached lobby object
         * @return The lobby object
         */
        Lobby getLobby() {
            return lobby;
        }
    }

    @Inject
    public LobbyRepository(ApiService apiService, DrawItDatabase database,
                           UserRepository userRepository, WebSocketService webSocketService,
                           Context context, WebSocketMessageConverter messageConverter,
                           AppExecutors appExecutors) {
        this.apiService = apiService;
        this.lobbyDao = database.lobbyDao();
        this.userRepository = userRepository;
        this.webSocketService = webSocketService;
        this.settingsManager = new LobbySettingsManager(context);
        this.messageConverter = messageConverter;
        this.appExecutors = appExecutors;

        // Set up WebSocket connection for real-time updates
        setupWebSocketCallback();

        // Observe local database for available lobbies
        availableLobbies.addSource(lobbyDao.getAvailableLobbies(), lobbiesFromDb -> {
            if (lobbiesFromDb != null) {
                Log.d(TAG, "LobbyDao emitted " + lobbiesFromDb.size() + " lobbies (main constructor). Enriching from cache.");
                List<Lobby> resultLobbies = new ArrayList<>();
                for (Lobby dbLobby : lobbiesFromDb) {
                    CachedLobby cachedEntry = lobbyCache.get(dbLobby.getLobbyId());
                    // Use cached entry if it's valid and, crucially, if its players list is not null
                    // (since that's the whole point of using the cache here)
                    if (cachedEntry != null && !cachedEntry.isExpired() && cachedEntry.lobby.getPlayers() != null) {
                        Log.d(TAG, "Using cached version for lobby: " + dbLobby.getLobbyId() +
                                   ", players: " + cachedEntry.lobby.getPlayers().size() +
                                   ", name: " + cachedEntry.lobby.getLobbyName());
                        resultLobbies.add(cachedEntry.lobby); // Add the cached version
                    } else {
                        // No valid/complete cache entry, use the DB version.
                        Log.d(TAG, "No valid/complete cache for lobby: " + dbLobby.getLobbyId() + ". Using DB version. Name: " + dbLobby.getLobbyName());
                        settingsManager.applySettings(dbLobby);
                        ensureHostInPlayerList(dbLobby);
                        resultLobbies.add(dbLobby);
                    }
                }
                availableLobbies.setValue(resultLobbies);
            } else {
                Log.d(TAG, "LobbyDao emitted null list (main constructor), setting availableLobbies to empty list.");
                availableLobbies.setValue(new ArrayList<>());
            }
        });
    }

    /**
     * Set up WebSocket callback for real-time lobby updates
     * This optimized version ensures WebSocket messages are properly processed
     * and updates the cache to prevent redundant API calls
     */
    private void setupWebSocketCallback() {
        Log.d(TAG, "Setting up WebSocket callback");

        // CRITICAL FIX: Set the authentication token before connecting
        String authToken = userRepository.getAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            Log.i(TAG, "🔑 Setting WebSocket auth token: " +
                  (authToken.length() > 5 ? authToken.substring(0, 5) + "..." : "[short token]"));
            webSocketService.setAuthToken(authToken);
        } else {
            Log.w(TAG, "⚠️ No valid auth token available for WebSocket connection");
        }

        // Make sure WebSocket is connected (this will use the auth token we just set)
        if (!webSocketService.isConnected()) {
            webSocketService.connect();
        }

        webSocketService.setLobbyUpdateCallback(new WebSocketService.LobbyUpdateCallback() {
            @Override
            public void onLobbiesUpdated(com.example.drawit_app.network.message.LobbiesUpdateMessage message) {
                if (message != null && message.getLobbiesPayload().getLobbies() != null) {
                    Log.d(TAG, "Received lobby list update via WebSocket with " + message.getLobbiesPayload().getLobbies().size() + " lobbies");

                    // Process the updated lobby list
                    appExecutors.diskIO().execute(() -> {
                        // Use the WebSocketMessageConverter to safely convert the raw lobby objects to Lobby instances
                        List<Lobby> updatedLobbies = messageConverter.convertToLobbyList(message.getLobbiesPayload().getLobbies());
                        Log.d(TAG, "Successfully converted " + updatedLobbies.size() + " lobbies using WebSocketMessageConverter");

                        // Update each lobby in the database
                        for (Lobby lobby : updatedLobbies) {
                            // Apply local settings
                            settingsManager.applySettings(lobby);
                            // Make sure the host is in the player list
                            ensureHostInPlayerList(lobby);
                            // Update the cache
                            lobbyCache.put(lobby.getLobbyId(), new CachedLobby(lobby));
                            // Save to database
                            lobbyDao.insert(lobby);
                        }

                        // Update the available lobbies list on the main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Log.d(TAG, "Updating availableLobbies LiveData with " + updatedLobbies.size() + " lobbies");
                            availableLobbies.setValue(updatedLobbies);
                        });
                    });

                    // Forward to external callback if present
                    if (externalCallback != null) {
                        externalCallback.onLobbiesUpdated(message);
                    }
                }
            }

            @Override
            public void onLobbyStateChanged(LobbyStateMessage message) {
                if (message != null && message.getLobbyPayload() != null &&
                    message.getLobbyPayload().getLobby() != null) {

                    Lobby updatedLobby = message.getLobbyPayload().getLobby();
                    String lobbyId = updatedLobby.getLobbyId();
                    String event = message.getLobbyPayload().getEvent();

                    // Enhanced logging with emoji for visibility and consistent format
                    Log.i(TAG, "🔔 WEBSOCKET EVENT: " + (event != null ? event : "update") +
                          " for lobby " + lobbyId + " (" + updatedLobby.getLobbyName() + ")");

                    // Log detailed timestamp for correlation with server logs
                    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                        .format(new java.util.Date());
                    Log.i(TAG, "⏰ WebSocket message received at: " + timestamp);

                    // Log player list details with clear formatting
                    if (updatedLobby.getPlayers() != null) {
                        Log.i(TAG, "👥 PLAYERS LIST FROM WEBSOCKET: " + updatedLobby.getPlayers().size() +
                              " players in lobby " + updatedLobby.getLobbyId());
                        for (User player : updatedLobby.getPlayers()) {
                            Log.i(TAG, "   👤 Player: " + player.getUsername() +
                                  " (ID: " + player.getUserId() + ")");
                        }
                    } else {
                        Log.e(TAG, "⚠️ WEBSOCKET ERROR: Lobby " + updatedLobby.getLobbyId() +
                              " has NULL players list! This indicates a potential server issue.");
                    }

                    // DEBUG: Log the lobby name received from WebSocket
                    Log.d(TAG, "DEBUG: WebSocket lobby received - ID: " + lobbyId +
                          ", Raw name value: " + updatedLobby.getLobbyName() +
                          ", Name null? " + (updatedLobby.getLobbyName() == null));

                    // Ensure lobby name is preserved
                    if (updatedLobby.getLobbyName() == null || updatedLobby.getLobbyName().isEmpty()) {
                        // Try to find existing name in cache or database
                        CachedLobby cachedLobby = lobbyCache.get(lobbyId);
                        if (cachedLobby != null && cachedLobby.lobby.getLobbyName() != null &&
                            !cachedLobby.lobby.getLobbyName().isEmpty()) {
                            updatedLobby.setLobbyName(cachedLobby.lobby.getLobbyName());
                            Log.d(TAG, "Restoring lobby name from cache: " + cachedLobby.lobby.getLobbyName());
                        }
                    }

                    Log.d(TAG, "WebSocket update received for lobby: " + lobbyId +
                          ", name: " + updatedLobby.getLobbyName() +
                          ", players: " + (updatedLobby.getPlayers() != null ? updatedLobby.getPlayers().size() : 0) +
                          ", event: " + (event != null ? event : "update"));

                    // Handle empty lobbies or lobby deletion events
                    if ("deleted".equals(event) ||
                        updatedLobby.getPlayers() == null ||
                        updatedLobby.getPlayers().isEmpty()) {

                        Log.d(TAG, "Empty or deleted lobby detected: " + lobbyId);
                        Log.i(TAG, "LOBBY DELETED or EMPTY - Event type: " + (event != null ? event : "unknown"));
                        appExecutors.diskIO().execute(() -> {
                            // Remove from cache
                            lobbyCache.remove(lobbyId);

                            // Remove from database
                            lobbyDao.deleteLobbyById(lobbyId);

                            // Clear current lobby if it's the deleted one
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> {
                                Lobby current = currentLobby.getValue();
                                if (current != null && current.getLobbyId().equals(lobbyId)) {
                                    Log.i(TAG, "Lobby deleted event - clearing current lobby in UI");
                                    currentLobby.postValue(null);
                                }
                            });
                        });
                        return;
                    }

                    // Fix player count by ensuring the host is included in the player list
                    ensureHostInPlayerList(updatedLobby);

                    // Apply locally stored settings
                    settingsManager.applySettings(updatedLobby);

                    // Update the cache
                    lobbyCache.put(lobbyId, new CachedLobby(updatedLobby));

                    // Handle specific events
                    if ("settings_updated".equals(event)) {
                        Log.d(TAG, "Lobby settings updated: rounds=" + updatedLobby.getNumRounds() +
                              ", duration=" + updatedLobby.getRoundDurationSeconds());

                        // Store updated settings locally
                        settingsManager.storeSettings(lobbyId,
                                                    updatedLobby.getNumRounds(),
                                                    updatedLobby.getRoundDurationSeconds());
                    }

                    // Update local database
                    appExecutors.diskIO().execute(() -> {
                        // DEBUG: Log before inserting into database
                        Log.d(TAG, "DEBUG: Before DB insert - ID: " + updatedLobby.getLobbyId() +
                              ", Name: " + updatedLobby.getLobbyName());
                        lobbyDao.insert(updatedLobby);

                        // Check what was actually saved
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            Lobby savedLobby = lobbyDao.getLobbyByIdDirect(updatedLobby.getLobbyId());
                            if (savedLobby != null) {
                                Log.d(TAG, "DEBUG: After DB insert - ID: " + savedLobby.getLobbyId() +
                                      ", Name: " + savedLobby.getLobbyName() +
                                      ", Name null? " + (savedLobby.getLobbyName() == null));
                            } else {
                                Log.d(TAG, "DEBUG: After DB insert - Lobby not found in DB");
                            }
                        }, 100);
                    });

                    // Update current lobby if it's the same one
                    Lobby currentLobbyValue = currentLobby.getValue();
                    if (currentLobbyValue != null && currentLobbyValue.getLobbyId().equals(lobbyId)) {
                        // Check if this update is for a player joining/leaving
                        if (event != null && (event.equals("player_joined") || event.equals("player_left"))) {
                            Log.i(TAG, "🎮 Player " + event.replace("player_", "") + " event detected in " + updatedLobby.getLobbyName());

                            // Prefetch host user data to avoid "Unknown Host" display in UI
                            if (updatedLobby.getHostId() != null) {
                                String hostId = updatedLobby.getHostId();
                                Log.d(TAG, "🔍 Pre-fetching host data for ID: " + hostId);
                                // This call will cache the host data for future use
                                userRepository.getUserById(hostId);
                            }
                        }

                        // Broadcast on main thread to ensure UI updates properly
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> {
                            // Create a defensive copy to ensure observers detect the change
                            Lobby updatedCopy = new Lobby();
                            updatedCopy.setLobbyId(updatedLobby.getLobbyId());

                            // Lobby name fix: ensure we have a proper name
                            String lobbyName = updatedLobby.getLobbyName();
                            if ((lobbyName == null || lobbyName.isEmpty()) && currentLobbyValue.getLobbyName() != null) {
                                // Preserve existing lobby name if the update doesn't have one
                                lobbyName = currentLobbyValue.getLobbyName();
                                Log.d(TAG, "Preserving existing lobby name: " + lobbyName);
                            }
                            updatedCopy.setLobbyName(lobbyName);

                            // Host handling - check if we have both ID and User object
                            updatedCopy.setHostId(updatedLobby.getHostId());
                            if (updatedLobby.getHost() != null) {
                                updatedCopy.setHostId(updatedLobby.getHostId());
                            } else if (currentLobbyValue.getHost() != null) {
                                updatedCopy.setHostId(currentLobbyValue.getHostId());
                            }

                            // Copy other properties
                            updatedCopy.setMaxPlayers(updatedLobby.getMaxPlayers());
                            updatedCopy.setNumRounds(updatedLobby.getNumRounds());
                            updatedCopy.setRoundDurationSeconds(updatedLobby.getRoundDurationSeconds());
                            updatedCopy.setLocked(updatedLobby.isLocked());

                            // SIMPLIFIED: Player list handling - trust the server's player list for correctness
                            // This eliminates complex merging logic that could cause inconsistencies
                            if (updatedLobby.getPlayers() != null) {
                                // Log incoming player list
                                Log.i(TAG, "📋 LOBBY UPDATE: Received player list with " +
                                      updatedLobby.getPlayers().size() + " players");

                                // For player events specifically (join/leave), add extra detailed logging
                                boolean isPlayerEvent = event != null &&
                                    (event.equals("player_joined") || event.equals("player_left"));

                                if (isPlayerEvent) {
                                    Log.i(TAG, "👤 PLAYER EVENT: " + event + " in lobby " + lobbyId);
                                    Log.i(TAG, "🔄 Player list BEFORE: " +
                                          (currentLobbyValue.getPlayers() != null ?
                                           currentLobbyValue.getPlayers().size() : 0) + " players");
                                    Log.i(TAG, "🔄 Player list AFTER: " +
                                          updatedLobby.getPlayers().size() + " players");

                                    if (event.equals("player_joined")) {
                                        // Try to identify the new player
                                        if (currentLobbyValue.getPlayers() != null) {
                                            for (User newPlayer : updatedLobby.getPlayers()) {
                                                boolean isNew = true;
                                                for (User existingPlayer : currentLobbyValue.getPlayers()) {
                                                    if (existingPlayer.getUserId().equals(newPlayer.getUserId())) {
                                                        isNew = false;
                                                        break;
                                                    }
                                                }
                                                if (isNew) {
                                                    Log.i(TAG, "✅ NEW PLAYER JOINED: " +
                                                          newPlayer.getUsername() +
                                                          " (ID: " + newPlayer.getUserId() + ")");
                                                }
                                            }
                                        }
                                    } else {
                                        // Try to identify who left
                                        if (currentLobbyValue.getPlayers() != null) {
                                            for (User existingPlayer : currentLobbyValue.getPlayers()) {
                                                boolean stillPresent = false;
                                                for (User remainingPlayer : updatedLobby.getPlayers()) {
                                                    if (existingPlayer.getUserId().equals(remainingPlayer.getUserId())) {
                                                        stillPresent = true;
                                                        break;
                                                    }
                                                }
                                                if (!stillPresent) {
                                                    Log.i(TAG, "❌ PLAYER LEFT: " +
                                                          existingPlayer.getUsername() +
                                                          " (ID: " + existingPlayer.getUserId() + ")");
                                                }
                                            }
                                        }
                                    }
                                }

                                Log.i(TAG, "📊 UPDATING UI with latest player list: " +
                                      updatedLobby.getPlayers().size() + " players");
                                updatedCopy.setPlayers(new ArrayList<>(updatedLobby.getPlayers()));

                            } else if (currentLobbyValue.getPlayers() != null) {
                                // Only if server sent null players (error case), preserve existing list
                                Log.w(TAG, "⚠️ Server sent null player list, preserving current list");
                                updatedCopy.setPlayers(new ArrayList<>(currentLobbyValue.getPlayers()));
                            } else {
                                // Initialize empty list if both are null
                                updatedCopy.setPlayers(new ArrayList<>());
                            }

                            // Make sure host is in player list
                            ensureHostInPlayerList(updatedCopy);

                            int finalPlayerCount = updatedCopy.getPlayers() != null ? updatedCopy.getPlayers().size() : 0;
                            Log.i(TAG, "Updating current lobby UI for ALL users with players: " + finalPlayerCount);

                            // Force a new instance to guarantee observers detect the change
                            // This ensures ALL users in the lobby (not just the host) see updated player lists
                            // ALWAYS use postValue for safety - even though we're on main thread, this ensures thread safety
                            Log.d(TAG, "UPDATING UI: Setting current lobby with " + finalPlayerCount + " players");
                            currentLobby.postValue(updatedCopy);
                        });
                    }

                    // Forward to external callback if present
                    if (externalCallback != null) {
                        externalCallback.onLobbyStateChanged(message);
                    }
                }
            }

            @Override
            public void onGameStateChanged(GameStateMessage message) {
                if (message != null && message.getGamePayload() != null && message.getGamePayload().getGame() != null) {
                    String gameId = message.getGamePayload().getGame().getGameId();
                    String event = message.getGamePayload().getEvent();

                    Log.i(TAG, "🎮 GAME STATE EVENT: " + (event != null ? event : "update") +
                          " for game " + gameId);

                    // Log detailed timestamp for game events
                    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                        .format(new java.util.Date());
                    Log.i(TAG, "⏰ Game state update received at: " + timestamp);

                    // Forward to external callback if present
                    if (externalCallback != null) {
                        externalCallback.onGameStateChanged(message);
                    }
                } else {
                    Log.e(TAG, "⚠️ Received null or invalid game state message");
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "WebSocket error: " + errorMessage);

                // Try to reconnect if disconnected
                if (!webSocketService.isConnected()) {
                    Log.d(TAG, "Attempting to reconnect WebSocket after error");
                    webSocketService.connect();
                }

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

        apiService.getLobbies("Bearer " + token).enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<LobbyListResponse>> call, retrofit2.Response<ApiResponse<LobbyListResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    LobbyListResponse lobbyListResponse = response.body().getData();
                    List<Lobby> lobbies = null;
                    if (lobbyListResponse != null) {
                        lobbies = lobbyListResponse.getLobbies();
                    }

                    if (lobbies != null && !lobbies.isEmpty()) {
                        Log.i(TAG, "Fetched " + lobbies.size() + " lobbies from server (in refreshLobbies).");
                        for (Lobby lobby : lobbies) {
                            if (lobby.getPlayers() != null) {
                                Log.i(TAG, "  Lobby " + lobby.getLobbyId() + " players (" + lobby.getPlayers().size() + ") from SERVER:");
                                for (User player : lobby.getPlayers()) {
                                    Log.i(TAG, "    Player ID: " + player.getUserId() + ", Username: " + player.getUsername());
                                }
                            } else {
                                Log.i(TAG, "  Lobby " + lobby.getLobbyId() + " has NULL players list from SERVER.");
                            }
                        }
                    } else {
                        Log.i(TAG, "Fetched lobby list from server is null or empty (in refreshLobbies).");
                    }

                    // Ensure 'lobbies' is used consistently hereafter, checking for null if necessary
                    if (lobbies == null) {
                        lobbies = new ArrayList<>(); // Avoid NPEs further down if server response was strange
                    }
                    Log.d(TAG, "Processing " + lobbies.size() + " lobbies from server");

                    // DEBUG: Print out details of each lobby from API
                    for (Lobby lobby : lobbies) {
                        Log.d(TAG, "DEBUG: API lobby - ID: " + lobby.getLobbyId() +
                                ", Name: " + lobby.getLobbyName() +
                                ", JSON annotation working? " + (lobby.getLobbyName() != null));
                    }

                    // Update database in background
                    List<Lobby> finalLobbies = lobbies; // effectively final for lambda
                    appExecutors.diskIO().execute(() -> {
                        lobbyDao.deleteAllLobbiesDirectly();
                        for (Lobby lobby : finalLobbies) {
                            // Apply any local settings before storing
                            settingsManager.applySettings(lobby);
                            lobbyDao.insert(lobby);
                        }
                    });

                    result.postValue(Resource.success(response.body().getData()));
                } else {
                    String errorMsg = "Failed to get lobbies";
                    if (response.body() != null && response.body().getMessage() != null) {
                        errorMsg = response.body().getMessage();
                    } else if (!response.isSuccessful()) {
                        try {
                            errorMsg = "HTTP " + response.code() + ": " + response.errorBody().string();
                        } catch (IOException e) {
                            errorMsg = "HTTP " + response.code() + ": Error reading error body";
                        }
                    }
                    Log.e(TAG, "Refresh lobbies error: " + errorMsg);
                    result.postValue(Resource.error(errorMsg, null));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<LobbyListResponse>> call, Throwable t) {
                Log.e(TAG, "Network error refreshing lobbies: " + t.getMessage(), t);
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });

        return result;
    }

// ...

/**
 * Ensure the host is properly included in the player list
 * @param lobby The lobby to check and update
 */
private void ensureHostInPlayerList(Lobby lobby) {
    if (lobby == null || lobby.getHostId() == null) {
        Log.d(TAG, "ensureHostInPlayerList: lobby or hostId is null, returning.");
        return;
    }

    // Initialize player list if null
    if (lobby.getPlayers() == null) {
        lobby.setPlayers(new ArrayList<>());
        Log.d(TAG, "Initialized empty player list for lobby: " + lobby.getLobbyId());
    }

    // Log current state for debugging
    Log.d(TAG, "Checking host in player list for lobby: " + lobby.getLobbyId() + ", host: " + lobby.getHostId() + ", current player count: " + lobby.getPlayers().size());

    // Check if host is already in the player list
    boolean hostFound = false;
    for (User player : lobby.getPlayers()) {
        if (player != null && player.getUserId().equals(lobby.getHostId())) {
            hostFound = true;
            Log.d(TAG, "Host already in player list: " + player.getUsername() + " (ID: " + player.getUserId() + ")");
            break;
        }
    }

    // Only add the host if they're not already in the list
    if (!hostFound) {
        // First try to get host from lobby.getHost()
        User hostUser = lobby.getHost();

        // If host object doesn't exist or doesn't have userId matching hostId, create a minimal host user
        if (hostUser == null || !hostUser.getUserId().equals(lobby.getHostId())) {
            // Create a minimal host user with the ID and some basic info
            hostUser = new User();
            hostUser.setUserId(lobby.getHostId());

            // Add a placeholder username to make it visible in UI
            hostUser.setUsername("Host (" + lobby.getHostId().substring(0, 5) + "...)");

            // Add a default avatar URL
            hostUser.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=host");

            Log.d(TAG, "Created enhanced host user with ID: " + lobby.getHostId() + ", username: " + hostUser.getUsername());
        }

        // Add host to player list
        lobby.getPlayers().add(hostUser);
        Log.d(TAG, "Added host to player list with ID: " + hostUser.getUserId());
    }

    // Log final player list
    Log.d(TAG, "Final player list for lobby " + lobby.getLobbyId() + " has " +
          lobby.getPlayers().size() + " players (including host)");
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
        appExecutors.diskIO().execute(() -> {
            try {
                Log.d(TAG, "Creating lobby: " + lobbyName + ", rounds: " + numRounds +
                      ", duration: " + roundDurationSeconds);

                // Make synchronous API call
                retrofit2.Response<ApiResponse<Lobby>> response =
                    apiService.createLobby("Bearer " + token, request).execute();

                if (response.isSuccessful() && response.body() != null &&
                    response.body().isSuccess() && response.body().getData() != null) {

                    Lobby newLobby = response.body().getData();

                    // Store settings locally since server doesn't save them
                    settingsManager.storeSettings(newLobby.getLobbyId(), numRounds, roundDurationSeconds);

                    // Apply settings directly to returned lobby object
                    newLobby.setNumRounds(numRounds);
                    newLobby.setRoundDurationSeconds(roundDurationSeconds);

                    // CRITICAL: Make sure the host is in the player list
                    Log.d(TAG, "Adding host to player list for newly created lobby: " + newLobby.getLobbyId());
                    ensureHostInPlayerList(newLobby);

                    // Handle specific events
                    Log.i(TAG, " Player list changed event detected: " + "lobby_created" + " for lobby " + newLobby.getLobbyName());

                    // Pre-fetch host user data to improve UI experience
                    if (newLobby.getHostId() != null) {
                        // Move the user pre-fetching to the main thread to avoid IllegalStateException
                        String hostId = newLobby.getHostId();
                        Log.d(TAG, " Pre-fetching host data for ID: " + hostId);

                        // This needs to happen on the main thread since getUserById uses setValue internally
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> {
                            // This call will cache the host data for future use
                            userRepository.getUserById(hostId);
                        });
                    }

                    // Update lobby in database to persist changes
                    appExecutors.diskIO().execute(() -> {
                        lobbyDao.insert(newLobby);

                        // If this is the current lobby, update the UI
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> {
                            // Update available lobbies listing
                            refreshLobbies();
                            result.postValue(Resource.success(newLobby));
                        });
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

    // Flag to track if a join request is in progress to prevent duplicate requests
    private volatile boolean joinRequestInProgress = false;
    private String currentJoinAttemptLobbyId = null;

    /**
     * Join an existing lobby
     * Uses WebSocket for real-time updates after joining
     */
    public LiveData<Resource<Lobby>> joinLobby(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            Log.e(TAG, "JoinLobby failed: Not authenticated");
            return createErrorLiveData("Not authenticated");
        }

        // Synchronized block to prevent race conditions when checking/updating the flag
        synchronized (this) {
            // Check if a join request is already in progress
            if (joinRequestInProgress) {
                Log.w(TAG, "Join request already in progress for lobby ID: " + currentJoinAttemptLobbyId +
                        ", ignoring new request for: " + lobbyId);
                return createErrorLiveData("Already joining a lobby, please wait");
            }

            // Check if we're already in this lobby
            Lobby currentLobbyValue = currentLobby.getValue();
            if (currentLobbyValue != null && currentLobbyValue.getLobbyId().equals(lobbyId)) {
                Log.i(TAG, "Already in this lobby (ID: " + lobbyId + "), returning current lobby data");
                return new MutableLiveData<>(Resource.success(currentLobbyValue));
            }

            // Mark that a join request is now in progress
            joinRequestInProgress = true;
            currentJoinAttemptLobbyId = lobbyId;
            Log.d(TAG, "Setting joinRequestInProgress=true for lobby ID: " + lobbyId);
        }

        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        Log.d(TAG, "Attempting to join lobby with ID: " + lobbyId);

        if (!webSocketService.isConnected()) {
            Log.d(TAG, "WebSocket not connected, attempting to connect before joining lobby.");
            webSocketService.connect();
        }

        apiService.joinLobby("Bearer " + token, lobbyId, new JoinLobbyRequest()).enqueue(new retrofit2.Callback<ApiResponse<Lobby>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Lobby>> call, retrofit2.Response<ApiResponse<Lobby>> response) {
                try {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Lobby joinedLobby = response.body().getData();
                        if (joinedLobby != null) {
                            Log.i(TAG, "Successfully joined lobby: " + joinedLobby.getLobbyName() + " (ID: " + joinedLobby.getLobbyId() + ")");
                            appExecutors.diskIO().execute(() -> {
                                try {
                                    settingsManager.applySettings(joinedLobby);
                                    ensureHostInPlayerList(joinedLobby);
                                    lobbyDao.insert(joinedLobby);
                                    lobbyCache.put(joinedLobby.getLobbyId(), new CachedLobby(joinedLobby));

                                    // Use postValue instead of setValue for background thread safety
                                    currentLobby.postValue(joinedLobby);

                                    // Don't attempt to use WebSocket join - it's causing errors with serialization
                                    // The API join is sufficient, and WebSocket updates will work automatically
                                    // This prevents the serialization error with WebSocketMessage
                                    if (webSocketService.isConnected()) {
                                        Log.d(TAG, "API join successful for lobby ID: " + joinedLobby.getLobbyId() +
                                              ", WebSocket connection is active for updates");
                                    } else {
                                        Log.d(TAG, "API join successful but WebSocket not connected. Real-time updates may be delayed.");
                                        // Try to connect WebSocket for future updates
                                        webSocketService.connect();
                                    }

                                    // Post success on main thread
                                    new Handler(Looper.getMainLooper()).post(() -> {
                                        result.setValue(Resource.success(joinedLobby));
                                    });
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in background processing after joining lobby: " + e.getMessage(), e);
                                    postError(result, "Error after joining lobby: " + e.getMessage());
                                } finally {
                                    // Always reset the flag when done
                                    resetJoinRequestFlag();
                                }
                            });
                        } else {
                            Log.e(TAG, "Joined lobby but data is null. Lobby ID: " + lobbyId);
                            result.postValue(Resource.error("Failed to join lobby: Server returned no lobby data.", null));
                            resetJoinRequestFlag();
                        }
                    } else {
                        String errorMsg = extractErrorMessage(response);
                        Log.e(TAG, "Failed to join lobby. Response Code: " + response.code() + ", Error: " + errorMsg + ", Lobby ID: " + lobbyId);
                        result.postValue(Resource.error(errorMsg, null));
                        resetJoinRequestFlag();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in joinLobby response handler: " + e.getMessage(), e);
                    result.postValue(Resource.error("Unexpected error: " + e.getMessage(), null));
                    resetJoinRequestFlag();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Lobby>> call, Throwable t) {
                Log.e(TAG, "Network error while trying to join lobby ID: " + lobbyId + ". Error: " + t.getMessage(), t);
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
                resetJoinRequestFlag();
            }
        });
        return result;
    }

    /**
     * Reset the join request flag safely
     */
    private synchronized void resetJoinRequestFlag() {
        Log.d(TAG, "Resetting joinRequestInProgress flag from true to false");
        joinRequestInProgress = false;
        currentJoinAttemptLobbyId = null;
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
                    currentLobby.postValue(null);

                    result.postValue(Resource.success(null));
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
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
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

                        // Store settings locally since server doesn't save them
                        settingsManager.storeSettings(updatedLobby.getLobbyId(), numRounds, roundDurationSeconds);

                        // Ensure settings are set even if server doesn't return them
                        updatedLobby.setNumRounds(numRounds);
                        updatedLobby.setRoundDurationSeconds(roundDurationSeconds);

                        // Update database and current lobby
                        appExecutors.diskIO().execute(() -> {
                            lobbyDao.insert(updatedLobby);

                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> {
                                currentLobby.postValue(updatedLobby);
                                result.postValue(Resource.success(updatedLobby));
                            });
                        });
                    } else {
                        String errorMsg = response.body() != null && response.body().getMessage() != null ?
                                         response.body().getMessage() : "Failed to update lobby settings";
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
                    String gameId = game.getGameId();
                    result.postValue(Resource.success(gameId));

                    // IMPORTANT BUGFIX: The WebSocket message might not be received
                    // Create a synthetic game state message to ensure clients transition to game screen
                    Log.i(TAG, "🎲 Game started successfully via API, gameId: " + gameId);
                    Log.i(TAG, "🔍 Creating synthetic game_state message as WebSocket fallback");

                    // Wait a short time to see if WebSocket message arrives first
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (externalCallback != null) {
                            try {
                                GameStateMessage gameStateMessage = getGameStateMessage(gameId);

                                // Forward the synthetic message to the callback
                                Log.i(TAG, "🚀 Dispatching synthetic game_state message to trigger navigation");
                                externalCallback.onGameStateChanged(gameStateMessage);
                            } catch (Exception e) {
                                Log.e(TAG, "⚠️ Error creating synthetic game state message: " + e.getMessage(), e);
                            }
                        } else {
                            Log.e(TAG, "⚠️ No external callback registered - cannot send synthetic game state");
                        }
                    }, 1000); // Wait 1 second to see if WebSocket message arrives first
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ?
                                     response.body().getMessage() : "Failed to start game";
                    Log.e(TAG, errorMsg);
                    result.postValue(Resource.error(errorMsg, null));
                }
            }

            @NonNull
            private GameStateMessage getGameStateMessage(String gameId) {
                Game gameObj =
                    new GameStateMessage.GamePayload().getGame();
                gameObj.setGameId(gameId);

                GameStateMessage.GamePayload payload =
                    new GameStateMessage.GamePayload();
                payload.setEvent("started");
                payload.setGame(gameObj);

                GameStateMessage gameStateMessage =
                    new GameStateMessage();
                gameStateMessage.setGamePayload(payload);
                gameStateMessage.setType("game_state");
                return gameStateMessage;
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Game>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                // Using postValue instead of setValue for thread safety
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
            }
        });

        return result;
    }

    /**
     * Delete an empty lobby (cleanup method)
     */
    private void deleteEmptyLobby(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            createErrorLiveData("Not authenticated");
            return;
        }

        MutableLiveData<Resource<Void>> result = new MutableLiveData<>();
        // Use postValue for thread safety when updating from background thread
        result.postValue(Resource.loading(null));

        // First, delete from local database
        appExecutors.diskIO().execute(() -> {
            lobbyDao.deleteLobbyById(lobbyId);
            
            // Use postValue for thread safety since we're in a background thread
            result.postValue(Resource.success(null));
        });

    }
    public LiveData<Resource<Boolean>> deleteLobbyIfEmpty(String lobbyId) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Boolean>> errorResult = new MutableLiveData<>();
            errorResult.setValue(Resource.error("Not authenticated", false));
            return errorResult;
        }

        MutableLiveData<Resource<Boolean>> result = new MutableLiveData<>();
        result.postValue(Resource.loading(false)); // Changed to postValue for thread safety
        
        // Get the lobby to check if it's empty
        appExecutors.diskIO().execute(() -> {
            Lobby lobby = lobbyDao.getLobbyByIdDirect(lobbyId);
            if (lobby != null && (lobby.getPlayers() == null || lobby.getPlayers().isEmpty())) {
                // Lobby is empty, delete it
                lobbyDao.deleteLobbyById(lobbyId);
                
                // Also notify server (if API supports this operation)
                deleteEmptyLobby(lobbyId);
                
                // Post success result using postValue for thread safety
                result.postValue(Resource.success(true));
                
                // Log the successful deletion action
                Log.i(TAG, "✅ Lobby " + lobbyId + " was empty and has been deleted");
            } else {
                // Lobby is not empty or doesn't exist
                result.postValue(Resource.success(false));
                
                // Log why we didn't delete
                if (lobby == null) {
                    Log.i(TAG, "ℹ️ Lobby " + lobbyId + " not found, nothing to delete");
                } else {
                    Log.i(TAG, "ℹ️ Lobby " + lobbyId + " has " + 
                          (lobby.getPlayers() != null ? lobby.getPlayers().size() : 0) + 
                          " players, not deleting");  
                }
            }
        });
        
        return result;
    }
    
    /**
     * Get a lobby by its ID
     * 
     * @param lobbyId ID of the lobby to retrieve
     * @return LiveData with Lobby object
     */
    public LiveData<Resource<Lobby>> getLobbyById(String lobbyId) {
        Log.d(TAG, "Getting lobby by ID: " + lobbyId);
        
        MutableLiveData<Resource<Lobby>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        // First check cache
        CachedLobby cachedLobby = lobbyCache.get(lobbyId);
        if (cachedLobby != null && !cachedLobby.isExpired()) {
            result.setValue(Resource.success(cachedLobby.getLobby()));
            return result;
        }
        
        // Then check local database
        appExecutors.diskIO().execute(() -> {
            Lobby lobby = lobbyDao.getLobbyByIdDirect(lobbyId);
            if (lobby != null) {
                // Update cache
                lobbyCache.put(lobbyId, new CachedLobby(lobby));
                
                // Post result
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    result.setValue(Resource.success(lobby));
                });
                return;
            }
            
            // If not in database, fetch from server
            String token = userRepository.getAuthToken();
            if (token == null) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    result.postValue(Resource.error("Not authenticated", null));
                });
                return;
            }
            
            try {
                // Make API call on background thread
                retrofit2.Response<ApiResponse<Lobby>> response = 
                    apiService.getLobbyDetails("Bearer " + token, lobbyId).execute();
                
                if (response.isSuccessful() && response.body() != null && 
                    response.body().isSuccess() && response.body().getData() != null) {
                    
                    Lobby serverLobby = response.body().getData();
                    
                    // Save to database
                    lobbyDao.insert(serverLobby);
                    
                    // Update cache
                    lobbyCache.put(lobbyId, new CachedLobby(serverLobby));
                    
                    // Post success result
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        result.setValue(Resource.success(serverLobby));
                    });
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ?
                                    response.body().getMessage() : "Failed to get lobby details";
                    
                    // Post error result
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        result.postValue(Resource.error(errorMsg, null));
                    });
                }
            } catch (Exception e) {
                // Handle network errors
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    result.postValue(Resource.error("Network error: " + e.getMessage(), null));
                });
            }
        });
        
        return result;
    }
    
    /**
     * Remove a lobby update callback
     * 
     * @param callback The callback to remove
     */
    public void removeLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        if (callback != null && webSocketService != null) {
            webSocketService.removeLobbyUpdateCallback(callback);
        }
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

    /**
     * Helper method to handle lobby state change messages
     */
    private void onLobbyStateChanged(LobbyStateMessage message) {
        if (message != null && message.getLobbyPayload() != null && 
            message.getLobbyPayload().getLobby() != null) {
            
            Lobby updatedLobby = message.getLobbyPayload().getLobby();
            String lobbyId = updatedLobby.getLobbyId();
            String event = message.getLobbyPayload().getEvent();

            Log.d(TAG, "🔄 Processing lobby state change for lobby: " + lobbyId + 
                 ", event: " + (event != null ? event : "update"));

            // Check for player join/leave events
            boolean isPlayerEvent = ("player_joined".equals(event) || "player_left".equals(event));
                
            if (isPlayerEvent) {
                Log.i(TAG, "👤 Player " + event.replace("player_", "") + " event detected in lobby " + 
                      (updatedLobby.getLobbyName() != null ? updatedLobby.getLobbyName() : lobbyId));
                
                // Proactively fetch host information to ensure UI displays correctly
                if (updatedLobby.getHostId() != null) {
                    String hostId = updatedLobby.getHostId();
                    Log.d(TAG, "🔍 Pre-fetching host data for ID: " + hostId);
                    
                    // Use main thread for LiveData updates to avoid IllegalStateException
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                        // This call will cache the host data in UserRepository
                        userRepository.getUserById(hostId);
                    });
                }
                
                // Log all players for debugging
                if (updatedLobby.getPlayers() != null) {
                    Log.d(TAG, "👥 Received WebSocket update with " + updatedLobby.getPlayers().size() + " players:");
                    for (User player : updatedLobby.getPlayers()) {
                        if (player != null) {
                            Log.d(TAG, "   - Player: " + player.getUserId() + 
                                  ", Username: " + player.getUsername());
                            
                            // Proactively cache player data when they join
                            if ("player_joined".equals(event) && userRepository != null) {
                                final String playerId = player.getUserId();
                                Log.d(TAG, "🔄 Pre-fetching joined player data for ID: " + playerId);
                                // Must run on main thread
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    try {
                                        userRepository.getUserById(playerId);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error fetching user data: " + e.getMessage());
                                    }
                                });
                            }
                        }
                    }
                    
                    // Log when player events occur to help with debugging
                    Log.i(TAG, "⚡ Player event occurred, lobby state will be updated");
                } else {
                    Log.w(TAG, "⚠️ Player event but players list is null!");
                }
            }

            // Apply any locally stored settings
            settingsManager.applySettings(updatedLobby);
            
            // Update cache with fresh data
            lobbyCache.put(lobbyId, new CachedLobby(updatedLobby));
            
            // Update database on background thread
            appExecutors.diskIO().execute(() -> {
                lobbyDao.insert(updatedLobby);
                
                // Log database update for player events
                if (isPlayerEvent) {
                    Log.d(TAG, "💾 Updated database with player event for lobby " + lobbyId);
                }
            });
            
            // Update current lobby if it's the same one
            Lobby currentLobbyValue = currentLobby.getValue();
            if (currentLobbyValue != null && currentLobbyValue.getLobbyId().equals(lobbyId)) {
                // Use postValue for thread safety
                Log.d(TAG, "🔄 Updating current lobby LiveData with updated player list");
                currentLobby.postValue(updatedLobby);
            }

            // Forward to external callback (e.g., ViewModels and UI)
            if (externalCallback != null) {
                Log.d(TAG, "📢 Notifying external callback about lobby change");
                externalCallback.onLobbyStateChanged(message);
            }
        }
    }

    /**
     * Process a lobby update message from WebSocket
     * Uses the WebSocketMessageConverter to parse the message
     */
    public void processLobbyUpdate(String lobbyData) {
        try {
            // Use WebSocketMessageConverter to parse the message
            LobbyStateMessage message = messageConverter.parseLobbyStateMessage(lobbyData);
            
            if (message != null && message.getLobbyPayload() != null && 
                message.getLobbyPayload().getLobby() != null) {
                
                Lobby lobby = message.getLobbyPayload().getLobby();
                String lobbyId = lobby.getLobbyId();

                // Apply any locally stored settings before processing
                settingsManager.applySettings(lobby);

                // Log the lobby data for debugging
                Log.d(TAG, "Processing lobby update: id=" + lobbyId +
                          ", rounds=" + lobby.getNumRounds() +
                          ", duration=" + lobby.getRoundDurationSeconds());
                          
                // Process the message as a lobby state change
                onLobbyStateChanged(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pre-processing lobby update: " + e.getMessage());
        }
    }

    private String extractErrorMessage(Response<?> response) {
        String errorMsg = "Failed to join lobby (API error)";
        if (response.errorBody() != null) {
            try {
                String errorBodyString = response.errorBody().string();
                if (!errorBodyString.isEmpty()) {
                    return errorBodyString;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading error body: " + e.getMessage());
            }
        } else if (response.body() instanceof ApiResponse) { // Check if body itself is ApiResponse (e.g. success=false)
            ApiResponse<?> apiResponse = (ApiResponse<?>) response.body();
            if (!apiResponse.isSuccess() && apiResponse.getMessage() != null) {
                return apiResponse.getMessage();
            }
        } else if (!response.message().isEmpty()) {
             errorMsg = response.message(); // HTTP status message
        }
        return errorMsg;
    }

    private <T> MutableLiveData<Resource<T>> createErrorLiveData(String errorMessage) {
        MutableLiveData<Resource<T>> result = new MutableLiveData<>();
        result.postValue(Resource.error(errorMessage, null));
        return result;
    }

    /**
     * Post an error to a LiveData on the main thread
     */
    private <T> void postError(MutableLiveData<Resource<T>> liveData, String errorMessage) {
        // Use postValue which is safe to call from any thread
        liveData.postValue(Resource.error(errorMessage, null));
    }
}
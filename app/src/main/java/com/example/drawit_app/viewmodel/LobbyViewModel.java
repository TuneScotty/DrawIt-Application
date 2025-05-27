package com.example.drawit_app.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Observer;

import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.LobbiesState;
import com.example.drawit_app.model.LobbyState;
import com.example.drawit_app.model.User;
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.LobbyStateMessage;
import com.example.drawit_app.network.response.LobbyListResponse;
import com.example.drawit_app.repository.BaseRepository.Resource;
import com.example.drawit_app.repository.LobbyRepository;
import com.example.drawit_app.repository.UserRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * ViewModel for lobby-related operations
 */
@HiltViewModel
public class LobbyViewModel extends ViewModel {
    
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    
    // State of available lobbies
    private final MediatorLiveData<LobbiesState> lobbiesState = new MediatorLiveData<>();
    
    // State of the current lobby
    private final MediatorLiveData<LobbyState> lobbyState = new MediatorLiveData<>();
    
    // Form validation errors
    private final MutableLiveData<String> lobbyNameError = new MutableLiveData<>();
    
    // Game start event
    private final MutableLiveData<String> gameStartEvent = new MutableLiveData<>();
    
    // Newly created lobby event - triggers navigation to the new lobby
    private final MutableLiveData<Lobby> newlyCreatedLobby = new MutableLiveData<>();
    
    // Loading state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    
    // Result of lobby deletion
    private final MutableLiveData<Resource<Void>> lobbyDeletionResult = new MutableLiveData<>();
    
    // WebSocket callback for lobby updates
    private WebSocketService.LobbyUpdateCallback lobbyUpdateCallback;
    
    @Inject
    public LobbyViewModel(LobbyRepository lobbyRepository, UserRepository userRepository) {
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        
        // Initialize states
        lobbiesState.setValue(new LobbiesState(null, null, false));
        lobbyState.setValue(new LobbyState(null, null, false));
        
        // Observe current lobby
        LiveData<Lobby> currentLobby = lobbyRepository.getCurrentLobby();
        lobbyState.addSource(currentLobby, lobby -> {
            LobbyState currentState = lobbyState.getValue();
            if (currentState != null) {
                lobbyState.setValue(new LobbyState(lobby, currentState.getErrorMessage(), false));
            } else {
                lobbyState.setValue(new LobbyState(lobby, null, false));
            }
        });
        
        // Observe available lobbies
        LiveData<List<Lobby>> availableLobbies = lobbyRepository.getAvailableLobbies();
        lobbiesState.addSource(availableLobbies, lobbies -> {
            LobbiesState currentState = lobbiesState.getValue();
            if (currentState != null) {
                lobbiesState.setValue(new LobbiesState(lobbies, currentState.getErrorMessage(), false));
            } else {
                lobbiesState.setValue(new LobbiesState(lobbies, null, false));
            }
        });
    }
    
    /**
     * Refresh the list of available lobbies
     */
    public void refreshLobbies() {
        // Get current state
        LobbiesState currentState = lobbiesState.getValue();
        List<Lobby> currentLobbies = currentState != null ? currentState.getLobbies() : null;
        
        // Define initial, success and error states
        LobbiesState initialState = new LobbiesState(currentLobbies, null, true);
        LobbiesState successState = new LobbiesState(currentLobbies, null, false);
        
        // Call repository using helper method
        handleRepositoryCall(
            lobbyRepository.refreshLobbies(),
            lobbiesState,
            initialState,
            successState,
            errorMsg -> new LobbiesState(currentLobbies, errorMsg, false)
        );
    }
    
    /**
     * Fetch lobbies from the server
     * This method is called by the LobbiesFragment to refresh the lobby list
     */
    public void fetchLobbies() {
        isLoading.setValue(true);
        
        // Call repository to refresh lobbies
        refreshLobbies();
    }
    
    /**
     * Create a new lobby
     * @return LiveData resource with the created lobby for observing the result
     */
    public LiveData<Resource<Lobby>> createLobby(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        // Validate inputs
        boolean isValid = validateLobbyForm(lobbyName, maxPlayers, numRounds, roundDurationSeconds);
        if (!isValid) {
            MutableLiveData<Resource<Lobby>> errorResult = new MutableLiveData<>();
            errorResult.setValue(Resource.error("Invalid lobby form data", null));
            return errorResult;
        }
        
        // Define states for repository call
        LobbyState loadingState = new LobbyState(null, null, true);
        LobbyState successState = new LobbyState(null, null, false); // Success handled by observing currentLobby
        
        // Call repository to create lobby
        LiveData<Resource<Lobby>> result = lobbyRepository.createLobby(
            lobbyName, maxPlayers, numRounds, roundDurationSeconds);
        
        // Use helper method to handle repository call
        handleRepositoryCall(
            result,
            lobbyState,
            loadingState,
            successState,
            errorMsg -> new LobbyState(null, errorMsg, false)
        );
        
        return result;
    }
    
    /**
     * Join an existing lobby
     */
    public void joinLobby(String lobbyId) {
        // Set loading state
        lobbyState.setValue(new LobbyState(null, null, true));
        
        // Call repository to join lobby
        LiveData<Resource<Lobby>> result = lobbyRepository.joinLobby(lobbyId);
        lobbyState.addSource(result, resource -> {
            if (resource.isLoading()) {
                // Already set loading state above
            } else if (resource.isSuccess()) {
                // Success handled by observing lobbyRepository.getCurrentLobby()
                // Just clear error message
                lobbyState.setValue(new LobbyState(lobbyState.getValue().getLobby(), null, false));
            } else if (resource.isError()) {
                lobbyState.setValue(new LobbyState(null, resource.getMessage(), false));
            }
            
            lobbyState.removeSource(result);
        });
    }
    
    /**
     * Leave the current lobby
     * @return LiveData<Resource<Void>> that can be observed for the result
     */
    public LiveData<Resource<Void>> leaveLobby() {
        // Get current state
        LobbyState currentState = lobbyState.getValue();
        Lobby currentLobby = currentState != null ? currentState.getLobby() : null;
        
        // Define states for repository call
        LobbyState loadingState = new LobbyState(currentLobby, null, true);
        LobbyState successState = new LobbyState(null, null, false); // Clear lobby on success
        
        // Call repository to leave lobby
        LiveData<Resource<Void>> result = lobbyRepository.leaveLobby();
        
        // Use helper method to handle the call
        handleRepositoryCall(
            result, 
            lobbyState, 
            loadingState,
            successState,
            errorMsg -> new LobbyState(currentLobby, errorMsg, false)
        );
        
        // Return the result so it can be observed by the fragment
        return result;
    }
    
    /**
     * Delete a lobby if it's empty (typically after last player leaves)
     * @param lobbyId ID of the possibly empty lobby to check and delete
     * @return LiveData with the result of the deletion attempt
     */
    public LiveData<Resource<Void>> deleteLobbyIfEmpty(String lobbyId) {
        Log.d("LobbyViewModel", "Checking if lobby is empty and can be deleted: " + lobbyId);
        
        // Get the current user to check if they're the host
        User currentUser = getCurrentUser();
        if (currentUser == null || currentUser.getUserId() == null) {
            Log.e("LobbyViewModel", "Cannot delete lobby: No current user");
            lobbyDeletionResult.setValue(Resource.error("Not authenticated", null));
            return lobbyDeletionResult;
        }
        
        // Check current lobby data (could be null if already left)
        Lobby lobby = getCurrentLobby().getValue();
        if (lobby == null) {
            // Use the repository's private method to delete empty lobby directly
            // This is a simplified approach - in a real implementation, we'd check player count first
            Log.d("LobbyViewModel", "No current lobby data, assuming empty and requesting deletion");
            callDeleteEmptyLobbyMethod(lobbyId);
        } else if (isLobbyEmptyOrOnlyContainsCurrentUser(lobby, currentUser)) {
            // Lobby is empty or has only the current user (who is leaving)
            
            // Check if current user is host (only host can delete lobby)
            if (isUserHostOfLobby(lobby, currentUser.getUserId())) {
                Log.d("LobbyViewModel", "Current user is host of an empty lobby, requesting deletion");
                callDeleteEmptyLobbyMethod(lobbyId);
            } else {
                // Not host, cannot delete
                Log.d("LobbyViewModel", "Current user is not host, cannot delete lobby");
                lobbyDeletionResult.setValue(Resource.success(null));
            }
        } else {
            // Lobby has other players, no need to delete
            Log.d("LobbyViewModel", "Lobby is not empty, not deleting");
            lobbyDeletionResult.setValue(Resource.success(null));
        }
        
        return lobbyDeletionResult;
    }
    
    /**
     * Check if a user is the host of a given lobby
     * @param lobby The lobby to check
     * @param userId The user ID to check
     * @return true if the user is the host, false otherwise
     */
    private boolean isUserHostOfLobby(Lobby lobby, String userId) {
        return lobby != null && lobby.getHostId() != null && lobby.getHostId().equals(userId);
    }
    
    /**
     * Check if a lobby is empty or only contains the given user
     * @param lobby The lobby to check
     * @param user The user who might be the only player
     * @return true if the lobby is empty or only contains the given user
     */
    private boolean isLobbyEmptyOrOnlyContainsCurrentUser(Lobby lobby, User user) {
        if (lobby == null || lobby.getPlayers() == null) {
            return true;
        }
        
        if (lobby.getPlayers().isEmpty()) {
            return true;
        }
        
        // Check if there's exactly one player and it's the current user
        return lobby.getPlayers().size() == 1 && 
               user != null && 
               user.getUserId() != null && 
               lobby.getPlayers().get(0).getUserId() != null && 
               lobby.getPlayers().get(0).getUserId().equals(user.getUserId());
    }
    
    /**
     * Call the deleteEmptyLobby method on the LobbyRepository using reflection
     * This is a workaround for accessing a private method
     * 
     * @param lobbyId The ID of the lobby to delete
     */
    private void callDeleteEmptyLobbyMethod(String lobbyId) {
        try {
            java.lang.reflect.Method deleteEmptyLobbyMethod = 
                    lobbyRepository.getClass().getDeclaredMethod("deleteEmptyLobby", String.class);
            deleteEmptyLobbyMethod.setAccessible(true);
            deleteEmptyLobbyMethod.invoke(lobbyRepository, lobbyId);
            
            lobbyDeletionResult.setValue(Resource.success(null));
        } catch (Exception e) {
            Log.e("LobbyViewModel", "Failed to delete empty lobby: " + e.getMessage(), e);
            lobbyDeletionResult.setValue(Resource.error("Failed to delete empty lobby", null));
        }
    }
    
    /**
     * Set the WebSocket callback for lobby updates
     */
    public void setLobbyUpdateCallback(WebSocketService.LobbyUpdateCallback callback) {
        this.lobbyUpdateCallback = callback;
        lobbyRepository.setLobbyUpdateCallback(callback);
    }
    
    /**
     * Process a lobby update message from WebSocket
     */
    public void processLobbyUpdate(String lobbyData) {
        // This would typically parse the lobby data and update relevant LiveData objects
        // For now, we'll just pass it to the repository
        lobbyRepository.processLobbyUpdate(lobbyData);
    }
    
    /**
     * Start a game in the current lobby (host only)
     */
    public void startGame(String lobbyId) {
        // Set loading state
        isLoading.setValue(true);
        
        // Call repository to start game
        LiveData<Resource<String>> result = lobbyRepository.startGame(lobbyId);
        result.observeForever(new Observer<Resource<String>>() {
            @Override
            public void onChanged(Resource<String> resource) {
                result.removeObserver(this);
                isLoading.setValue(false);
                
                if (resource.isSuccess() && resource.getData() != null) {
                    gameStartEvent.setValue(resource.getData()); // Game ID
                } else if (resource.isError()) {
                    lobbyState.setValue(new LobbyState(lobbyState.getValue().getLobby(), 
                            resource.getMessage(), false));
                }
            }
        });
    }
    
    /**
     * Toggle the lock state of the lobby (host only)
     */
    public void toggleLobbyLock(String lobbyId, boolean lock) {
        // Set loading state
        isLoading.setValue(true);
        
        // Call repository to lock/unlock lobby
        LiveData<Resource<Lobby>> result = lobbyRepository.toggleLobbyLock(lobbyId, lock);
        result.observeForever(new Observer<Resource<Lobby>>() {
            @Override
            public void onChanged(Resource<Lobby> resource) {
                result.removeObserver(this);
                isLoading.setValue(false);
                
                if (resource.isSuccess()) {
                    // Success handled by observing lobbyRepository.getCurrentLobby()
                } else if (resource.isError()) {
                    lobbyState.setValue(new LobbyState(lobbyState.getValue().getLobby(), 
                            resource.getMessage(), false));
                }
            }
        });
    }
    
    /**
     * Update settings for the current lobby (host only)
     */
    public void updateLobbySettings(int numRounds, int roundDurationSeconds) {
        // Validate inputs
        boolean isValid = validateSettings(numRounds, roundDurationSeconds);
        if (!isValid) {
            return;
        }
        
        // Get current state
        LobbyState currentState = lobbyState.getValue();
        if (currentState == null || currentState.getLobby() == null) {
            return; // Can't update settings if not in a lobby
        }
        
        Lobby currentLobby = currentState.getLobby();
        
        // Define states for the repository call
        LobbyState initialState = new LobbyState(currentLobby, null, true);
        LobbyState successState = new LobbyState(currentLobby, null, false);
        
        // Call repository using helper method
        handleRepositoryCall(
            lobbyRepository.updateLobbySettings(numRounds, roundDurationSeconds),
            lobbyState,
            initialState,
            successState,
            errorMsg -> new LobbyState(currentLobby, errorMsg, false)
        );
    }
    
    /**
     * Check if the current user is the host of the current lobby
     */
    public boolean isCurrentUserHost() {
        LobbyState currentLobbyState = lobbyState.getValue();
        User currentUser = userRepository.getCurrentUser().getValue();
        
        if (currentLobbyState != null && currentLobbyState.getLobby() != null && currentUser != null) {
            return currentUser.getUserId().equals(currentLobbyState.getLobby().getHostId());
        }
        
        return false;
    }
    
    /**
     * Get the current user
     */
    public User getCurrentUser() {
        return userRepository.getCurrentUser().getValue();
    }
    
    /**
     * Validate lobby creation form
     */
    private boolean validateLobbyForm(String lobbyName, int maxPlayers, int numRounds, int roundDurationSeconds) {
        boolean isValid = true;
        
        // Lobby name validation
        if (lobbyName == null || lobbyName.trim().isEmpty()) {
            lobbyNameError.setValue("Lobby name is required");
            isValid = false;
        } else {
            lobbyNameError.setValue(null);
        }
        
        // Max players validation (can also be added to another LiveData error field if needed)
        if (maxPlayers < 2 || maxPlayers > 10) {
            isValid = false;
        }
        
        // Other validations
        if (!validateSettings(numRounds, roundDurationSeconds)) {
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * Validate lobby settings
     */
    private boolean validateSettings(int numRounds, int roundDurationSeconds) {
        boolean isValid = true;
        
        // Rounds validation
        if (numRounds < 1 || numRounds > 10) {
            isValid = false;
        }
        
        // Duration validation
        if (roundDurationSeconds < 30 || roundDurationSeconds > 300) {
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * Get available lobbies
     */
    public LiveData<List<Lobby>> getLobbies() {
        return lobbyRepository.getAvailableLobbies();
    }
    
    /**
     * Get current lobby
     */
    public LiveData<Lobby> getCurrentLobby() {
        return lobbyRepository.getCurrentLobby();
    }
    
    /**
     * Get newly created lobby event
     * This will be triggered when a lobby is successfully created
     */
    public LiveData<Lobby> getNewlyCreatedLobby() {
        return newlyCreatedLobby;
    }
    
    /**
     * Reset the newly created lobby to prevent re-triggering navigation
     */
    public void resetNewlyCreatedLobby() {
        newlyCreatedLobby.setValue(null);
    }
    
    /**
     * Get game start event
     */
    public LiveData<String> getGameStartEvent() {
        return gameStartEvent;
    }
    
    /**
     * Get loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * Get error message
     */
    public LiveData<String> getErrorMessage() {
        return lobbyRepository.getErrorMessage();
    }
    
    /**
     * Set an error message
     */
    private void setErrorMessage(String message) {
        if (message != null && !message.isEmpty()) {
            Log.e("LobbyViewModel", "Error: " + message, new Throwable(message));
        }
    }
    
    /**
     * Get the state of available lobbies as LiveData for UI to observe
     */
    public LiveData<LobbiesState> getLobbiesState() {
        return lobbiesState;
    }
    
    /**
     * Get the state of the current lobby as LiveData for UI to observe
     */
    public LiveData<LobbyState> getLobbyState() {
        return lobbyState;
    }
    
    /**
     * Get lobby name error as LiveData for UI to observe
     */
    public LiveData<String> getLobbyNameError() {
        return lobbyNameError;
    }
    
    // Inner state classes have been moved to model package
    
    /**
     * Helper method to handle repository calls with standard loading and error state management
     * Reduces boilerplate code in repository method calls
     * 
     * @param <T> The type of data being returned by the repository call
     * @param <S> The type of state used in the mediator
     * @param repositoryCall The LiveData resource from the repository
     * @param stateMediator The state MediatorLiveData to update based on results
     * @param initialState The initial state to set before making the call
     * @param successState The state to set on success
     * @param errorStateProvider Function to provide error state
     */
    private <T, S> void handleRepositoryCall(
            LiveData<Resource<T>> repositoryCall,
            MediatorLiveData<S> stateMediator,
            S initialState,
            S successState,
            java.util.function.Function<String, S> errorStateProvider) {
        
        // Set initial loading state
        stateMediator.setValue(initialState);
        
        // Observe repository call
        stateMediator.addSource(repositoryCall, resource -> {
            if (resource.isLoading()) {
                // Already set loading state above
            } else if (resource.isSuccess()) {
                stateMediator.setValue(successState);
            } else if (resource.isError()) {
                stateMediator.setValue(errorStateProvider.apply(resource.getMessage()));
            }
            
            // Remove source to avoid memory leaks
            stateMediator.removeSource(repositoryCall);
        });
    }
}

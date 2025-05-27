package com.example.drawit_app.model;

/**
 * State class representing the state of a current lobby
 */
public class LobbyState {
    private final Lobby lobby;
    private final String errorMessage;
    private final boolean isLoading;
    
    public LobbyState(Lobby lobby, String errorMessage, boolean isLoading) {
        this.lobby = lobby;
        this.errorMessage = errorMessage;
        this.isLoading = isLoading;
    }
    
    public Lobby getLobby() {
        return lobby;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public boolean isLoading() {
        return isLoading;
    }
    
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
    
    public boolean isInLobby() {
        return lobby != null;
    }
}

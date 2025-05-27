package com.example.drawit_app.model;

import java.util.List;

/**
 * State class representing the state of available lobbies
 */
public class LobbiesState {
    private final List<Lobby> lobbies;
    private final String errorMessage;
    private final boolean isLoading;
    
    public LobbiesState(List<Lobby> lobbies, String errorMessage, boolean isLoading) {
        this.lobbies = lobbies;
        this.errorMessage = errorMessage;
        this.isLoading = isLoading;
    }
    
    public List<Lobby> getLobbies() {
        return lobbies;
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
}

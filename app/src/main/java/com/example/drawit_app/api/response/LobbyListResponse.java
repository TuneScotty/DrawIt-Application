package com.example.drawit_app.api.response;

import com.example.drawit_app.model.Lobby;

import java.util.List;

/**
 * Response model for the lobby list endpoint
 */
public class LobbyListResponse {
    private List<Lobby> lobbies;
    private int totalCount;
    
    public List<Lobby> getLobbies() {
        return lobbies;
    }
    
    public void setLobbies(List<Lobby> lobbies) {
        this.lobbies = lobbies;
    }

    public int getTotalCount() { return totalCount; }
}

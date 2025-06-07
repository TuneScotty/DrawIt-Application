package com.example.drawit_app.network.response;

import com.example.drawit_app.model.User;

/**
 * Response model for authentication operations
 */
public class AuthResponse {
    private String token;
    private User user;
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
}

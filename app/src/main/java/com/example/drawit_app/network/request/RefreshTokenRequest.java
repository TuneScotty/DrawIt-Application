package com.example.drawit_app.network.request;

/**
 * Request model for refreshing JWT authentication tokens
 */
public class RefreshTokenRequest {
    private String token;
    
    public RefreshTokenRequest(String token) {
        this.token = token;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
}

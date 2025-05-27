package com.example.drawit_app.network.request;

/**
 * Request model for updating user profile information
 */
public class UpdateProfileRequest {
    private String username;
    private String password;
    private String avatarUrl;
    
    public UpdateProfileRequest(String username, String password, String avatarUrl) {
        this.username = username;
        this.password = password;
        this.avatarUrl = avatarUrl;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}

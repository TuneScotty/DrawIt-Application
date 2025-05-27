package com.example.drawit_app.model;

/**
 * Represents a chat message in the game
 */
public class ChatMessage {
    
    public enum MessageType {
        PLAYER_MESSAGE,  // Regular message from a player
        SYSTEM_MESSAGE,  // System notification
        CORRECT_GUESS,   // Correct word guess
        CLOSE_GUESS      // Close to the correct word
    }
    
    private User sender;
    private String message;
    private MessageType type;
    private long timestamp;
    
    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public ChatMessage(User sender, String message, MessageType type) {
        this.sender = sender;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public User getSender() {
        return sender;
    }
    
    public void setSender(User sender) {
        this.sender = sender;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

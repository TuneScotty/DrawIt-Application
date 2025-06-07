package com.example.drawit_app.network.message;

import com.squareup.moshi.Json;

/**
 * Represents system-level connection status messages from the WebSocket server
 */
public class ConnectionStatusMessage extends WebSocketMessage {
    
    public static final String TYPE_CONNECTION_ESTABLISHED = "connection_established";
    
    @Json(name = "message")
    private String message;
    
    public ConnectionStatusMessage() {
        super(TYPE_CONNECTION_ESTABLISHED, null);
    }
    
    public ConnectionStatusMessage(String message) {
        super(TYPE_CONNECTION_ESTABLISHED, null);
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}

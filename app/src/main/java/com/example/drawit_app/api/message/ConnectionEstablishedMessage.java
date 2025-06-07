package com.example.drawit_app.api.message;

import com.squareup.moshi.Json;

/**
 * WebSocket message for connection established notifications
 */
public class ConnectionEstablishedMessage extends WebSocketMessage {
    
    public static final String TYPE_CONNECTION_ESTABLISHED = "connection_established";
    
    @Json(name = "data")
    private Object data; // Using Object since we don't need to process this data specifically
    
    public ConnectionEstablishedMessage() {
        super();
        setType(TYPE_CONNECTION_ESTABLISHED);
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
}

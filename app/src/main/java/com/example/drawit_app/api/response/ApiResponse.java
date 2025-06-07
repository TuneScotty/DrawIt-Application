package com.example.drawit_app.network.response;

/**
 * Generic wrapper for API responses
 * @param <T> Type of data contained in the response
 */
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    /**
     * Check if data is present and not null
     * @return true if data exists and is not null
     */
    public boolean hasData() {
        return data != null;
    }
}

package com.example.drawit_app.network.request;

/**
 * Request model for rating a drawing
 */
public class RateDrawingRequest {
    private float rating;
    
    public RateDrawingRequest(float rating) {
        this.rating = rating;
    }
    
    public float getRating() {
        return rating;
    }
    
    public void setRating(float rating) {
        this.rating = rating;
    }
}

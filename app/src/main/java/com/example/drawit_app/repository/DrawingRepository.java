package com.example.drawit_app.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import android.graphics.Bitmap;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Build;
import android.content.Context;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.DrawingDao;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.request.RateDrawingRequest;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Repository for drawing-related operations and drawing archive
 */
@Singleton
public class DrawingRepository extends BaseRepository {
    
    private final ApiService apiService;
    private final DrawingDao drawingDao;
    private final UserRepository userRepository;
    
    @Inject
    public DrawingRepository(ApiService apiService, DrawItDatabase database, UserRepository userRepository) {
        this.apiService = apiService;
        this.drawingDao = database.drawingDao();
        this.userRepository = userRepository;
    }
    
    /**
     * Get all drawings for the current user
     */
    public LiveData<Resource<List<Drawing>>> getUserDrawings() {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<List<Drawing>>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<List<Drawing>>> result = callApi(apiService.getUserDrawings("Bearer " + token));
        
        // Observe the result to update local database
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                List<Drawing> drawings = resource.getData();
                for (Drawing drawing : drawings) {
                    drawingDao.insert(drawing);
                }
            }
        });
        
        return result;
    }
    
    /**
     * Search user drawings by word
     */
    public LiveData<Resource<List<Drawing>>> searchDrawings(String word) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<List<Drawing>>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        LiveData<Resource<List<Drawing>>> result = callApi(apiService.searchUserDrawings("Bearer " + token, word));
        
        // Observe the result to update local database
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                List<Drawing> drawings = resource.getData();
                for (Drawing drawing : drawings) {
                    drawingDao.insert(drawing);
                }
            }
        });
        
        return result;
    }
    
    /**
     * Get top rated drawings for the current user
     */
    public LiveData<List<Drawing>> getTopRatedDrawings(int limit) {
        String userId = userRepository.getCurrentUser().getValue() != null ? 
                userRepository.getCurrentUser().getValue().getUserId() : null;
        
        if (userId == null) {
            MutableLiveData<List<Drawing>> result = new MutableLiveData<>();
            result.setValue(null);
            return result;
        }
        
        return drawingDao.getTopRatedDrawings(userId, limit);
    }
    
    /**
     * Rate a drawing
     */
    public LiveData<Resource<Drawing>> rateDrawing(String gameId, String drawingId, float rating) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            MutableLiveData<Resource<Drawing>> result = new MutableLiveData<>();
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        RateDrawingRequest request = new RateDrawingRequest(rating);
        LiveData<Resource<Drawing>> result = callApi(apiService.rateDrawing("Bearer " + token, gameId, drawingId, request));
        
        // Observe the result to update local database
        observeOnce(result, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Drawing drawing = resource.getData();
                drawingDao.insert(drawing);
            }
        });
        
        return result;
    }
    
    /**
     * Create a new drawing locally (before submission)
     */
    public Drawing createNewDrawing(String userId, String word) {
        String drawingId = UUID.randomUUID().toString();
        return new Drawing(drawingId, userId, null, 0, word);
    }
    
    /**
     * Get a specific drawing by ID
     * @param drawingId The drawing ID to retrieve
     * @return LiveData with drawing resource
     */
    public LiveData<Resource<Drawing>> getDrawingById(String drawingId) {
        MutableLiveData<Resource<Drawing>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));
        
        String token = userRepository.getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return result;
        }
        
        // First try to get from local database
        LiveData<Drawing> localDrawingLiveData = drawingDao.getDrawingById(drawingId);
        
        // Observe once to get the actual value
        observeOnce(localDrawingLiveData, drawing -> {
            if (drawing != null) {
                result.setValue(Resource.success(drawing));
            } else {
                // If not in local database, get from API
                fetchDrawingFromApi(drawingId, result);
            }
        });
        
        return result;
    }
    
    /**
     * Helper method to fetch a drawing from the API
     */
    private void fetchDrawingFromApi(String drawingId, MutableLiveData<Resource<Drawing>> result) {
        String token = userRepository.getAuthToken();
        if (token == null) {
            result.setValue(Resource.error("Not authenticated", null));
            return;
        }
        
        LiveData<Resource<Drawing>> apiResult = callApi(apiService.getDrawing("Bearer " + token, drawingId));
        
        // Observe the result to update local database
        observeOnce(apiResult, resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Drawing drawing = resource.getData();
                
                // Use a background thread for database operations
                new Thread(() -> drawingDao.insert(drawing)).start();
                
                // Update the result
                result.setValue(Resource.success(drawing));
            } else {
                // Forward the error
                result.setValue(resource);
            }
        });
    }
    
    /**
     * Rate a drawing (overloaded for compatibility)
     * @param drawingId The drawing ID to rate
     * @param rating The rating value (1-5)
     * @return LiveData with drawing resource
     */
    public LiveData<Resource<Drawing>> rateDrawing(String drawingId, int rating) {
        MutableLiveData<Resource<Drawing>> result = new MutableLiveData<>();
        
        // Get the current game ID from the drawing
        LiveData<Drawing> drawingLiveData = drawingDao.getDrawingById(drawingId);
        
        observeOnce(drawingLiveData, drawing -> {
            String gameId = drawing != null ? drawing.getGameId() : "unknown_game";
            
            // Call the original method with the game ID and observe its result
            LiveData<Resource<Drawing>> ratingResult = rateDrawing(gameId, drawingId, (float)rating);
            observeOnce(ratingResult, resource -> {
                result.setValue(resource);
            });
        });
        
        return result;
    }
    
    /**
     * Save drawing to the device gallery
     * @param bitmap The bitmap to save
     * @param title The title for the saved image
     */
    public void saveDrawingToGallery(Bitmap bitmap, String title, Context context) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, title);
            values.put(MediaStore.Images.Media.DISPLAY_NAME, title);
            values.put(MediaStore.Images.Media.DESCRIPTION, "Drawing from DrawIt app");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            
            // Handle Android 10 (Q) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            
            // Insert and get URI
            android.net.Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                // Write the bitmap to the URI
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, context.getContentResolver().openOutputStream(uri));
                
                // Finish pending state for Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                }
            }
        } catch (Exception e) {
            setError("Failed to save drawing: " + e.getMessage());
        }
    }
    
    /**
     * Save drawing to the device gallery (overloaded for backward compatibility)
     * @param bitmap The bitmap to save
     * @param title The title for the saved image
     */
    public void saveDrawingToGallery(Bitmap bitmap, String title) {
        // In a real implementation, we would use the application context
        // For now, just log an error since we can't access the context
        setError("Context required to save drawing to gallery");
    }
    
    /**
     * Get all drawings in the system
     * @return LiveData with list of all drawings
     */
    public LiveData<List<Drawing>> getAllDrawings() {
        // Return LiveData from local database
        return drawingDao.getAllDrawings();
    }
    
    /**
     * Helper method to observe a LiveData object once
     */
    private <T> void observeOnce(LiveData<T> liveData, OnObservedListener<T> listener) {
        liveData.observeForever(new androidx.lifecycle.Observer<T>() {
            @Override
            public void onChanged(T t) {
                listener.onObserved(t);
                liveData.removeObserver(this);
            }
        });
    }
    
    /**
     * Interface for observing LiveData once
     */
    private interface OnObservedListener<T> {
        void onObserved(T t);
    }
}

package com.example.drawit_app.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.drawit_app.network.response.ApiResponse;
import com.example.drawit_app.network.response.LobbyListResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Base repository class with common functionality for API calls and response handling
 */
public abstract class BaseRepository {
    
    protected final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    /**
     * Sets an error message to be displayed to the user
     * @param message The error message
     */
    protected void setError(String message) {
        errorMessage.postValue(message);
    }
    
    /**
     * Get the error message LiveData
     * @return LiveData containing error messages
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    protected <T> LiveData<Resource<T>> callApi(Call<ApiResponse<T>> call) {
        return callApi(call, false); // Default: not a retry
    }
    
    /**
     * Extended callApi method that supports token refresh and retry
     * @param call The API call to execute
     * @param isRetry Whether this is a retry after token refresh
     * @return LiveData with the result
     */
    protected <T> LiveData<Resource<T>> callApi(Call<ApiResponse<T>> call, boolean isRetry) {
        MutableLiveData<Resource<T>> result = new MutableLiveData<>();
        // Use postValue for the initial loading state since this might not be on the main thread
        result.postValue(Resource.loading(null));
        
        // Enhanced logging for API calls
        android.util.Log.d("APICall", "====== API REQUEST DETAILS ======");
        android.util.Log.d("APICall", "URL: " + call.request().url());
        android.util.Log.d("APICall", "Method: " + call.request().method());
        android.util.Log.d("APICall", "Headers: " + call.request().headers());
        android.util.Log.d("APICall", "Is retry: " + isRetry);
        
        // Try to log the request body if possible
        try {
            if (call.request().body() != null) {
                android.util.Log.d("APICall", "Has request body: true");
            }
        } catch (Exception e) {
            android.util.Log.d("APICall", "Error logging request body: " + e.getMessage());
        }
        android.util.Log.d("APICall", "=================================");
        
        call.enqueue(new Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
                android.util.Log.d("APICall", "Response code: " + response.code());
                android.util.Log.d("APICall", "Response message: " + response.message());
                android.util.Log.d("APICall", "Is successful: " + response.isSuccessful());
                android.util.Log.d("APICall", "Has body: " + (response.body() != null));
                
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<T> apiResponse = response.body();
                    android.util.Log.d("APICall", "API success: " + apiResponse.isSuccess());
                    android.util.Log.d("APICall", "API message: " + apiResponse.getMessage());
                    android.util.Log.d("APICall", "API has data: " + apiResponse.hasData());
                    
                    if (apiResponse.isSuccess()) {
                        // Success case - pass the data (may be null)
                        // Use postValue since this callback runs on a background thread
                        if (apiResponse.getData() != null) {
                            android.util.Log.d("APICall", "Setting success result with data");
                            result.postValue(Resource.success(apiResponse.getData()));
                        } else {
                            android.util.Log.d("APICall", "API success but data is null, attempting to handle edge case");
                            // Try to construct response from other available data if possible
                            try {
                                // This is a special edge case handler - we know the call succeeded but data is null
                                // This helps with certain API endpoints that might not return data in the expected format
                                result.postValue(Resource.success(null));
                            } catch (Exception e) {
                                android.util.Log.d("APICall", "Error handling null data in success response: " + e.getMessage());
                                result.postValue(Resource.success(null)); // Fallback to just pass null
                            }
                        }
                    } else {
                        // Error case from server but with HTTP 200
                        // The error message is in apiResponse.getMessage()
                        result.postValue(Resource.error(apiResponse.getMessage(), null));
                        android.util.Log.d("APICall", "Setting error result: " + apiResponse.getMessage());
                    }
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                        android.util.Log.d("APICall", "Error body: " + errorBody);
                    } catch (Exception e) {
                        android.util.Log.d("APICall", "Failed to read error body");
                    }
                    
                    // Check for expired token (HTTP 403)
                    if (response.code() == 403 && !isRetry) {
                        android.util.Log.d("APICall", "Received 403 (Forbidden) - possibly expired token");
                        handleExpiredToken(call, result);
                    } else {
                        String errorMessage = "HTTP Error " + response.code() + ": " + response.message();
                        result.postValue(Resource.error(errorMessage, null));
                        android.util.Log.d("APICall", "Setting error result: " + errorMessage);
                    }
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<T>> call, Throwable t) {
                android.util.Log.d("APICall", "Call failed: " + t.getMessage());
                result.postValue(Resource.error("Network error: " + t.getMessage(), null));
                onFailure(call, t); // Call the abstract method
            }
        });
        
        return result;
    }
    
    /**
     * Handle expired token by refreshing it and retrying the original request
     * @param originalCall The original API call that failed due to expired token
     * @param result The MutableLiveData to update with the result
     */
    protected <T> void handleExpiredToken(Call<ApiResponse<T>> originalCall, MutableLiveData<Resource<T>> result) {
        // This must be implemented by subclasses that have access to UserRepository
        // Default implementation just returns error
        android.util.Log.e("BaseRepository", "Token refresh required but not implemented in this repository");
        result.postValue(Resource.error("Session expired, please login again", null));
    }

    public abstract void onFailure(Call<ApiResponse<LobbyListResponse>> call, Throwable t) // End of onFailure
    ;

    /**
     * Resource wrapper class that represents the state of a network operation
     */
    public static class Resource<T> {
        public enum Status { SUCCESS, ERROR, LOADING }
        private final Status status;
        private final T data;
        private final String message;
        
        private Resource(Status status, T data, String message) {
            this.status = status;
            this.data = data;
            this.message = message;
        }
        
        public static <T> Resource<T> success(T data) {
            return new Resource<>(Status.SUCCESS, data, null);
        }
        
        public static <T> Resource<T> error(String message, T data) {
            return new Resource<>(Status.ERROR, data, message);
        }
        
        public static <T> Resource<T> loading(T data) {
            return new Resource<>(Status.LOADING, data, null);
        }
        
        public Status getStatus() {
            return status;
        }
        
        public T getData() {
            return data;
        }
        
        public String getMessage() {
            return message;
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
        
        public boolean isLoading() {
            return status == Status.LOADING;
        }
        
        public boolean isError() {
            return status == Status.ERROR;
        }
    }
}

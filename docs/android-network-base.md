# Android Network Components - Base Documentation

## Overview

This document details the essential networking components in the DrawIt Android application, focusing on API services, WebSocket implementation, and network message handling.

## Key Components

### ApiService Interface

**File Path**: `com.example.drawit_app.network.ApiService`

**Purpose**: Defines the REST API endpoints for communication with the DrawIt server.

**Implementation Details**:

```java
public interface ApiService {
    
    // User Authentication
    @POST("users")
    Call<ApiResponse<User>> registerUser(@Body RegisterRequest registerRequest);
    
    @POST("users/login")
    Call<ApiResponse<AuthResponse>> loginUser(@Body LoginRequest loginRequest);
    
    // User Profile
    @GET("users/{userId}")
    Call<ApiResponse<User>> getUser(@Path("userId") String userId);
    
    @PUT("users/{userId}")
    Call<ApiResponse<User>> updateUser(@Path("userId") String userId, @Body UpdateUserRequest updateRequest);
    
    // Lobbies
    @GET("lobbies")
    Call<ApiResponse<List<Lobby>>> getLobbies();
    
    @POST("lobbies")
    Call<ApiResponse<Lobby>> createLobby(@Body CreateLobbyRequest createLobbyRequest);
    
    @GET("lobbies/{lobbyId}")
    Call<ApiResponse<Lobby>> getLobby(@Path("lobbyId") String lobbyId);
    
    @POST("lobbies/{lobbyId}/join")
    Call<ApiResponse<Lobby>> joinLobby(@Path("lobbyId") String lobbyId, @Body JoinLobbyRequest joinLobbyRequest);
    
    @POST("lobbies/{lobbyId}/leave")
    Call<ApiResponse<Void>> leaveLobby(@Path("lobbyId") String lobbyId);
    
    @PUT("lobbies/{lobbyId}/ready")
    Call<ApiResponse<Void>> setReady(@Path("lobbyId") String lobbyId, @Body ReadyRequest readyRequest);
    
    // Games
    @GET("games/{gameId}")
    Call<ApiResponse<Game>> getGame(@Path("gameId") String gameId);
}
```

**Configuration**:

```java
// NetworkModule.java
@Provides
@Singleton
public ApiService provideApiService(OkHttpClient okHttpClient, Moshi moshi) {
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build();
        
    return retrofit.create(ApiService.class);
}
```

### RetrofitClient

**File Path**: `com.example.drawit_app.network.RetrofitClient` 

**Purpose**: Singleton that manages Retrofit instance creation and API service access.

**Implementation Details**:

```java
public class RetrofitClient {
    private static final String BASE_URL = "http://your-server-address/api/";
    private static RetrofitClient instance;
    private final Retrofit retrofit;
    private final ApiService apiService;
    
    private RetrofitClient() {
        // Custom OkHttpClient with authentication interceptor
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request original = chain.request();
                
                // Add auth token if available
                String token = PreferenceManager.getDefaultSharedPreferences(AppContext.get())
                    .getString("auth_token", null);
                
                if (token != null) {
                    Request.Builder requestBuilder = original.newBuilder()
                        .header("Authorization", "Bearer " + token);
                    return chain.proceed(requestBuilder.build());
                }
                
                return chain.proceed(original);
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
            
        // Create Moshi adapter for JSON parsing
        Moshi moshi = new Moshi.Builder()
            .add(new PolymorphicJsonAdapterFactory.of(WebSocketMessage.class, "type")
                .withSubtype(LobbyStateMessage.class, LobbyStateMessage.TYPE)
                .withSubtype(ChatMessage.class, ChatMessage.TYPE)
                .withSubtype(GameStateMessage.class, GameStateMessage.TYPE)
                .withSubtype(DrawingDataMessage.class, DrawingDataMessage.TYPE))
            .build();
            
        retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build();
            
        apiService = retrofit.create(ApiService.class);
    }
    
    // Singleton instance getter
    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }
    
    // API service getter
    public ApiService getApi() {
        return apiService;
    }
}
```

### ApiResponse Class

**File Path**: `com.example.drawit_app.network.model.ApiResponse`

**Purpose**: Generic wrapper for server responses providing consistent structure.

**Implementation Details**:

```java
public class ApiResponse<T> {
    @Json(name = "success")
    private boolean success;
    
    @Json(name = "message")
    private String message;
    
    @Json(name = "data")
    private T data;
    
    // Getters and setters
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public T getData() {
        return data;
    }
}
```

## Important Request/Response Models

### Authentication Models

```java
// User registration
public class RegisterRequest {
    @Json(name = "username")
    private String username;
    
    @Json(name = "password")
    private String password;
}

// Authentication response
public class AuthResponse {
    @Json(name = "token")
    private String token;
    
    @Json(name = "user")
    private User user;
}
```

### Lobby Request Models

```java
// Create lobby request
public class CreateLobbyRequest {
    @Json(name = "name")
    private String name;
    
    @Json(name = "maxPlayers")
    private int maxPlayers;
    
    @Json(name = "isPrivate")
    private boolean isPrivate;
    
    @Json(name = "password")
    private String password;
    
    @Json(name = "numRounds")
    private int numRounds;
    
    @Json(name = "roundDurationSeconds")
    private int roundDurationSeconds;
}

// Join lobby request
public class JoinLobbyRequest {
    @Json(name = "password")
    private String password;
}
```

## Common Pitfalls and Best Practices

### Authentication Token Management

**Issue**: Token expiration and invalidation can cause API requests to fail

**Solution**:
- Implement token refresh logic
- Handle 401 unauthorized responses globally
- Clear token and redirect to login on authentication failures

```java
public class AuthInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        
        if (response.code() == 401) {
            // Clear token and emit global auth error event
            PreferenceManager.getDefaultSharedPreferences(AppContext.get())
                .edit()
                .remove("auth_token")
                .apply();
                
            EventBus.getDefault().post(new AuthenticationErrorEvent());
        }
        
        return response;
    }
}
```

### Error Handling

**Issue**: Network failures are not consistently handled

**Solution**:
- Use enqueue with proper callback handling
- Implement meaningful error messages for users
- Log detailed errors for debugging

```java
apiService.getLobby(lobbyId).enqueue(new Callback<ApiResponse<Lobby>>() {
    @Override
    public void onResponse(Call<ApiResponse<Lobby>> call, Response<ApiResponse<Lobby>> response) {
        if (response.isSuccessful() && response.body() != null) {
            if (response.body().isSuccess()) {
                // Handle successful response
                Lobby lobby = response.body().getData();
                // Update UI or state
            } else {
                // API returned error message
                String errorMessage = response.body().getMessage();
                // Show error to user
            }
        } else {
            // HTTP error
            try {
                Gson gson = new Gson();
                ApiResponse errorResponse = gson.fromJson(
                    response.errorBody().string(),
                    ApiResponse.class
                );
                String errorMessage = errorResponse.getMessage();
                // Show specific error message
            } catch (Exception e) {
                // Failed to parse error
                // Show generic error
            }
        }
    }
    
    @Override
    public void onFailure(Call<ApiResponse<Lobby>> call, Throwable t) {
        // Network failure
        Log.e(TAG, "Network request failed", t);
        // Show network error message
    }
});
```

### Thread Management

**Issue**: Network calls on main thread causing ANR (Application Not Responding)

**Solution**:
- Ensure all network calls happen off main thread
- Use proper threading or coroutines for async operations
- Consider using LiveData or observables for updating UI

```java
// Bad practice - blocking main thread
try {
    Response<ApiResponse<Lobby>> response = apiService.getLobby(lobbyId).execute();
    // Process response on main thread - will cause ANR
} catch (IOException e) {
    // Handle exception
}

// Better practice - async callback
apiService.getLobby(lobbyId).enqueue(new Callback<ApiResponse<Lobby>>() {
    // Callback implementation
});

// Best practice - with ViewModel and LiveData
public class LobbyViewModel extends ViewModel {
    private MutableLiveData<Lobby> lobbyLiveData = new MutableLiveData<>();
    
    public LiveData<Lobby> getLobby() {
        return lobbyLiveData;
    }
    
    public void fetchLobby(String lobbyId) {
        apiService.getLobby(lobbyId).enqueue(new Callback<ApiResponse<Lobby>>() {
            @Override
            public void onResponse(Call<ApiResponse<Lobby>> call, Response<ApiResponse<Lobby>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    lobbyLiveData.setValue(response.body().getData());
                } else {
                    // Handle error
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<Lobby>> call, Throwable t) {
                // Handle network failure
            }
        });
    }
}
```

package com.example.drawit_app.api;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;
import com.example.drawit_app.api.request.AuthRequest;
import com.example.drawit_app.api.request.CreateLobbyRequest;
import com.example.drawit_app.api.request.JoinLobbyRequest;
import com.example.drawit_app.api.request.RateDrawingRequest;
import com.example.drawit_app.api.request.RefreshTokenRequest;
import com.example.drawit_app.api.request.RegisterRequest;
import com.example.drawit_app.api.request.UpdateProfileRequest;
import com.example.drawit_app.api.response.ApiResponse;
import com.example.drawit_app.api.response.AuthResponse;
import com.example.drawit_app.api.response.LobbyListResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit API service interface for REST endpoints
 */
public interface ApiService {
    
    // Authentication endpoints
    @POST("auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequest request);
    
    @POST("auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body AuthRequest request);
    
    @POST("auth/logout")
    Call<ApiResponse<Void>> logout(@Header("Authorization") String token, @Query("deviceId") String deviceId);
    
    @POST("auth/refresh-token")
    Call<ApiResponse<AuthResponse>> refreshToken(@Body RefreshTokenRequest request);
    
    // User profile endpoints
    @GET("users/profile")
    Call<ApiResponse<User>> getCurrentUser(@Header("Authorization") String token);
    
    @PUT("users/profile")
    Call<ApiResponse<User>> updateProfile(@Header("Authorization") String token, @Body UpdateProfileRequest request);
    
    // Get user by ID endpoint
    @GET("users/{userId}")
    Call<ApiResponse<User>> getUserById(@Header("Authorization") String token, @Path("userId") String userId);
    
    // Lobby endpoints
    @GET("lobbies")
    Call<ApiResponse<LobbyListResponse>> getLobbies(@Header("Authorization") String token);
    
    @POST("lobbies")
    Call<ApiResponse<Lobby>> createLobby(@Header("Authorization") String token, @Body CreateLobbyRequest request);
    
    @GET("lobbies/{lobbyId}")
    Call<ApiResponse<Lobby>> getLobbyDetails(@Header("Authorization") String token, @Path("lobbyId") String lobbyId);
    
    @POST("lobbies/{lobbyId}/join")
    Call<ApiResponse<Lobby>> joinLobby(@Header("Authorization") String token, @Path("lobbyId") String lobbyId, @Body JoinLobbyRequest joinLobbyRequest);
    
    @DELETE("lobbies/{lobbyId}/leave")
    Call<ApiResponse<Void>> leaveLobby(@Header("Authorization") String token, @Path("lobbyId") String lobbyId);
    
    @DELETE("lobbies/{lobbyId}")
    Call<ApiResponse<Void>> deleteLobby(@Header("Authorization") String token, @Path("lobbyId") String lobbyId);
    
    @PUT("lobbies/{lobbyId}/settings")
    Call<ApiResponse<Lobby>> updateLobbySettings(@Header("Authorization") String token, @Path("lobbyId") String lobbyId, @Body CreateLobbyRequest request);
    
    @PUT("lobbies/{lobbyId}/lock")
    Call<ApiResponse<Lobby>> toggleLobbyLock(@Header("Authorization") String token, @Path("lobbyId") String lobbyId, @Body boolean isLocked);
    
    // Game endpoints
    @POST("lobbies/{lobbyId}/start-game")
    Call<ApiResponse<Game>> startGame(@Header("Authorization") String token, @Path("lobbyId") String lobbyId);
    
    @GET("games/{gameId}")
    Call<ApiResponse<Game>> getGameDetails(@Header("Authorization") String token, @Path("gameId") String gameId);
    
    // NOTE: Game joining is handled via WebSocket messages, not REST API
    
    // Drawing endpoints
    @POST("games/{gameId}/drawings")
    Call<ApiResponse<Drawing>> submitDrawing(@Header("Authorization") String token, @Path("gameId") String gameId, @Body Drawing drawing);
    
    @POST("games/{gameId}/drawings/{drawingId}/rate")
    Call<ApiResponse<Drawing>> rateDrawing(@Header("Authorization") String token, @Path("gameId") String gameId, 
                                          @Path("drawingId") String drawingId, @Body RateDrawingRequest request);
    
    @GET("users/drawings")
    Call<ApiResponse<List<Drawing>>> getUserDrawings(@Header("Authorization") String token);
    
    @GET("users/drawings/search/{word}")
    Call<ApiResponse<List<Drawing>>> searchUserDrawings(@Header("Authorization") String token, @Path("word") String word);
    
    @GET("drawings/{drawingId}")
    Call<ApiResponse<Drawing>> getDrawing(@Header("Authorization") String token, @Path("drawingId") String drawingId);
}

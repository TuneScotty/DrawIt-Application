package com.example.drawit_app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.drawit_app.model.Lobby;

import java.util.List;

/**
 * Data Access Object for Lobby entities
 */
@Dao
public interface LobbyDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Lobby lobby);
    
    @Update
    void update(Lobby lobby);
    
    @Delete
    void delete(Lobby lobby);
    
    @Query("SELECT * FROM lobbies WHERE lobbyId = :lobbyId")
    LiveData<Lobby> getLobbyById(String lobbyId);
    
    @Query("SELECT * FROM lobbies WHERE isLocked = 0")
    LiveData<List<Lobby>> getAvailableLobbies();
    
    @Query("SELECT * FROM lobbies WHERE hostId = :userId")
    LiveData<List<Lobby>> getLobbiesHostedBy(String userId);
    
    @Query("UPDATE lobbies SET isLocked = :isLocked WHERE lobbyId = :lobbyId")
    void updateLobbyLockStatus(String lobbyId, boolean isLocked);
    
    @Query("UPDATE lobbies SET hostId = :newHostId WHERE lobbyId = :lobbyId")
    void updateLobbyHost(String lobbyId, String newHostId);
    
    @Query("UPDATE lobbies SET numRounds = :numRounds, roundDurationSeconds = :durationSeconds WHERE lobbyId = :lobbyId")
    void updateLobbySettings(String lobbyId, int numRounds, int durationSeconds);
    
    @Query("DELETE FROM lobbies WHERE lobbyId = :lobbyId")
    void deleteLobbyById(String lobbyId);
    
    @Query("DELETE FROM lobbies")
    void deleteAllLobbiesDirectly();
}

package com.example.drawit_app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.drawit_app.model.Game;

import java.util.List;

/**
 * Data Access Object for Game entities
 */
@Dao
public interface GameDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Game game);
    
    @Update
    void update(Game game);
    
    @Delete
    void delete(Game game);
    
    @Query("SELECT * FROM games WHERE gameId = :gameId")
    LiveData<Game> getGameById(String gameId);
    
    @Query("SELECT * FROM games WHERE lobbyId = :lobbyId")
    LiveData<List<Game>> getGamesByLobbyId(String lobbyId);
    
    @Query("SELECT * FROM games WHERE gameState != 'FINISHED' ORDER BY startTime DESC LIMIT 1")
    LiveData<Game> getActiveGame();
    
    @Query("SELECT * FROM games ORDER BY startTime DESC")
    LiveData<List<Game>> getAllGames();
    
    @Query("UPDATE games SET gameState = :gameState WHERE gameId = :gameId")
    void updateGameState(String gameId, String gameState);
    
    @Query("UPDATE games SET currentRound = currentRound + 1 WHERE gameId = :gameId")
    void incrementRound(String gameId);
    
    @Query("UPDATE games SET currentWord = :word WHERE gameId = :gameId")
    void updateCurrentWord(String gameId, String word);
}

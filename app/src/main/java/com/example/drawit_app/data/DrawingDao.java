package com.example.drawit_app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.drawit_app.model.Drawing;

import java.util.List;

/**
 * Data Access Object for Drawing entities
 */
@Dao
public interface DrawingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Drawing drawing);
    
    @Update
    void update(Drawing drawing);
    
    @Delete
    void delete(Drawing drawing);
    
    @Query("SELECT * FROM drawings WHERE drawingId = :drawingId")
    LiveData<Drawing> getDrawingById(String drawingId);
    
    @Query("SELECT * FROM drawings WHERE userId = :userId ORDER BY timestamp DESC")
    LiveData<List<Drawing>> getDrawingsByUserId(String userId);
    
    @Query("SELECT * FROM drawings WHERE gameId = :gameId AND roundNumber = :roundNumber")
    LiveData<List<Drawing>> getDrawingsForGameRound(String gameId, int roundNumber);
    
    @Query("SELECT * FROM drawings WHERE userId = :userId AND word LIKE '%' || :searchTerm || '%' ORDER BY timestamp DESC")
    LiveData<List<Drawing>> searchDrawingsByWord(String userId, String searchTerm);
    
    @Query("SELECT * FROM drawings WHERE userId = :userId ORDER BY averageRating DESC LIMIT :limit")
    LiveData<List<Drawing>> getTopRatedDrawings(String userId, int limit);
    
    @Query("UPDATE drawings SET averageRating = :rating, ratingCount = ratingCount + 1 WHERE drawingId = :drawingId")
    void updateDrawingRating(String drawingId, float rating);
    
    @Query("DELETE FROM drawings WHERE gameId = :gameId")
    void deleteDrawingsForGame(String gameId);
    
    @Query("SELECT * FROM drawings ORDER BY timestamp DESC")
    LiveData<List<Drawing>> getAllDrawings();
}

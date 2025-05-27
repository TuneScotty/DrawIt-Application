package com.example.drawit_app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.drawit_app.model.User;

import java.util.List;

/**
 * Data Access Object for User entities
 */
@Dao
public interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);
    
    @Update
    void update(User user);
    
    @Delete
    void delete(User user);
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    LiveData<User> getUserById(String userId);
    
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    LiveData<User> getUserByUsername(String username);
    
    @Query("SELECT * FROM users")
    LiveData<List<User>> getAllUsers();
    
    @Query("DELETE FROM users")
    void deleteAll();
    
    @Query("UPDATE users SET averageRating = :newRating WHERE userId = :userId")
    void updateUserRating(String userId, float newRating);
    
    @Query("UPDATE users SET totalGamesPlayed = totalGamesPlayed + 1 WHERE userId = :userId")
    void incrementGamesPlayed(String userId);
}

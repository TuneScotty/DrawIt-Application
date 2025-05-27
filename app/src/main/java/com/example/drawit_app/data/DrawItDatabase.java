package com.example.drawit_app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.Lobby;
import com.example.drawit_app.model.User;

/**
 * Main database class for the DrawIt application.
 * Provides access to DAOs for all entity types.
 */
@Database(entities = {User.class, Lobby.class, Game.class, Drawing.class}, 
        version = 1, exportSchema = false)
@TypeConverters({DateConverter.class, GameStateConverter.class, 
        PlayerScoreConverter.class, DrawingPathsConverter.class})
public abstract class DrawItDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "drawit_db";
    private static volatile DrawItDatabase instance;

    // Define DAOs
    public abstract UserDao userDao();
    public abstract LobbyDao lobbyDao();
    public abstract GameDao gameDao();
    public abstract DrawingDao drawingDao();

    // Singleton pattern to prevent multiple instances of database opening at the same time
    public static synchronized DrawItDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    DrawItDatabase.class,
                    DATABASE_NAME)
                    .fallbackToDestructiveMigration() // Recreate database if no migration found
                    .build();
        }
        return instance;
    }
}

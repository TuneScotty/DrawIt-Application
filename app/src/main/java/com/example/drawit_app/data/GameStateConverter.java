package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import com.example.drawit_app.model.Game.GameState;

/**
 * Room TypeConverter for GameState enum
 */
public class GameStateConverter {
    
    @TypeConverter
    public static String fromGameState(GameState gameState) {
        if (gameState == null) {
            return null;
        }
        return gameState.name();
    }
    
    @TypeConverter
    public static GameState toGameState(String gameStateName) {
        if (gameStateName == null) {
            return null;
        }
        return GameState.valueOf(gameStateName);
    }
}

package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Room TypeConverter for player scores map in Game class
 */
public class PlayerScoreConverter {
    
    private static final Gson gson = new Gson();
    
    @TypeConverter
    public static String fromPlayerScores(Map<String, Float> playerScores) {
        if (playerScores == null) {
            return null;
        }
        return gson.toJson(playerScores);
    }
    
    @TypeConverter
    public static Map<String, Float> toPlayerScores(String playerScoresJson) {
        if (playerScoresJson == null) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, Float>>() {}.getType();
        return gson.fromJson(playerScoresJson, type);
    }
}

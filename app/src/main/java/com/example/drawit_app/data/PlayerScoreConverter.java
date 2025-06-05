package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Room TypeConverter for player scores map in Game class
 */
public class PlayerScoreConverter {
    
    private static final Moshi moshi = new Moshi.Builder().build();
    
    @TypeConverter
    public static String fromPlayerScores(Map<String, Float> playerScores) {
        if (playerScores == null) {
            return null;
        }
        
        Type type = Types.newParameterizedType(Map.class, String.class, Float.class);
        JsonAdapter<Map<String, Float>> adapter = moshi.adapter(type);
        return adapter.toJson(playerScores);
    }
    
    @TypeConverter
    public static Map<String, Float> toPlayerScores(String playerScoresJson) {
        if (playerScoresJson == null) {
            return new HashMap<>();
        }
        
        Type type = Types.newParameterizedType(Map.class, String.class, Float.class);
        JsonAdapter<Map<String, Float>> adapter = moshi.adapter(type);
        try {
            Map<String, Float> result = adapter.fromJson(playerScoresJson);
            return result != null ? result : new HashMap<>();
        } catch (IOException e) {
            android.util.Log.e("PlayerScoreConverter", "Error converting JSON to player scores", e);
            return new HashMap<>();
        }
    }
}

package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import com.example.drawit_app.model.Drawing.DrawingPath;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Room TypeConverter for DrawingPath lists in Drawing class
 */
public class DrawingPathsConverter {
    
    private static final Gson gson = new Gson();
    
    @TypeConverter
    public static String fromDrawingPaths(List<DrawingPath> paths) {
        if (paths == null) {
            return null;
        }
        return gson.toJson(paths);
    }
    
    @TypeConverter
    public static List<DrawingPath> toDrawingPaths(String pathsJson) {
        if (pathsJson == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<DrawingPath>>() {}.getType();
        return gson.fromJson(pathsJson, type);
    }
}

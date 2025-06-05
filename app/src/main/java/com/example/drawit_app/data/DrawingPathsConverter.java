package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import com.example.drawit_app.model.Drawing.DrawingPath;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Room TypeConverter for DrawingPath lists in Drawing class
 */
public class DrawingPathsConverter {
    
    private static final Moshi moshi = new Moshi.Builder().build();
    
    @TypeConverter
    public static String fromDrawingPaths(List<DrawingPath> paths) {
        if (paths == null) {
            return null;
        }
        
        Type type = Types.newParameterizedType(List.class, DrawingPath.class);
        JsonAdapter<List<DrawingPath>> adapter = moshi.adapter(type);
        return adapter.toJson(paths);
    }
    
    @TypeConverter
    public static List<DrawingPath> toDrawingPaths(String pathsJson) {
        if (pathsJson == null) {
            return new ArrayList<>();
        }
        
        Type type = Types.newParameterizedType(List.class, DrawingPath.class);
        JsonAdapter<List<DrawingPath>> adapter = moshi.adapter(type);
        try {
            List<DrawingPath> result = adapter.fromJson(pathsJson);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            android.util.Log.e("DrawingPathsConverter", "Error converting JSON to drawing paths", e);
            return new ArrayList<>();
        }
    }
}

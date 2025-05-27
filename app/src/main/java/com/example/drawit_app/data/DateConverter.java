package com.example.drawit_app.data;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Date converters for Room database
 */
public class DateConverter {
    
    /**
     * Converts Date to timestamp for storage
     */
    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    /**
     * Converts timestamp to Date for retrieval
     */
    @TypeConverter
    public static Date timestampToDate(Long timestamp) {
        return timestamp == null ? null : new Date(timestamp);
    }
}

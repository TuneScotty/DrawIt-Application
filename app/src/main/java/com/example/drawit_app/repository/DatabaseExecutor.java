package com.example.drawit_app.repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class to execute database operations on a background thread
 */
public class DatabaseExecutor {
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    /**
     * Execute a database operation on a background thread
     * @param operation The operation to execute
     */
    public static void execute(Runnable operation) {
        executor.execute(operation);
    }
}

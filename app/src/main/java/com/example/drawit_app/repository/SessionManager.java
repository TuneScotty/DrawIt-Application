package com.example.drawit_app.repository;

import android.content.Context;
import android.provider.Settings;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * Manages user sessions and prevents multiple device logins
 * Ensures security with proper token handling and session management
 */
public class SessionManager {
    private static final String PREF_NAME = "drawit_session_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_TOKEN_CREATION_TIME = "token_creation_time";
    private static final String KEY_TOKEN_EXPIRY = "token_expiry";
    
    // Token will expire after 7 days (in milliseconds)
    private static final long TOKEN_EXPIRY_DURATION = 7 * 24 * 60 * 60 * 1000;
    
    private final Context context;
    
    public SessionManager(Context context) {
        this.context = context;
        ensureDeviceId();
    }
    
    /**
     * Get the unique device ID or create it if it doesn't exist
     */
    public String getDeviceId() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            String deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null);
            if (deviceId == null) {
                // Create a unique device ID based on the Android ID and a UUID
                String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                deviceId = androidId + "-" + UUID.randomUUID().toString();
                sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            }
            
            return deviceId;
        } catch (GeneralSecurityException | IOException e) {
            // Fallback to a new UUID if encryption fails
            e.printStackTrace();
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * Ensure a device ID exists
     */
    private void ensureDeviceId() {
        getDeviceId();
    }
    
    /**
     * Create a new session when user logs in
     * Securely stores the token with creation time and expiry
     */
    public String createSession(String token) {
        String sessionId = UUID.randomUUID().toString();
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            long currentTime = System.currentTimeMillis();
            long expiryTime = currentTime + TOKEN_EXPIRY_DURATION;
            
            sharedPreferences.edit()
                    .putString(KEY_SESSION_ID, sessionId)
                    .putString(KEY_SESSION_TOKEN, token)
                    .putLong(KEY_TOKEN_CREATION_TIME, currentTime)
                    .putLong(KEY_TOKEN_EXPIRY, expiryTime)
                    .apply();
            
            android.util.Log.d("SessionManager", "New session created with ID: " + sessionId);
            return sessionId;
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return sessionId;
        }
    }
    
    /**
     * Get the current session ID
     * Checks if the token is expired and returns null if it is
     */
    public String getSessionId() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            String sessionId = sharedPreferences.getString(KEY_SESSION_ID, null);
            
            // Check if token is expired
            if (sessionId != null) {
                long expiryTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0);
                if (System.currentTimeMillis() > expiryTime) {
                    android.util.Log.d("SessionManager", "Session token expired, clearing session");
                    clearSession();
                    return null;
                }
            }
            
            return sessionId;
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get the current session token
     */
    public String getSessionToken() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            // Check if token is expired
            long expiryTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0);
            if (System.currentTimeMillis() > expiryTime) {
                android.util.Log.d("SessionManager", "Session token expired, clearing session");
                clearSession();
                return null;
            }
            
            return sharedPreferences.getString(KEY_SESSION_TOKEN, null);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Check if the current session is valid
     */
    public boolean isSessionValid() {
        String sessionId = getSessionId();
        String token = getSessionToken();
        
        return sessionId != null && token != null;
    }
    
    /**
     * Clear the current session
     */
    public void clearSession() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            EncryptedSharedPreferences sharedPreferences = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            sharedPreferences.edit()
                    .remove(KEY_SESSION_ID)
                    .remove(KEY_SESSION_TOKEN)
                    .apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}

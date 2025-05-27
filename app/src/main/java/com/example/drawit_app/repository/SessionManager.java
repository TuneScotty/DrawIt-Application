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
 */
public class SessionManager {
    private static final String PREF_NAME = "drawit_session_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_TOKEN = "session_token";
    
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
            
            sharedPreferences.edit()
                    .putString(KEY_SESSION_ID, sessionId)
                    .putString(KEY_SESSION_TOKEN, token)
                    .apply();
            
            return sessionId;
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return sessionId;
        }
    }
    
    /**
     * Get the current session ID
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
            
            return sharedPreferences.getString(KEY_SESSION_ID, null);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return null;
        }
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

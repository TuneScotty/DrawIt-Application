package com.example.drawit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton, registerButton;
    private FirebaseHandler firebaseHandler;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "LoginPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        firebaseHandler = FirebaseHandler.getInstance();

        setupViews();
        checkPreviousLogin();
    }

    private void setupViews() {
        emailEditText = findViewById(R.id.editTextTextEmailAddress);
        passwordEditText = findViewById(R.id.editTextTextPassword);
        loginButton = findViewById(R.id.button);
        registerButton = findViewById(R.id.registerButton);

        loginButton.setOnClickListener(v -> handleLogin());
        registerButton.setOnClickListener(v -> handleRegister());
    }

    private void checkPreviousLogin() {
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            String email = sharedPreferences.getString(KEY_EMAIL, "");
            String password = sharedPreferences.getString(KEY_PASSWORD, "");
            
            emailEditText.setText(email);
            passwordEditText.setText(password);
            
            // Auto login if credentials exist
            handleLogin();
        }
    }

    private void handleLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Disable buttons to prevent multiple login attempts
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);

        firebaseHandler.loginUser(email, password, task -> {
            // Re-enable buttons regardless of outcome
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);
                
                if (task.isSuccessful()) {
                    saveLoginCredentials(email, password);
                    startMainActivity();
                } else {
                    String errorMessage = "Login failed";
                    if (task.getException() != null && task.getException().getMessage() != null) {
                        errorMessage += ": " + task.getException().getMessage();
                    }
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleRegister() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Disable buttons to prevent multiple registration attempts
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);

        firebaseHandler.registerUser(email, password, task -> {
            // Re-enable buttons regardless of outcome
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);
                
                if (task.isSuccessful()) {
                    saveLoginCredentials(email, password);
                    startMainActivity();
                } else {
                    String errorMessage = "Registration failed";
                    if (task.getException() != null && task.getException().getMessage() != null) {
                        errorMessage += ": " + task.getException().getMessage();
                    }
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveLoginCredentials(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    private void startMainActivity() {
        try {
            Intent intent = new Intent(MainActivity.this, MainActivity2.class);
            // Add flags to create a new task and clear the previous ones
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            // Log and handle any exceptions during activity transition
            Toast.makeText(this, "Error starting application: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Optional: Add a logout method to clear credentials
    public static void logout(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }
}

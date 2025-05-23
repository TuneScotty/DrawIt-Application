package com.example.drawit.views;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.R;
import com.example.drawit.game.repositories.GameSettingsRepository;
import com.example.drawit.models.GameSettings;

/**
 * Activity allowing the host to configure game settings.
 * 
 * This activity follows the MVVM pattern by separating UI logic from business logic.
 * It uses a repository to handle data operations, making the code more testable and maintainable.
 */
public class LobbySettingsActivity extends AppCompatActivity {
    private static final String TAG = "LobbySettingsActivity";
    
    // UI Components
    private SeekBar roundsSeekBar;
    private TextView roundsValueText;
    private SeekBar timerSeekBar;
    private TextView timerValueText;
    private CheckBox enableWordCustomizationCheckbox;
    private CheckBox showScoresDuringGameCheckbox;
    private Button saveButton;
    
    // Data
    private GameSettingsRepository settingsRepository;
    private String lobbyId;
    private GameSettings currentSettings;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby_settings);
        
        // Setup action bar with back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Game Settings");
        }
        
        // Get lobby ID from intent
        lobbyId = getIntent().getStringExtra("lobby_id");
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.e(TAG, "No lobby ID provided");
            Toast.makeText(this, "Error: No lobby ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Initialize repository
        settingsRepository = new GameSettingsRepository();
        
        // Initialize UI components
        initializeViews();
        
        // Load existing settings
        loadSettings();
    }
    
    private void initializeViews() {
        // Find views
        roundsSeekBar = findViewById(R.id.rounds_seekbar);
        roundsValueText = findViewById(R.id.rounds_value_text);
        timerSeekBar = findViewById(R.id.timer_seekbar);
        timerValueText = findViewById(R.id.timer_value_text);
        enableWordCustomizationCheckbox = findViewById(R.id.enable_word_customization_checkbox);
        showScoresDuringGameCheckbox = findViewById(R.id.show_scores_checkbox);
        saveButton = findViewById(R.id.save_settings_button);
        
        // Configure rounds seekbar
        roundsSeekBar.setMin(GameSettings.MIN_ROUNDS);
        roundsSeekBar.setMax(GameSettings.MAX_ROUNDS);
        roundsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateRoundsText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Configure timer seekbar
        timerSeekBar.setMin(GameSettings.MIN_ROUND_DURATION_SECONDS);
        timerSeekBar.setMax(GameSettings.MAX_ROUND_DURATION_SECONDS);
        timerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTimerText(progress);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Configure save button
        saveButton.setOnClickListener(v -> saveSettings());
    }
    
    private void loadSettings() {
        // Show loading state
        setControlsEnabled(false);
        
        settingsRepository.getSettings(lobbyId, new GameSettingsRepository.SettingsCallback<GameSettings>() {
            @Override
            public void onSuccess(GameSettings settings) {
                currentSettings = settings;
                runOnUiThread(() -> {
                    // Update UI with retrieved settings
                    roundsSeekBar.setProgress(settings.getNumberOfRounds());
                    updateRoundsText(settings.getNumberOfRounds());
                    
                    timerSeekBar.setProgress(settings.getRoundDurationSeconds());
                    updateTimerText(settings.getRoundDurationSeconds());
                    
                    enableWordCustomizationCheckbox.setChecked(settings.isEnableWordCustomization());
                    showScoresDuringGameCheckbox.setChecked(settings.isShowScoresDuringGame());
                    
                    // Enable controls
                    setControlsEnabled(true);
                });
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading settings", e);
                runOnUiThread(() -> {
                    Toast.makeText(LobbySettingsActivity.this, 
                            "Error loading settings: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    
                    // Use default settings
                    currentSettings = new GameSettings();
                    
                    // Update UI with default settings
                    roundsSeekBar.setProgress(currentSettings.getNumberOfRounds());
                    updateRoundsText(currentSettings.getNumberOfRounds());
                    
                    timerSeekBar.setProgress(currentSettings.getRoundDurationSeconds());
                    updateTimerText(currentSettings.getRoundDurationSeconds());
                    
                    enableWordCustomizationCheckbox.setChecked(currentSettings.isEnableWordCustomization());
                    showScoresDuringGameCheckbox.setChecked(currentSettings.isShowScoresDuringGame());
                    
                    // Enable controls
                    setControlsEnabled(true);
                });
            }
        });
    }
    
    private void saveSettings() {
        // Create settings object from UI values
        GameSettings settings = new GameSettings(
                roundsSeekBar.getProgress(),
                timerSeekBar.getProgress(),
                enableWordCustomizationCheckbox.isChecked(),
                showScoresDuringGameCheckbox.isChecked()
        );
        
        // Show saving state
        setControlsEnabled(false);
        
        // Save settings
        settingsRepository.saveSettings(lobbyId, settings, new GameSettingsRepository.SettingsCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(LobbySettingsActivity.this, 
                            "Settings saved successfully", 
                            Toast.LENGTH_SHORT).show();
                    
                    // Re-enable controls
                    setControlsEnabled(true);
                    
                    // Go back to lobby
                    finish();
                });
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error saving settings", e);
                runOnUiThread(() -> {
                    Toast.makeText(LobbySettingsActivity.this, 
                            "Error saving settings: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    
                    // Re-enable controls
                    setControlsEnabled(true);
                });
            }
        });
    }
    
    private void updateRoundsText(int rounds) {
        roundsValueText.setText(String.format("%d rounds", rounds));
    }
    
    private void updateTimerText(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        timerValueText.setText(String.format("%d:%02d per round", minutes, remainingSeconds));
    }
    
    private void setControlsEnabled(boolean enabled) {
        roundsSeekBar.setEnabled(enabled);
        timerSeekBar.setEnabled(enabled);
        enableWordCustomizationCheckbox.setEnabled(enabled);
        showScoresDuringGameCheckbox.setEnabled(enabled);
        saveButton.setEnabled(enabled);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

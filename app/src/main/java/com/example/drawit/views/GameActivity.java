package com.example.drawit.views;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.R;
import com.example.drawit.game.FirebaseHandler;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.models.Game;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.models.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.drawit.R;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.Game;
import com.example.drawit.models.Player;
import com.example.drawit.game.FirebaseHandler;
import com.example.drawit.game.DefaultWordProvider;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.game.models.Guess;
import com.example.drawit.game.repositories.GameSettingsRepository;
import com.example.drawit.game.services.GameSettingsService;
import com.example.drawit.models.GameSettings;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameActivity extends BaseActivity implements GameManager.GameEventListener {
    private static final String EXTRA_LOBBY_ID = "lobby_id";
    private static final String EXTRA_USER_ID = "user_id";
    private static final String EXTRA_IS_HOST = "is_host";
    
    private static final String TAG = "GameActivity";
    
    private GameManager gameManager;
    private GameUI gameUI;
    private GameInitializer gameInitializer;
    private FirebaseHandler firebaseHandler;
    private Timer timer;
    private String lobbyId;
    private String userId;
    
    /**
     * Get the current user ID
     * @return The user ID of the current player
     */
    public String getCurrentUserId() {
        return userId;
    }
    private boolean isHost;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        
        // Get intent extras
        lobbyId = getIntent().getStringExtra(EXTRA_LOBBY_ID);
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        isHost = getIntent().getBooleanExtra(EXTRA_IS_HOST, false);
        
        // Validate required parameters
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.e(TAG, "Missing required parameter: lobbyId");
            Toast.makeText(this, "Error: Missing game information", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "Missing required parameter: userId");
            Toast.makeText(this, "Error: Missing user information", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        try {
            // Initialize Firebase handler with proper error handling
            try {
                firebaseHandler = FirebaseHandler.getInstance();
                if (firebaseHandler == null) {
                    throw new RuntimeException("Failed to initialize Firebase handler");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing Firebase handler", e);
                Toast.makeText(this, "Connection error: Please check your internet connection", 
                    Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            // Initialize game manager with DefaultWordProvider
            gameManager = new GameManager(new DefaultWordProvider());
            
            // Load game settings
            loadGameSettings(lobbyId);
            
            // Initialize UI before game initializer to ensure UI is ready
            gameUI = new GameUI(
                getWindow().getDecorView().getRootView(),
                gameManager,
                userId,
                isHost
            );
            
            // Setup game listeners
            setupGameListeners();
            
            // Initialize game initializer with proper error handling
            try {
                gameInitializer = new GameInitializer(
                    firebaseHandler,
                    lobbyId,
                    userId,
                    gameManager,
                    isHost
                );
                
                // Initialize the game
                gameInitializer.initializeGame();
                
                // Force UI update to ensure drawing tools are visible
                runOnUiThread(() -> {
                    if (gameUI != null) {
                        // Force the game into a drawing state to ensure UI elements appear
                        gameUI.updateGameState(GameStatus.DRAWING);
                        
                        // Make sure we can see and use the drawing tools
                        View drawingToolsContainer = findViewById(R.id.drawing_tools_container);
                        View colorPickerContainer = findViewById(R.id.color_picker_container);
                        TextView wordToDrawTextView = findViewById(R.id.word_to_draw_text_view);
                        
                        if (drawingToolsContainer != null) {
                            drawingToolsContainer.setVisibility(View.VISIBLE);
                        }
                        
                        if (colorPickerContainer != null) {
                            colorPickerContainer.setVisibility(View.VISIBLE);
                        }
                        
                        // Set a random word if none exists
                        if (wordToDrawTextView != null) {
                            wordToDrawTextView.setVisibility(View.VISIBLE);
                            // Get a random word from the word provider
                            String randomWord = gameManager.getWordProvider().getRandomWord();
                            wordToDrawTextView.setText("Draw: " + randomWord);
                            
                            // Update the game state with this word
                            Game game = gameManager.getGame();
                            if (game != null) {
                                game.setCurrentWord(randomWord);
                            }
                        }
                        
                        // Start the timer
                        startTimer();
                        
                        // Log that we've forced the UI update
                        Log.d(TAG, "Forced game UI into drawing state with timer and word");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in game initialization", e);
                // Show a more specific error message
                Toast.makeText(this, "Game setup error: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
                // Don't immediately finish the activity - let the user retry
                return;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Unhandled error in game setup", e);
            // Show error to user
            Toast.makeText(this, "Failed to initialize game: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            // Don't immediately finish - give a chance to retry
        }
    }
    
    private void setupGameListeners() {
        // Set the game event listener
        gameManager.setEventListener(this);
    }
    
    @Override
    public void onGameStarted(Game game) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            startTimer();
        });
    }
    
    @Override
    public void onRoundStarted(Game game) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            startTimer();
        });
    }
    
    @Override
    public void onWordSelectionStarted(Game game, Player drawer, List<String> wordChoices) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            startTimer();
        });
    }
    
    @Override
    public void onDrawingPhaseStarted(Game game) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            startTimer();
        });
    }
    
    @Override
    public void onRatingPhaseStarted(Game game) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            startTimer();
        });
    }
    
    @Override
    public void onDrawingActionReceived(Game game, DrawingAction action) {
        runOnUiThread(() -> {
            if (gameUI != null) {
                gameUI.processDrawingAction(action);
            }
        });
    }
    
    @Override
    public void onGuessReceived(Game game, Guess guess) {
        // Handle guess if needed
    }
    
    @Override
    public void onRoundEnded(Game game) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            stopTimer();
            
            // Check if we should move to next round or end the game
            if (game.getCurrentRound() >= game.getTotalRounds()) {
                // Last round finished, end the game
                gameManager.endGame("All rounds completed");
            } else {
                // Show the round summary before starting the next round
                showRoundSummary(game);
            }
        });
    }
    
    @Override
    public void onGameEnded(Game game, String reason) {
        runOnUiThread(() -> {
            gameUI.updateGameState(game.getStatus());
            stopTimer();
            Toast.makeText(this, "Game ended: " + reason, Toast.LENGTH_LONG).show();
            
            // Show rating screen and leaderboard
            showRatingScreen(game);
        });
    }
    
    @Override
    public void onPlayerJoined(Game game, Player player) {
        runOnUiThread(() -> gameUI.updatePlayerList(game.getPlayers()));
    }
    
    @Override
    public void onPlayerLeft(Game game, Player player) {
        runOnUiThread(() -> gameUI.updatePlayerList(game.getPlayers()));
    }
    
    @Override
    public void onPlayerDisconnected(Game game, Player player) {
        runOnUiThread(() -> gameUI.updatePlayerList(game.getPlayers()));
    }
    
    @Override
    public void onPlayerReconnected(Game game, Player player) {
        runOnUiThread(() -> gameUI.updatePlayerList(game.getPlayers()));
    }
    
    @Override
    public void onHostChanged(Game game, Player newHost) {
        // Update UI to show the new host if needed
        runOnUiThread(() -> {
            if (newHost != null) {
                String message = String.format("%s is now the host", newHost.getName());
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // Default starting time for the round in milliseconds (2 minutes)
    private static final long DEFAULT_ROUND_TIME = 120000;
    private long TOTAL_ROUND_TIME = DEFAULT_ROUND_TIME;
    private long roundStartTime;
    private long timeRemaining = TOTAL_ROUND_TIME;
    
    private void startTimer() {
        // Stop any existing timer
        stopTimer();
        
        // Initialize the starting time
        roundStartTime = System.currentTimeMillis();
        timeRemaining = TOTAL_ROUND_TIME;
        
        // Create a new timer to update the UI periodically
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Calculate elapsed time
                    long currentTime = System.currentTimeMillis();
                    long elapsedTime = currentTime - roundStartTime;
                    
                    // Calculate remaining time
                    timeRemaining = Math.max(0, TOTAL_ROUND_TIME - elapsedTime);
                    
                    // Update the UI on the main thread
                    runOnUiThread(() -> {
                        if (gameUI != null) {
                            gameUI.updateTimer(timeRemaining);
                            
                            // If time's up, end the round
                            if (timeRemaining <= 0) {
                                stopTimer();
                                Toast.makeText(GameActivity.this, "Time's up!", Toast.LENGTH_SHORT).show();
                                
                                // Handle end of round
                                handleRoundEnd();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error updating timer", e);
                }
            }
        }, 0, 250); // Update every 250ms for smooth countdown
        
        // Log that we've started the timer
        Log.d(TAG, "Started game timer with duration: " + (TOTAL_ROUND_TIME / 1000) + " seconds");
    }
    
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }
    
    /**
     * Load game settings from the repository
     */
    private void loadGameSettings(String lobbyId) {
        if (lobbyId == null || lobbyId.isEmpty()) {
            Log.e(TAG, "Cannot load game settings: Lobby ID is null or empty");
            return;
        }
        
        Log.d(TAG, "Loading game settings for lobby: " + lobbyId);
        GameSettingsService settingsService = GameSettingsService.getInstance();
        if (settingsService == null) {
            Log.e(TAG, "Game settings service is null");
            return;
        }
        
        settingsService.getGameSettings(lobbyId, new GameSettingsService.GameSettingsCallback() {
            @Override
            public void onSettingsLoaded(GameSettings settings) {
                if (settings == null) {
                    Log.e(TAG, "Loaded settings are null, using defaults");
                    TOTAL_ROUND_TIME = DEFAULT_ROUND_TIME;
                    return;
                }
                
                Log.d(TAG, "Loaded settings: rounds=" + settings.getNumberOfRounds() + 
                       ", duration=" + settings.getRoundDurationSeconds() + "s");
                
                // Apply settings to the game
                Game game = gameManager.getGame();
                if (game != null) {
                    game.setTotalRounds(settings.getNumberOfRounds());
                    
                    // Convert seconds to milliseconds for the timer
                    long roundTimeInMs = settings.getRoundDurationSeconds() * 1000;
                    TOTAL_ROUND_TIME = roundTimeInMs;
                    timeRemaining = roundTimeInMs; // Make sure the current timer also uses the new settings
                    
                    Log.d(TAG, "Applied settings to game: rounds=" + game.getTotalRounds() + 
                           ", duration=" + (TOTAL_ROUND_TIME / 1000) + "s");
                    
                    // If timer is running, restart it with new duration
                    if (timer != null) {
                        stopTimer();
                        startTimer();
                    }
                } else {
                    Log.e(TAG, "Game object is null, cannot apply settings");
                }
            }
            
            @Override
            public void onSettingsError(Exception e) {
                Log.e(TAG, "Error loading settings", e);
                // Use default settings
                TOTAL_ROUND_TIME = DEFAULT_ROUND_TIME;
                timeRemaining = DEFAULT_ROUND_TIME;
                
                // Still initialize the game with default settings
                if (gameManager != null && gameManager.getGame() != null) {
                    gameManager.getGame().setTotalRounds(3); // Default number of rounds
                }
            }
        });
    }
    
    /**
     * Handle the end of a round
     */
    private void handleRoundEnd() {
        Game game = gameManager.getGame();
        if (game != null) {
            // Log that the round has ended
            Log.d(TAG, "Round ended. Current round: " + game.getCurrentRound() + ", Total rounds: " + game.getTotalRounds());
            
            // First transition to rating phase before incrementing round counter
            if (isHost) {
                // Trigger transition to rating phase in Firebase
                firebaseHandler.updateGameStatus(lobbyId, GameStatus.RATING);
                Log.d(TAG, "Host triggered rating phase");
                
                // Save the current round's drawings for rating
                gameManager.saveRoundDrawings();
            }
            
            // Show the rating UI
            runOnUiThread(() -> {
                // Display all player drawings for rating
                gameUI.updateGameState(GameStatus.RATING);
                showRatingScreen(game);
                
                // Set a delay before moving to the next round or ending the game
                new Handler().postDelayed(() -> {
                    // After rating phase, check if this was the last round
                    if (game.getCurrentRound() >= game.getTotalRounds()) {
                        // If this was the last round, end the game
                        if (isHost) {
                            gameManager.endGame("All rounds completed");
                        }
                    } else {
                        // Otherwise, increment round and continue
                        int currentRound = game.getCurrentRound();
                        game.setCurrentRound(currentRound + 1);
                        
                        if (isHost) {
                            // Host triggers next round
                            gameManager.startNextRound();
                        }
                    }
                }, 10000); // 10-second rating phase
            });
        }
    }
    
    /**
     * Show round summary between rounds
     */
    private void showRoundSummary(Game game) {
        // Display a dialog showing the current round results
        // This could show the correct word, who guessed it, etc.
        gameUI.showRoundSummary(game, () -> {
            // Start the next round after a delay
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(() -> {
                        if (isHost) {
                            gameManager.startNextRound();
                        }
                    });
                }
            }, 3000); // 3 second delay before next round
        });
    }
    
    /**
     * Show the rating screen at the end of the game
     */
    // Track which player's drawing we're currently rating
    private int currentPlayerRatingIndex = 0;
    private List<Player> playersToRate = new ArrayList<>();
    
    /**
     * Shows the rating screen for all players' drawings from the current round
     */
    private void showRatingScreen(Game game) {
        Log.d(TAG, "Showing rating screen for round " + game.getCurrentRound());
        
        // Instead of showing just the current drawer, we'll show each player's drawing in sequence
        runOnUiThread(() -> {
            // Get all players who drew this round
            List<Player> allPlayers = game.getPlayers();
            if (allPlayers == null || allPlayers.isEmpty()) {
                Log.e(TAG, "No players to rate drawings for");
                completeRatingAndMoveOn(game);
                return;
            }
            
            // Reset our player index and build list of players to rate
            currentPlayerRatingIndex = 0;
            playersToRate.clear();
            
            // Add all players except the current player (we don't rate our own drawing)
            Player currentPlayer = game.getCurrentPlayer();
            for (Player player : allPlayers) {
                if (currentPlayer == null || !player.getId().equals(currentPlayer.getId())) {
                    playersToRate.add(player);
                }
            }
            
            Log.d(TAG, "Found " + playersToRate.size() + " players to rate");
            
            // Start the rating sequence
            if (!playersToRate.isEmpty()) {
                showNextPlayerDrawing(game);
            } else {
                Log.e(TAG, "No other players to rate");
                completeRatingAndMoveOn(game);
            }
        });
    }
    
    /**
     * Show the next player's drawing in the rating sequence
     */
    private void showNextPlayerDrawing(Game game) {
        if (currentPlayerRatingIndex >= playersToRate.size()) {
            // We've rated all players, move on
            completeRatingAndMoveOn(game);
            return;
        }
        
        // Get the current player to rate
        Player drawerPlayer = playersToRate.get(currentPlayerRatingIndex);
        Log.d(TAG, "Showing drawing for player: " + drawerPlayer.getName() + 
              " (" + (currentPlayerRatingIndex + 1) + " of " + playersToRate.size() + ")");
        
        // Get the word that was drawn
        String currentWord = game.getCurrentWord();
        if (currentWord == null || currentWord.isEmpty()) {
            Log.e(TAG, "No word found for the current round");
            currentPlayerRatingIndex++;
            showNextPlayerDrawing(game);
            return;
        }
        
        // Show a dialog with the drawer's name, the word, and the drawing
        View ratingView = getLayoutInflater().inflate(R.layout.rating_dialog, null);
        
        // Get references to views in the rating dialog
        TextView drawerNameTextView = ratingView.findViewById(R.id.drawer_name_text_view);
        TextView wordTextView = ratingView.findViewById(R.id.word_text_view);
        DrawingView drawingView = ratingView.findViewById(R.id.drawing_view);
        RatingBar ratingBar = ratingView.findViewById(R.id.rating_bar);
        
        // Set view-only mode to prevent drawing on the canvas
        drawingView.setViewOnlyMode(true);
        
        // Set drawer name and word
        drawerNameTextView.setText(drawerPlayer.getName());
        wordTextView.setText("Word: " + currentWord);
        
        // DEBUG - log the canvas state
        Log.d(TAG, "Setting up rating view for " + drawerPlayer.getName() + ", in round " + game.getCurrentRound());
        
        // Clear existing drawing content
        drawingView.clearCanvas();
        
        // Load the drawing from Firebase - using a more robust path
        String path = "drawings/" + lobbyId + "/round" + game.getCurrentRound() + "/player" + drawerPlayer.getId();
        Log.d(TAG, "Loading drawings from Firebase path: " + path);
        
        firebaseHandler.getDrawingForRound(lobbyId, game.getCurrentRound(), drawerPlayer.getId(), drawings -> {
            runOnUiThread(() -> {
                // Display the drawing in the DrawingView
                if (drawings != null && !drawings.isEmpty()) {
                    Log.d(TAG, "Loaded " + drawings.size() + " drawing actions for " + drawerPlayer.getName());
                    drawingView.loadDrawingActions(drawings);
                    drawingView.invalidate(); // Force redraw
                } else {
                    Log.e(TAG, "No drawings found for player: " + drawerPlayer.getName());
                }
            });
        });
        
        // Create an alert dialog to display the rating UI
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog ratingDialog = builder.setTitle("Rate Drawing " + (currentPlayerRatingIndex + 1) + "/" + playersToRate.size())
               .setView(ratingView)
               .setPositiveButton("Submit Rating", (dialog, which) -> {
                   // Submit the rating to Firebase
                   float rating = ratingBar.getRating();
                   gameManager.submitRating(drawerPlayer.getId(), rating);
                   
                   // Show toast confirmation
                   Toast.makeText(this, "Rating submitted for " + drawerPlayer.getName(), Toast.LENGTH_SHORT).show();
                   
                   // Move to the next player
                   currentPlayerRatingIndex++;
                   showNextPlayerDrawing(game);
               })
               .setCancelable(false)
               .create();
        
        ratingDialog.show();
        
        // Add a timer to auto-complete the rating after 15 seconds
        new Handler().postDelayed(() -> {
            if (ratingDialog.isShowing()) {
                // Auto-submit with a default rating if the user hasn't rated yet
                if (ratingBar.getRating() == 0) {
                    // Default 3-star rating
                    gameManager.submitRating(drawerPlayer.getId(), 3.0f);
                }
                ratingDialog.dismiss();
                
                // Move to the next player
                currentPlayerRatingIndex++;
                showNextPlayerDrawing(game);
            }
        }, 15000); // 15 seconds to rate
    }
    
    /**
     * Complete the rating phase and move to the next round or end the game
     */
    private void completeRatingAndMoveOn(Game game) {
        // If this is the last round, show final scores
        if (game.getCurrentRound() >= game.getTotalRounds()) {
            // Show final scores
            showFinalScores(game);
        } else {
            // Otherwise, move to the next round
            if (isHost) {
                gameManager.completeRatingPhase();
            }
        }
    }
    
    /**
     * Show the final scores screen
     */
    private void showFinalScores(Game game) {
        runOnUiThread(() -> {
            // Create a view for displaying final scores
            View scoresView = getLayoutInflater().inflate(R.layout.final_scores_dialog, null);
            
            // Find the ListView for displaying player scores
            ListView scoresListView = scoresView.findViewById(R.id.scores_list_view);
            
            // Create an adapter for displaying the scores
            List<Player> sortedPlayers = new ArrayList<>(game.getPlayers());
            Collections.sort(sortedPlayers, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
            
            // Create a simple adapter to display player names and scores
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
            
            // Add players to the adapter
            for (Player player : sortedPlayers) {
                adapter.add(player.getName() + ": " + player.getScore() + " points");
            }
            
            // Set the adapter to the ListView
            scoresListView.setAdapter(adapter);
            
            // Show the winner if available
            TextView winnerTextView = scoresView.findViewById(R.id.winner_text_view);
            if (game.getWinner() != null) {
                winnerTextView.setText("Winner: " + game.getWinner().getName());
            } else if (!sortedPlayers.isEmpty()) {
                winnerTextView.setText("Winner: " + sortedPlayers.get(0).getName());
            }
            
            // Display the final scores dialog
            new AlertDialog.Builder(this)
                    .setTitle("Final Scores")
                    .setView(scoresView)
                    .setPositiveButton("Return to Lobby", (dialog, which) -> {
                        // Return to the lobby screen
                        Intent intent = new Intent(this, SecondaryActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        });
    }
}

package com.example.drawit.views;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.drawit.R;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.Game;
import com.example.drawit.models.Player;
import com.example.drawit.game.models.DrawingAction;
// import com.example.drawit.utils.GameConstants; // Removed as the class doesn't exist

import java.util.List;
import java.util.Locale;

public class GameUI {
    private static final String TAG = "GameUI";
    
    // UI Components
    private final View rootView;
    private final DrawingView drawingView;
    private final TextView timerTextView;
    private final TextView wordToDrawTextView;
    private final TextView playerListTextView;
    private final View guessContainer;
    private final EditText guessInput;
    private final Button guessButton;
    private final View colorPickerContainer;
    private final View drawingToolsContainer;
    private final ProgressBar loadingIndicator;
    private final Button startGameButton;
    
    // State
    private final GameManager gameManager;
    private final String currentUserId;
    private final boolean isHost;
    
    public GameUI(View rootView, GameManager gameManager, String currentUserId, boolean isHost) {
        this.rootView = rootView;
        this.gameManager = gameManager;
        this.currentUserId = currentUserId;
        this.isHost = isHost;
        
        // Initialize views
        this.drawingView = rootView.findViewById(R.id.drawing_view);
        this.timerTextView = rootView.findViewById(R.id.timer_text_view);
        this.wordToDrawTextView = rootView.findViewById(R.id.word_to_draw_text_view);
        this.playerListTextView = rootView.findViewById(R.id.player_list_text_view);
        this.guessContainer = rootView.findViewById(R.id.guess_container);
        this.guessInput = rootView.findViewById(R.id.guess_input);
        this.guessButton = rootView.findViewById(R.id.guess_button);
        this.colorPickerContainer = rootView.findViewById(R.id.color_picker_container);
        this.drawingToolsContainer = rootView.findViewById(R.id.drawing_tools_container);
        this.loadingIndicator = rootView.findViewById(R.id.loading_indicator);
        this.startGameButton = rootView.findViewById(R.id.start_game_button);
        
        setupUI();
    }
    
    private void setupUI() {
        // Setup drawing tools
        setupDrawingTools();
        
        // Setup guess button
        if (guessButton != null && guessInput != null) {
            guessButton.setOnClickListener(v -> handleGuess());
        }
        
        // Setup start game button for host
        if (startGameButton != null) {
            startGameButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
            startGameButton.setOnClickListener(v -> startGame());
        }
    }
    
    private void setupDrawingTools() {
        // Setup color picker and drawing tools
            // Set up color picker
        int[] colorIds = {
                R.id.color_black, R.id.color_red, R.id.color_blue,
                R.id.color_green, R.id.color_yellow, R.id.color_purple
        };
        
        int[] colors = {
                Color.BLACK, Color.RED, Color.BLUE,
                Color.GREEN, Color.YELLOW, 0xFF800080 // Purple
        };
        
        for (int i = 0; i < colorIds.length; i++) {
            View colorView = rootView.findViewById(colorIds[i]);
            if (colorView != null) {
                final int color = colors[i];
                colorView.setOnClickListener(v -> {
                    if (drawingView != null) {
                        drawingView.setColor(color);
                    }
                });
            }
        }
        
        // Set up brush size buttons
        View smallBrushBtn = rootView.findViewById(R.id.small_brush_btn);
        View mediumBrushBtn = rootView.findViewById(R.id.medium_brush_btn);
        View largeBrushBtn = rootView.findViewById(R.id.large_brush_btn);
        
        if (smallBrushBtn != null) {
            smallBrushBtn.setOnClickListener(v -> {
                if (drawingView != null) drawingView.setBrushSize(8f);
            });
        }
        
        if (mediumBrushBtn != null) {
            mediumBrushBtn.setOnClickListener(v -> {
                if (drawingView != null) drawingView.setBrushSize(16f);
            });
        }
        
        if (largeBrushBtn != null) {
            largeBrushBtn.setOnClickListener(v -> {
                if (drawingView != null) drawingView.setBrushSize(24f);
            });
        }
        
        // Set up eraser and clear buttons
        View eraserBtn = rootView.findViewById(R.id.eraser_btn);
        View clearCanvasBtn = rootView.findViewById(R.id.clear_canvas_btn);
        
        if (eraserBtn != null) {
            eraserBtn.setOnClickListener(v -> {
                if (drawingView != null) drawingView.setEraseMode(true);
            });
        }
        
        if (clearCanvasBtn != null) {
            clearCanvasBtn.setOnClickListener(v -> {
                if (drawingView != null) drawingView.clearCanvas();
            });
        }
    }
    
    private void handleGuess() {
        if (guessInput == null || guessInput.getText() == null || gameManager == null) return;
        
        String guess = guessInput.getText().toString().trim();
        if (!guess.isEmpty()) {
            gameManager.handleGuess(currentUserId, guess);
            guessInput.setText("");
            
            // Disable input until next round
            if (guessInput != null) guessInput.setEnabled(false);
            if (guessButton != null) guessButton.setEnabled(false);
        }
    }
    
    private void startGame() {
        if (isHost && gameManager != null) {
            gameManager.startGame();
            if (startGameButton != null) {
                startGameButton.setEnabled(false);
            }
        }
    }
    
    public void updateGameState(GameStatus status) {
        if (rootView == null || gameManager == null) return;
        
        rootView.post(() -> {
            // Update UI based on game state
            switch (status) {
                case WAITING_FOR_PLAYERS:
                    showWaitingForPlayers();
                    break;
                case WAITING_FOR_START:
                    showWaitingForPlayers();
                    break;
                case STARTED:
                    showRoundStarting();
                    break;
                case WORD_SELECTION:
                    showRoundStarting();
                    break;
                case DRAWING:
                    showInProgress();
                    break;
                case GUESSING:
                    showInProgress();
                    break;
                case BETWEEN_ROUNDS:
                    showRoundEnding();
                    break;
                case ROUND_ENDED:
                    showRoundEnding();
                    break;
                case GAME_OVER:
                    showGameOver();
                    break;
                case ENDED:
                    showGameOver();
                    break;
                default:
                    Log.w(TAG, "Unknown game status: " + status);
            }
        });
    }
    
    public void updatePlayerList(List<Player> players) {
        if (playerListTextView == null || players == null) return;
        
        StringBuilder playerList = new StringBuilder();
        for (Player player : players) {
            String playerName = player.getUsername() != null ? player.getUsername() : "Player";
            playerList.append(String.format(Locale.getDefault(),
                "%s: %d points\n",
                playerName,
                player.getScore()
            ));
        }
        playerListTextView.setText(playerList.toString().trim());
    }
    
    public void updateTimer(long timeRemaining) {
        if (timerTextView == null) return;
        
        long minutes = timeRemaining / 60000;
        long seconds = (timeRemaining % 60000) / 1000;
        String time = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        timerTextView.setText(time);
        
        // Flash red when time is running low
        if (timeRemaining < 10000) { // Last 10 seconds
            timerTextView.setTextColor(Color.RED);
        } else {
            timerTextView.setTextColor(Color.WHITE);
        }
    }
    
    public void updateWordToDraw(String word) {
        if (wordToDrawTextView == null) return;
        
        if (word != null && !word.isEmpty()) {
            wordToDrawTextView.setText(String.format("Draw: %s", word));
            wordToDrawTextView.setVisibility(View.VISIBLE);
        } else {
            wordToDrawTextView.setVisibility(View.INVISIBLE);
        }
    }
    
    public void showMessage(String message) {
        if (rootView != null && message != null) {
            Toast.makeText(rootView.getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showWaitingForPlayers() {
        updateUIForState(false, false, "Waiting for players...");
    }
    
    private void showRoundStarting() {
        updateUIForState(false, false, "Round starting...");
    }
    
    private void showInProgress() {
        // Force display of all game UI elements
        if (drawingView != null) {
            drawingView.setEnabled(true);
            drawingView.setVisibility(View.VISIBLE);
        }
        
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.VISIBLE);
        }
        
        if (colorPickerContainer != null) {
            colorPickerContainer.setVisibility(View.VISIBLE);
        }
        
        if (timerTextView != null) {
            timerTextView.setVisibility(View.VISIBLE);
            timerTextView.setText("2:00");
        }
        
        // Set game status text in word display
        if (wordToDrawTextView != null) {
            wordToDrawTextView.setVisibility(View.VISIBLE);
            String currentWord = gameManager.getGame() != null && gameManager.getGame().getCurrentWord() != null ?
                    gameManager.getGame().getCurrentWord() : "Draw Something!";
            wordToDrawTextView.setText("Draw: " + currentWord);
        }
        
        // Remove loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.GONE);
        }
        
        Log.d(TAG, "Showing game in progress UI");
    }
    
    private void showRoundEnding() {
        updateUIForState(false, false, "Round over!");
    }
    
    private void showGameOver() {
        updateUIForState(false, false, "Game Over!");
        showMessage("Game Over!");
    }
    
    private void updateUIForState(boolean showDrawingTools, boolean isDrawer, String statusText) {
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(showDrawingTools ? View.VISIBLE : View.GONE);
        }
        
        if (colorPickerContainer != null) {
            colorPickerContainer.setVisibility(showDrawingTools ? View.VISIBLE : View.GONE);
        }
        
        if (guessContainer != null) {
            guessContainer.setVisibility(!showDrawingTools && !isDrawer ? View.VISIBLE : View.GONE);
        }
        
        if (wordToDrawTextView != null) {
            wordToDrawTextView.setVisibility(isDrawer ? View.VISIBLE : View.INVISIBLE);
        }
        
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.GONE);
        }
        
        // Enable/disable drawing view
        if (drawingView != null) {
            drawingView.setEnabled(showDrawingTools && isDrawer);
            if (!showDrawingTools) {
                drawingView.clearCanvas();
            }
        }
    }
    
    private void setViewVisibility(View view, int visibility) {
        if (view != null) {
            view.setVisibility(visibility);
        }
    }
    
    public void cleanup() {
        // Clean up any resources if needed
    }
    
    /**
     * Show round summary dialog between rounds
     * @param game The current game state
     * @param callback Callback to run when the summary is dismissed
     */
    public void showRoundSummary(Game game, Runnable callback) {
        if (rootView == null || game == null) return;
        
        // Create a dialog showing the round summary
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(rootView.getContext());
        builder.setTitle("Round " + game.getCurrentRound() + " Complete!");
        
        // Create a summary message showing the word and scores
        StringBuilder message = new StringBuilder();
        message.append("The word was: ").append(game.getCurrentWord()).append("\n\n");
        message.append("Current Scores:\n");
        
        // Add player scores
        for (Player player : game.getPlayers()) {
            message.append(player.getUsername()).append(": ").append(player.getScore()).append(" points\n");
        }
        
        builder.setMessage(message.toString());
        builder.setPositiveButton("Next Round", (dialog, which) -> {
            dialog.dismiss();
            if (callback != null) {
                callback.run();
            }
        });
        
        // Show the dialog
        builder.create().show();
    }
    
    /**
     * Show the rating phase UI
     */
    private void showRatingPhase() {
        // Hide drawing tools and show rating UI
        if (drawingView != null) {
            drawingView.setVisibility(View.VISIBLE);
            drawingView.setEnabled(false); // Disable drawing
        }
        
        // Hide drawing tools
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.GONE);
        }
        
        if (colorPickerContainer != null) {
            colorPickerContainer.setVisibility(View.GONE);
        }
        
        // Show a message
        if (wordToDrawTextView != null) {
            wordToDrawTextView.setVisibility(View.VISIBLE);
            wordToDrawTextView.setText("Rate this drawing!");
        }
        
        // Hide loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.GONE);
        }
    }
    
    /**
     * Process a drawing action received from another player
     * @param action The drawing action to process
     */
    public void processDrawingAction(DrawingAction action) {
        if (drawingView != null && action != null) {
            drawingView.drawFromAction(action);
        }
    }
}

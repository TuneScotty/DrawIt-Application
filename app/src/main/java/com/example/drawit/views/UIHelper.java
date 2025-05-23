package com.example.drawit.views;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.drawit.R;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.models.Player;
import com.example.drawit.views.DrawingView;

import java.util.List;
import java.util.Map;

public class UIHelper {
    private static final String TAG = "UIHelper";
    private Activity activity; // Removed final to allow initialization in different constructors
    
    public UIHelper(Activity activity) {
        this.activity = activity;
    }
    private TextView timerTextView;
    private TextView roundTextView;
    private TextView wordHintTextView;
    private TextView waitingMessageTextView;
    private TextView gameStatusTextView;
    private TextView playerListTextView;
    private View drawingToolsContainer;
    private EditText guessInput;
    private Button guessButton;
    private View guessContainer;
    private View gameOverScreen;
    private ImageButton star1Button;
    private ImageButton star2Button;
    private ImageButton star3Button;
    private TextView ratingInstructionText;
    private Button nextDrawingButton;
    private View ratingLayout;
    private View lobbyContainer;
    private TextView lobbyCodeTextView;
    private TextView lobbyPlayerCountTextView;
    private Button startGameButton;
    private Button leaveLobbyButton;
    private Button startNextRoundButton;
    private TextView playerScoresTextView;
    private TextView brushSizeIndicator;
    private View colorPickerContainer;
    private ProgressBar loadingSpinner;
    private DrawingView drawingView;

    /**
     * Full constructor with all UI components
     */
    public UIHelper(Activity activity, 
                   DrawingView drawingView,
                   TextView timerTextView,
                   TextView wordToDrawTextView,
                   TextView waitingMessageTextView,
                   TextView playerListTextView,
                   TextView gameStatusTextView,
                   View guessContainer,
                   EditText guessInput,
                   Button guessButton,
                   View colorPickerContainer,
                   View drawingToolsContainer,
                   Button startGameButton,
                   ProgressBar loadingSpinner) {
        
        this.activity = activity;
        this.drawingView = drawingView;
        this.timerTextView = timerTextView;
        this.wordHintTextView = wordToDrawTextView;
        this.waitingMessageTextView = waitingMessageTextView;
        this.playerListTextView = playerListTextView;
        this.gameStatusTextView = gameStatusTextView;
        this.guessContainer = guessContainer;
        this.guessInput = guessInput;
        this.guessButton = guessButton;
        this.colorPickerContainer = colorPickerContainer;
        this.drawingToolsContainer = drawingToolsContainer;
        this.startGameButton = startGameButton;
        this.loadingSpinner = loadingSpinner;
    }
    
    /**
     * Default constructor
     */
    public UIHelper() {
        // Default constructor for backward compatibility
        this.activity = null; // Initialize to null for compatibility
    }

    public void setupViews(Activity activity, TextView waitingMessage, TextView timer, TextView wordToDraw, TextView playerList, View guessContainer, EditText guessInput, Button guessButton, View colorPickerContainer, View drawingToolsContainer, ImageButton colorBlack, ImageButton colorRed, ImageButton colorGreen, ImageButton colorBlue, TextView brushSizeIndicator, ImageButton eraser, ImageButton clearCanvas) {
        this.waitingMessageTextView = waitingMessage;
        this.timerTextView = timer;
        this.wordHintTextView = wordToDraw;
        this.playerListTextView = playerList;
        this.gameStatusTextView = playerList;
        this.guessContainer = guessContainer;
        this.guessInput = guessInput;
        this.guessButton = guessButton;
        this.colorPickerContainer = colorPickerContainer;
        this.drawingToolsContainer = drawingToolsContainer;
        this.brushSizeIndicator = brushSizeIndicator;
    }

    public void setupDrawingTools(DrawingView drawingView, ImageButton colorBlack, ImageButton colorRed, ImageButton colorGreen, ImageButton colorBlue, ImageButton brushSmall, ImageButton brushMedium, ImageButton brushLarge, ImageButton eraser, ImageButton clearCanvas, TextView brushSizeIndicator) {
        this.drawingView = drawingView;
        colorBlack.setOnClickListener(v -> drawingView.setColor(android.graphics.Color.BLACK));
        colorRed.setOnClickListener(v -> drawingView.setColor(android.graphics.Color.RED));
        colorGreen.setOnClickListener(v -> drawingView.setColor(android.graphics.Color.GREEN));
        colorBlue.setOnClickListener(v -> drawingView.setColor(android.graphics.Color.BLUE));
        brushSmall.setOnClickListener(v -> { drawingView.setBrushSize(5); brushSizeIndicator.setText("Brush Size: 5"); });
        brushMedium.setOnClickListener(v -> { drawingView.setBrushSize(10); brushSizeIndicator.setText("Brush Size: 10"); });
        brushLarge.setOnClickListener(v -> { drawingView.setBrushSize(15); brushSizeIndicator.setText("Brush Size: 15"); });
        eraser.setOnClickListener(v -> {
            if (drawingView != null) {
                // Set eraser mode by using white color instead of a dedicated method
                drawingView.setColor(android.graphics.Color.WHITE);
            }
        });
        clearCanvas.setOnClickListener(v -> drawingView.clearCanvas());
        this.brushSizeIndicator.setText("Brush Size: 5");
    }

    public void updateTimer(long secondsRemaining) {
        if (timerTextView != null) {
            timerTextView.setText(String.format("Time: %d", secondsRemaining));
        }
    }

    public void updateRound(int currentRound, int totalRounds) {
        if (roundTextView != null) {
            roundTextView.setText(String.format("Round: %d/%d", currentRound, totalRounds));
        }
    }

    public void updateWordHint(String hint) {
        if (wordHintTextView != null) {
            wordHintTextView.setText(String.format("Word: %s", hint));
        }
    }

    public void showWaitingMessage(String message) {
        if (waitingMessageTextView != null) {
            waitingMessageTextView.setText(message);
            waitingMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    public void hideWaitingMessage() {
        if (waitingMessageTextView != null) {
            waitingMessageTextView.setVisibility(View.GONE);
        }
    }

    public void updateGameStatus(String status) {
        if (gameStatusTextView != null) {
            gameStatusTextView.setText(status);
        }
    }

    public void showDrawingTools() {
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.VISIBLE);
        }
    }

    public void hideDrawingTools() {
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.GONE);
        }
    }

    public void showDrawerTools() {
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Drawing tools container is null, cannot show drawer tools");
        }
        if (colorPickerContainer != null) {
            colorPickerContainer.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Color picker container is null, cannot show color picker");
        }
    }

    public void hideDrawerTools() {
        if (drawingToolsContainer != null) {
            drawingToolsContainer.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Drawing tools container is null, cannot hide drawer tools");
        }
        if (colorPickerContainer != null) {
            colorPickerContainer.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Color picker container is null, cannot hide color picker");
        }
    }

    public void enableGuessing() {
        if (guessContainer != null) {
            guessContainer.setVisibility(View.VISIBLE);
        }
        if (guessInput != null) {
            guessInput.setEnabled(true);
        }
        if (guessButton != null) {
            guessButton.setEnabled(true);
        }
    }

    public void disableGuessing() {
        if (guessInput != null) {
            guessInput.setEnabled(false);
        }
        if (guessButton != null) {
            guessButton.setEnabled(false);
        }
        if (guessContainer != null) {
            guessContainer.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Guess container is null, cannot disable guessing");
        }
    }

    public void showGuessingUI() {
        if (guessContainer != null) {
            guessContainer.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Guess container is null, cannot show guessing UI");
        }
        if (guessInput != null) {
            guessInput.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Guess input is null, cannot show guessing UI");
        }
        if (guessButton != null) {
            guessButton.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Guess button is null, cannot show guessing UI");
        }
    }

    public void hideGuessingUI() {
        if (guessContainer != null) {
            guessContainer.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Guess container is null, cannot hide guessing UI");
        }
        if (guessInput != null) {
            guessInput.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Guess input is null, cannot hide guessing UI");
        }
        if (guessButton != null) {
            guessButton.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Guess button is null, cannot hide guessing UI");
        }
    }

    public void showGameOverScreen() {
        if (gameOverScreen != null) {
            gameOverScreen.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Game over screen is null, cannot show game over screen");
        }
    }

    public void hideGameOverScreen() {
        if (gameOverScreen != null) {
            gameOverScreen.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Game over screen is null, cannot hide game over screen");
        }
    }

    public void showRatingUI() {
        if (ratingLayout != null) {
            ratingLayout.setVisibility(View.VISIBLE);
        }
        if (ratingInstructionText != null) {
            ratingInstructionText.setVisibility(View.VISIBLE);
        }
        if (star1Button != null) {
            star1Button.setVisibility(View.VISIBLE);
        }
        if (star2Button != null) {
            star2Button.setVisibility(View.VISIBLE);
        }
        if (star3Button != null) {
            star3Button.setVisibility(View.VISIBLE);
        }
    }

    public void hideRatingUI() {
        if (ratingLayout != null) {
            ratingLayout.setVisibility(View.GONE);
        }
        if (ratingInstructionText != null) {
            ratingInstructionText.setVisibility(View.GONE);
        }
        if (star1Button != null) {
            star1Button.setVisibility(View.GONE);
        }
        if (star2Button != null) {
            star2Button.setVisibility(View.GONE);
        }
        if (star3Button != null) {
            star3Button.setVisibility(View.GONE);
        }
        if (nextDrawingButton != null) {
            nextDrawingButton.setVisibility(View.GONE);
            nextDrawingButton.setEnabled(false);
        }
    }

    public void showLobbyUI(String lobbyId, int playerCount, boolean isHost) {
        // Show lobby UI elements
        if (lobbyContainer != null) {
            lobbyContainer.setVisibility(View.VISIBLE);
            if (lobbyCodeTextView != null) {
                lobbyCodeTextView.setText("Lobby Code: " + lobbyId);
            }
            if (lobbyPlayerCountTextView != null) {
                lobbyPlayerCountTextView.setText(playerCount + " players in lobby");
            }
            if (startGameButton != null) {
                startGameButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
            }
        } else {
            Log.w(TAG, "Lobby container is null, cannot show lobby UI");
        }
        
        // Hide game-specific UI elements
        hideDrawerTools();
        hideGuessingUI();
    }

    public void hideLobbyUI() {
        if (lobbyContainer != null) {
            lobbyContainer.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Lobby container is null, cannot hide lobby UI");
        }
    }

    public void showStartNextRoundButton() {
        if (startNextRoundButton != null) {
            startNextRoundButton.setVisibility(View.VISIBLE);
        } else {
            Log.w(TAG, "Start next round button is null, cannot show");
        }
    }

    public void hideStartNextRoundButton() {
        if (startNextRoundButton != null) {
            startNextRoundButton.setVisibility(View.GONE);
        } else {
            Log.w(TAG, "Start next round button is null, cannot hide");
        }
    }

    public void updatePlayerScores(String scores) {
        if (playerScoresTextView != null) {
            playerScoresTextView.setText(scores);
            playerScoresTextView.setVisibility(View.VISIBLE);
        }
    }

    public void hidePlayerScores() {
        if (playerScoresTextView != null) {
            playerScoresTextView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Update the drawing view with the provided drawing actions
     * @param drawingActions The list of drawing actions to apply
     */
    // Lobby UI methods
    public void initializeLobbyUI(boolean isHost, int playerCount) {
        if (lobbyContainer != null) {
            lobbyContainer.setVisibility(View.VISIBLE);
        }
        
        if (startGameButton != null) {
            startGameButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
        }
        
        if (lobbyPlayerCountTextView != null) {
            lobbyPlayerCountTextView.setText(String.format("Players: %d", playerCount));
        }
    }
    
    public void showStartGameButton() {
        if (startGameButton != null) {
            startGameButton.setVisibility(View.VISIBLE);
        }
    }
    
    public void hideStartGameButton() {
        if (startGameButton != null) {
            startGameButton.setVisibility(View.GONE);
        }
    }
    
    // Word choice dialog
    public void showWordChoiceDialog(Activity activity, List<String> wordChoices) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.e(TAG, "Activity is null or finishing, cannot show word choice dialog");
            return;
        }
        
        if (wordChoices == null || wordChoices.isEmpty()) {
            Log.w(TAG, "No word choices provided");
            return;
        }
        
        try {
            // Create and show a simple dialog with word choices
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Choose a word to draw");
            
            // Convert list to array for the dialog
            final String[] wordsArray = wordChoices.toArray(new String[0]);
            
            builder.setItems(wordsArray, (dialog, which) -> {
                // Handle word selection
                if (which >= 0 && which < wordsArray.length) {
                    String selectedWord = wordsArray[which];
                    if (wordSelectedListener != null) {
                        wordSelectedListener.onWordSelected(selectedWord);
                    }
                }
            });
            
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            
            // Check if activity is still valid before showing dialog
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                dialog.show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing word choice dialog", e);
        }
    }
    
    // Interface for word selection callback
    public interface OnWordSelectedListener {
        void onWordSelected(String word);
    }
    
    // Show rating interface for player drawings
    public void showRatingInterface(Map<String, List<com.example.drawit.game.models.DrawingAction>> playerDrawings, 
                                  List<Player> players) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.e(TAG, "Cannot show rating interface - activity is not valid");
            return;
        }
        
        // In a real implementation, you would show a dialog or view to rate the drawings
        // This is a placeholder implementation
        Log.d(TAG, "Showing rating interface for " + playerDrawings.size() + " drawings");
        
        // You would typically show a dialog here with the drawings and rating options
        // For now, we'll just log the event
        if (ratingCompleteListener != null) {
            ratingCompleteListener.onRatingComplete();
        }
    }
    
    // Interface for rating completion callback
    public interface OnRatingCompleteListener {
        void onRatingComplete();
    }
    
    private OnRatingCompleteListener ratingCompleteListener;
    
    public void setOnRatingCompleteListener(OnRatingCompleteListener listener) {
        this.ratingCompleteListener = listener;
    }
    
    private OnWordSelectedListener wordSelectedListener;
    
    public void setOnWordSelectedListener(OnWordSelectedListener listener) {
        this.wordSelectedListener = listener;
    }
    
    // Drawing view methods
    public void updateDrawingView(List<DrawingAction> drawingActions) {
        if (drawingView != null && drawingActions != null && !drawingActions.isEmpty()) {
            // Clear existing drawings
            drawingView.clearCanvas();
            
            // Apply all drawing actions
            for (DrawingAction action : drawingActions) {
                drawingView.drawFromAction(action);
            }
            
            // Refresh the view
            drawingView.invalidate();
        }
    }

    public void showLoadingSpinner() {
        if (loadingSpinner != null) {
            loadingSpinner.setVisibility(View.VISIBLE);
        }
    }

    public void hideLoadingSpinner() {
        if (loadingSpinner != null) {
            loadingSpinner.setVisibility(View.GONE);
        }
    }
}

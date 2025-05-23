package com.example.drawit.views;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.game.models.Game;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.models.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Locale;

public class UIUpdater {
    private static final String TAG = "UIUpdater";
    private final GameActivity activity;
    private final UIHelper uiHelper;
    private String lobbyId;
    private boolean isHost;
    private TextView waitingMessageTextView;
    private TextView timerTextView;
    private TextView wordToDrawTextView;
    private TextView playerListTextView;
    private TextView gameStatusTextView;
    private View guessContainer;
    private EditText guessInput;
    private Button guessButton;
    private View colorPickerContainer;
    private View drawingToolsContainer;
    private Button startGameButton;
    private ProgressBar loadingIndicator;

    public UIUpdater(GameActivity activity, String lobbyId, boolean isHost, TextView waitingMessageTextView, TextView timerTextView, TextView wordToDrawTextView, TextView playerListTextView, TextView gameStatusTextView, View guessContainer, EditText guessInput, Button guessButton, View colorPickerContainer, View drawingToolsContainer, Button startGameButton, ProgressBar loadingIndicator) {
        this.activity = activity;
        this.uiHelper = new UIHelper(activity);
        this.lobbyId = lobbyId;
        this.isHost = isHost;
        this.waitingMessageTextView = waitingMessageTextView;
        this.timerTextView = timerTextView;
        this.wordToDrawTextView = wordToDrawTextView;
        this.playerListTextView = playerListTextView;
        this.gameStatusTextView = gameStatusTextView;
        this.guessContainer = guessContainer;
        this.guessInput = guessInput;
        this.guessButton = guessButton;
        this.colorPickerContainer = colorPickerContainer;
        this.drawingToolsContainer = drawingToolsContainer;
        this.startGameButton = startGameButton;
        this.loadingIndicator = loadingIndicator;
    }

    public void initializeUI(boolean isHost, List<Player> lobbyPlayers, String lobbyId) {
        this.isHost = isHost;
        this.lobbyId = lobbyId;
        updatePlayerList(lobbyPlayers);
         uiHelper.initializeLobbyUI(isHost, lobbyPlayers.size()); // Method not found in UIHelper
        updatePlayerList(lobbyPlayers); // Just update player list directly
    }

    public void updateGameState(String state, boolean isActive) {
        gameStatusTextView.setText(state);
        if (isActive) {
            loadingIndicator.setVisibility(View.GONE);
        } else {
            loadingIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void updatePlayerList(List<Player> players) {
        StringBuilder playerText = new StringBuilder();
        if (players != null) {
            for (Player player : players) {
                String displayName = (player.getUsername() != null && !player.getUsername().isEmpty()) ? player.getUsername() : ("Player " + (player.getId() != null && player.getId().length() >= 4 ? player.getId().substring(0, 4) : "?"));
                playerText.append(displayName);
                if (player.isDrawing()) playerText.append(" (Drawing)");
                if (player.isHasGuessedCorrectly()) playerText.append(" (Guessed)");
                playerText.append(" - Score: ").append(player.getScore());
                playerText.append("\n");
            }
        }
        playerListTextView.setText(playerText.toString());
    }

    public void updateUIOnGameStart(Game game) {
        Log.d(TAG, "Updating UI on game start, game status: " + game.getStatus());
        uiHelper.hideLobbyUI();
        uiHelper.hideWaitingMessage();
        updateUI(game);
    }

    public void updateUIOnRoundStart(Game game) {
        Log.d(TAG, "Updating UI on round start, round: " + game.getCurrentRound() + ", status: " + game.getStatus());
        uiHelper.hideStartNextRoundButton();
        uiHelper.hideWaitingMessage();
        updateUI(game);
    }

    public void updateUIOnDrawingFinished(Game game) {
        Log.d(TAG, "Updating UI on drawing finished");
        game.setStatus(GameStatus.GUESSING);
        updateUI(game);
    }

    public void updateUIOnTimeUp(Game game) {
        Log.d(TAG, "Updating UI on time up");
        game.setStatus(GameStatus.BETWEEN_ROUNDS);
        updateUI(game);
        if (isHost) {
            uiHelper.showStartNextRoundButton();
        }
    }

    public void updateUI(Game game) {
        if (game == null) {
            Log.e(TAG, "Game object is null in updateUI");
            return;
        }

        uiHelper.updateRound(game.getCurrentRound(), game.getTotalRounds());

        GameStatus status = game.getStatus();
        if (status == null) {
            Log.e(TAG, "Game status is null");
            return;
        }

        switch (status) {
            case WAITING_FOR_PLAYERS:
                updateWaitingForPlayersUI(game);
                break;
            case STARTED: // New state, can lead to word selection
                Log.d(TAG, "Game Status: STARTED, preparing for word selection.");
                // Fall through to WORD_SELECTION or add specific UI
            case WORD_SELECTION: // Mapped from ROUND_STARTING
                updateWordSelectionUI(game);
                break;
            case DRAWING:      // Mapped from IN_PROGRESS
                updateDrawingUI(game);
                break;
            case GUESSING:     // Explicit state for guessing time
                updateGuessingUI(game);
                break;
            case ROUND_ENDED:  // Mapped from ROUND_ENDING
            case BETWEEN_ROUNDS: // Grouping these two for similar UI
                updateRoundEndedUI(game);
                break;
            case RATING:       // Explicit state for rating drawings
                updateRatingUI(game);
                break;
            case GAME_OVER:
                updateGameOverUI(game);
                break;
            default:
                Log.w(TAG, "Unknown or unhandled game status: " + status);
                uiHelper.showWaitingMessage("Game is in an undefined state: " + status);
                break;
        }

        updateCommonUI(game);
    }

    private void updateWaitingForPlayersUI(Game game) {
        Log.d(TAG, "Updating UI for WAITING_FOR_PLAYERS state");

        try {
            uiHelper.showWaitingMessage("Waiting for players to join...");
            uiHelper.hideDrawerTools();
            uiHelper.disableGuessing();

            // Show start button only for host
            if (isHost) {
                 uiHelper.showStartGameButton(); // Method not found in UIHelper
            } else {
                 uiHelper.hideStartGameButton(); // Method not found in UIHelper
            }

            // Update player list
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating WAITING_FOR_PLAYERS UI", e);
        }
    }

    private void updateWordSelectionUI(Game game) { // Renamed from updateRoundStartingUI
        Log.d(TAG, "Updating UI for WORD_SELECTION state");
            updateTimer(game.getWordSelectionTimeRemainingSeconds(System.currentTimeMillis()));

        try {
            uiHelper.showWaitingMessage("Starting round...");
            uiHelper.hideDrawerTools();
            uiHelper.disableGuessing();

            // Update player list
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating WORD_SELECTION UI", e);
        }
    }

    private void updateDrawingUI(Game game) { // Renamed from updateInProgressUI
        Log.d(TAG, "Updating UI for DRAWING state");

        try {
            uiHelper.hideWaitingMessage();

            String currentUserId = activity != null ? activity.getCurrentUserId() : "";
            Player drawer = game.getCurrentDrawer();
            boolean isDrawing = drawer != null && currentUserId != null && currentUserId.equals(drawer.getId());

            Log.d(TAG, "Current user ID: " + currentUserId + ", isDrawing: " + isDrawing);

            // Show appropriate UI based on whether current player is drawing or guessing
            if (isDrawing) {
                Log.d(TAG, "Showing drawer tools for drawer");
                uiHelper.showDrawerTools();
                uiHelper.disableGuessing();
                if (wordToDrawTextView != null) {
                    wordToDrawTextView.setText("Your word: " + game.getCurrentWord());
                }
                // Show word choices if current player is the drawer and status is WORD_SELECTION (handled in updateWordSelectionUI)
                if (isDrawing && game.getStatus() == GameStatus.WORD_SELECTION && game.getWordChoices() != null && !game.getWordChoices().isEmpty()) {
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                        uiHelper.showWordChoiceDialog(activity, game.getWordChoices());
                    } else {
                        Log.e(TAG, "Cannot show word choice dialog - activity is not valid");
                    }
                }
            } else {
                Log.d(TAG, "Enabling guessing for non-drawer");
                uiHelper.hideDrawerTools();
                uiHelper.enableGuessing();
                if (wordToDrawTextView != null) {
                    wordToDrawTextView.setText("Guess: " + getHintForWord(game.getCurrentWord()));
                }
            }

            // Update timer
            updateTimer(game.getRoundTimeRemainingSeconds(System.currentTimeMillis()));

            // Update player list to show who's drawing
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating DRAWING UI", e);
        }
    }

    private void updateRoundEndedUI(Game game) { // Renamed from updateRoundEndingUI
        Log.d(TAG, "Updating UI for ROUND_ENDED / BETWEEN_ROUNDS state");
            // Timer usually not active here, or shows time until next round if applicable.

        try {
            uiHelper.showWaitingMessage("Round ended...");
            uiHelper.hideDrawerTools();
            uiHelper.disableGuessing();

            // Update player list
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating ROUND_ENDED UI", e);
        }
    }

    private void updateGameOverUI(Game game) {
        Log.d(TAG, "Updating UI for GAME_OVER state");

        try {
            uiHelper.showWaitingMessage("Game over!");
            uiHelper.hideDrawerTools();
            uiHelper.disableGuessing();

            // Update player list
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating GAME_OVER UI", e);
        }
    }

    private void updateCommonUI(Game game) {
        // Stubs for new UI methods called in the switch
        // These will be filled or adapted from existing logic
    }

    private void updateGuessingUI(Game game) {
        Log.d(TAG, "Updating UI for GUESSING state");
        uiHelper.hideWaitingMessage();
        String currentUserId = activity.getCurrentUserId();
        Player drawer = game.getCurrentDrawer();
        boolean isCurrentUserDrawing = drawer != null && currentUserId != null && currentUserId.equals(drawer.getId());

        if (isCurrentUserDrawing) {
            uiHelper.showDrawerTools(); // Drawer might see their drawing, but cannot draw more
            uiHelper.disableGuessing();
            if (wordToDrawTextView != null) {
                wordToDrawTextView.setText("Word was: " + game.getCurrentWord());
            }
        } else {
            uiHelper.hideDrawerTools();
            uiHelper.enableGuessing();
            if (wordToDrawTextView != null) {
                wordToDrawTextView.setText("Guess: " + getHintForWord(game.getCurrentWord()));
            }
        }
        updateTimer(game.getRoundTimeRemainingSeconds(System.currentTimeMillis())); 
        updatePlayerList(game.getPlayers());
        // Update drawing view with current actions
        if (game.getDrawingActions() != null && !game.getDrawingActions().isEmpty()) {
            // We don't have direct access to the drawing view, so we'll update through the UI helper
            uiHelper.updateDrawingView(game.getDrawingActions());
        }
    }

    private void updateRatingUI(Game game) {
        Log.d(TAG, "Updating UI for RATING state");
        uiHelper.showWaitingMessage("Rating drawings...");
        uiHelper.hideDrawerTools();
        uiHelper.disableGuessing();
        if (wordToDrawTextView != null) {
            wordToDrawTextView.setText("Rate the drawings!");
        }
        if (game.isRatingPhase() && game.getRatingPhaseEndTime() > 0) {
            long timeRemainingMillis = game.getRatingPhaseEndTime() - System.currentTimeMillis();
            updateTimer(Math.max(0, timeRemainingMillis / 1000));
        } else {
            // Potentially hide timer or show static message
            updateTimer(0);
        }
        updatePlayerList(game.getPlayers());
        // TODO: Implement UI for showing drawings and collecting ratings
         // Show rating UI - implement this in UIHelper if needed
        if (game.getPlayerDrawings() != null && !game.getPlayerDrawings().isEmpty()) {
            uiHelper.showRatingInterface(game.getPlayerDrawings(), game.getPlayers());
            // For now, just show a message
            uiHelper.showWaitingMessage("Rate the drawings for this round!");
        }
    }

    // Original updateCommonUI content moved here, and timer call removed.
    private void updateCommonGameElements(Game game) {
        try {
            // Timer updates are now handled in status-specific methods.

            // Update round info
            if (gameStatusTextView != null) {
                String statusText = "Round " + game.getCurrentRound() + " of " + game.getTotalRounds();
                gameStatusTextView.setText(statusText);
            }

            // Update player list
            updatePlayerList(game.getPlayers());

        } catch (Exception e) {
            Log.e(TAG, "Error updating common UI", e);
        }
    }

    private void updateTimer(long timeRemaining) {
        if (timerTextView != null) {
            timerTextView.setText(String.valueOf(timeRemaining));
        }
    }

    private void updateScores(List<Player> players) {
        try {
            if (players == null || players.isEmpty()) {
                Log.w(TAG, "No players to update scores for");
                return;
            }

            Log.d(TAG, "Updating scores for " + players.size() + " players");

            // Sort players by score (descending)
            List<Player> sortedPlayers = new ArrayList<>(players);
            sortedPlayers.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

            // Build score text
            StringBuilder scoreText = new StringBuilder("Scores:\n");
            for (int i = 0; i < Math.min(5, sortedPlayers.size()); i++) {
                Player player = sortedPlayers.get(i);
                String playerName = (player.getUsername() != null && !player.getUsername().isEmpty()) ? player.getUsername() : "Player";
                scoreText.append(String.format("%d. %s: %d\n", i + 1, playerName, player.getScore()));
            }

            // Update UI
            if (playerListTextView != null) {
                playerListTextView.setText(scoreText.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating scores", e);
        }
    }

    private String getHintForWord(String word) {
        if (word == null || word.isEmpty()) {
            return "????";
        }
        StringBuilder hint = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                hint.append("?");
            } else {
                hint.append(word.charAt(i));
            }
        }
        return hint.toString();
    }

    public void showLoadingState() {
        uiHelper.showLoadingSpinner();
    }

    public void hideLoadingState() {
        uiHelper.hideLoadingSpinner();
    }

    public void updateUIOnLobbyUpdate(String lobbyId, int playerCount) {
        uiHelper.showLobbyUI(lobbyId, playerCount, isHost);
    }

    public void startNextRound(Game game) {
        if (game != null) {
            game.setStatus(GameStatus.GUESSING);
            updateUI(game);
        }
    }

    public void showRoundSummary(Game game) {
        if (game != null) {
            game.setStatus(GameStatus.BETWEEN_ROUNDS);
            updateUI(game);
        }
    }
}

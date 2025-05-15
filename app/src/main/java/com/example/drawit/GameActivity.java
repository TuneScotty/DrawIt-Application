package com.example.drawit;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.R;
import com.example.drawit.game.DefaultWordProvider;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.WordProvider;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.game.models.Game;
import com.example.drawit.game.models.GameStatus;
import com.example.drawit.game.models.Guess;
import com.example.drawit.models.Player; // Using the unified Player model
import com.example.drawit.views.DrawingView;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameActivity extends AppCompatActivity implements GameManager.GameEventListener {
    private static final String TAG = "GameActivity";
    
    // UI Components
    private TextView wordToDrawText;
    private TextView timerText;
    private TextView roundInfoText;
    private TextView scoreText;
    private DrawingView drawingView;
    private EditText guessInput;
    private Button submitGuessButton;
    private View drawingToolsContainer;
    private View guessingContainer;
    
    // Rating UI Components
    private View ratingContainer;
    private ImageButton star1Button;
    private ImageButton star2Button;
    private ImageButton star3Button;
    private TextView ratingInstructionText;
    private Button nextDrawingButton;
    private int currentRating = 0;
    private int currentViewingDrawingIndex = 0;
    private List<String> playerIdsToRate = new ArrayList<>();
    
    // Game Components
    private String lobbyId;
    private String userId;
    private FirebaseHandler firebaseHandler;
    private GameManager gameManager;
    private WordProvider wordProvider;
    private CountDownTimer roundTimer;
    private boolean isDrawer = false;
    private boolean gameStartAttempted = false; // Flag to prevent multiple game start attempts
    
    // Constants
    private static final long ROUND_DURATION = 60000; // 60 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        lobbyId = getIntent().getStringExtra("LOBBY_ID");
        firebaseHandler = FirebaseHandler.getInstance();
        userId = firebaseHandler.getCurrentUser().getUid();
        
        // Initialize game components
        wordProvider = new DefaultWordProvider();
        gameManager = new GameManager(wordProvider);
        gameManager.setEventListener(this);

        setupViews();
        setupDrawingTools();
        setupGuessInput();
        
        // Initialize the game with the current player first
        initializeGame();
        
        // Then set up listeners
        setupGameListeners();
        
        // Show initial UI
        showWaitingUI();
        
        // If we're the host, force start the game after a short delay
        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);
        if (isHost) {
            new Handler().postDelayed(() -> {
                forceStartGame();
            }, 1000); // 1 second delay to ensure all initialization is complete
        }
    }

    // Reference to the loading indicator
    private ProgressBar loadingIndicator;
    
    private void setupViews() {
        // Text views
        wordToDrawText = findViewById(R.id.wordToDrawText);
        timerText = findViewById(R.id.timerText);
        roundInfoText = findViewById(R.id.roundInfoText);
        scoreText = findViewById(R.id.scoreText);
        
        // Drawing components
        drawingView = findViewById(R.id.drawingCanvas);
        drawingToolsContainer = findViewById(R.id.drawingToolsContainer);
        
        // Guessing components
        guessInput = findViewById(R.id.guessInput);
        submitGuessButton = findViewById(R.id.submitGuessButton);
        guessingContainer = findViewById(R.id.guessingContainer);
        
        // Rating components
        ratingContainer = findViewById(R.id.ratingContainer);
        star1Button = findViewById(R.id.star1Button);
        star2Button = findViewById(R.id.star2Button);
        star3Button = findViewById(R.id.star3Button);
        ratingInstructionText = findViewById(R.id.ratingInstructionText);
        nextDrawingButton = findViewById(R.id.nextDrawingButton);
        
        // Setup rating buttons
        if (star1Button != null) {
            star1Button.setOnClickListener(v -> setRating(1));
        }
        if (star2Button != null) {
            star2Button.setOnClickListener(v -> setRating(2));
        }
        if (star3Button != null) {
            star3Button.setOnClickListener(v -> setRating(3));
        }
        if (nextDrawingButton != null) {
            nextDrawingButton.setOnClickListener(v -> showNextDrawing());
        }
        
        // Create a loading indicator dynamically
        loadingIndicator = new ProgressBar(this);
        loadingIndicator.setIndeterminate(true);
        
        // Add to layout
        ViewGroup rootLayout = findViewById(android.R.id.content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        rootLayout.addView(loadingIndicator, params);
        
        // Hide all components initially
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.GONE);
        if (ratingContainer != null) {
            ratingContainer.setVisibility(View.GONE);
        }
    }
    
    private void setRating(int rating) {
        currentRating = rating;
        highlightRatingStars(rating);
        
        // Enable the next button once a rating is selected
        if (nextDrawingButton != null) {
            nextDrawingButton.setEnabled(true);
        }
        
        // Save the rating
        if (currentViewingDrawingIndex < playerIdsToRate.size()) {
            String ratedPlayerId = playerIdsToRate.get(currentViewingDrawingIndex);
            Game game = gameManager.getGame();
            if (game != null) {
                game.addRating(ratedPlayerId, userId, rating);
                // Update in Firebase
                firebaseHandler.updateGameRating(lobbyId, ratedPlayerId, userId, rating);
            }
        }
    }
    
    private void highlightRatingStars(int rating) {
        if (star1Button != null) {
            star1Button.setSelected(rating >= 1);
        }
        if (star2Button != null) {
            star2Button.setSelected(rating >= 2);
        }
        if (star3Button != null) {
            star3Button.setSelected(rating >= 3);
        }
    }
    
    private void showNextDrawing() {
        currentViewingDrawingIndex++;
        currentRating = 0;
        highlightRatingStars(0);
        
        if (nextDrawingButton != null) {
            nextDrawingButton.setEnabled(false);
        }
        
        if (currentViewingDrawingIndex < playerIdsToRate.size()) {
            // Show the next player's drawing
            showPlayerDrawing(playerIdsToRate.get(currentViewingDrawingIndex));
        } else {
            // All drawings rated, move to results
            Game game = gameManager.getGame();
            if (game != null) {
                showRoundEndedUI(game);
                
                // If we're the host, advance to the next round or end the game
                Player currentPlayer = game.findPlayerById(userId);
                if (currentPlayer != null && currentPlayer.isHost()) {
                    if (game.getCurrentRound() < game.getTotalRounds()) {
                        // Start next round after a delay
                        new Handler().postDelayed(() -> {
                            startRound();
                        }, 5000); // 5 second delay to show results
                    } else {
                        // End the game
                        gameManager.endGame("All rounds completed");
                    }
                }
            }
        }
    }
    
    private void showPlayerDrawing(String playerId) {
        Game game = gameManager.getGame();
        if (game == null) return;
        
        // Get the player's drawing
        List<DrawingAction> playerDrawing = game.getPlayerDrawing(playerId);
        if (playerDrawing == null) {
            // No drawing available
            return;
        }
        
        // Clear the canvas and replay the drawing
        drawingView.clearCanvas();
        for (DrawingAction action : playerDrawing) {
            drawingView.drawFromAction(action);
        }
        
        // Update the instruction text
        Player player = game.findPlayerById(playerId);
        if (player != null && ratingInstructionText != null) {
            ratingInstructionText.setText("Rate " + player.getUsername() + "'s drawing:");
        }
    }

    private void setupDrawingTools() {
        // Set up drawing view to send actions to other players
        drawingView.setOnDrawingActionListener(action -> {
            if (isDrawer) {
                // Only send drawing actions if this player is the drawer
                action.setDrawerId(userId); // Set the drawer ID
                firebaseHandler.sendDrawingAction(lobbyId, action);
            }
        });
        
        // Brush size buttons
        findViewById(R.id.smallBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(12f));
        findViewById(R.id.mediumBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(24f));
        findViewById(R.id.largeBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(36f));
        
        // Eraser and clear canvas buttons
        findViewById(R.id.eraserBtn).setOnClickListener(v -> drawingView.setEraser());
        findViewById(R.id.clearCanvasBtn).setOnClickListener(v -> {
            // Only allow the drawer to clear the canvas
            if (!isDrawer) {
                Toast.makeText(this, "Only the drawer can clear the canvas", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Show confirmation dialog before clearing
            new AlertDialog.Builder(this)
                .setTitle("Clear Canvas")
                .setMessage("Are you sure you want to clear the canvas?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    drawingView.clearCanvas();
                    // Send a clear action to other players
                    DrawingAction clearAction = new DrawingAction(DrawingAction.ACTION_CLEAR, userId);
                    firebaseHandler.sendDrawingAction(lobbyId, clearAction);
                })
                .setNegativeButton("No", null)
                .show();
        });
        
        // Color buttons with visual feedback
        findViewById(R.id.blackColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.BLACK);
            highlightSelectedColor(v);
        });
        findViewById(R.id.redColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.RED);
            highlightSelectedColor(v);
        });
        findViewById(R.id.blueColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.BLUE);
            highlightSelectedColor(v);
        });
        findViewById(R.id.greenColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.GREEN);
            highlightSelectedColor(v);
        });
        
        // Additional color buttons
        findViewById(R.id.yellowColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.YELLOW);
            highlightSelectedColor(v);
        });
        findViewById(R.id.purpleColorBtn).setOnClickListener(v -> {
            drawingView.setColor(Color.parseColor("#800080"));
            highlightSelectedColor(v);
        });
    }
    
    // Track the currently selected color button
    private View currentlySelectedColorButton = null;
    
    /**
     * Highlight the selected color button and remove highlight from previously selected button
     * @param selectedButton The button that was selected
     */
    private void highlightSelectedColor(View selectedButton) {
        // Only allow the drawer to change colors
        if (!isDrawer) return;
        
        // Remove highlight from previous button
        if (currentlySelectedColorButton != null) {
            currentlySelectedColorButton.setScaleX(1.0f);
            currentlySelectedColorButton.setScaleY(1.0f);
            currentlySelectedColorButton.setElevation(0f);
        }
        
        // Highlight new button
        selectedButton.setScaleX(1.2f);
        selectedButton.setScaleY(1.2f);
        selectedButton.setElevation(8f);
        
        // Update current selection
        currentlySelectedColorButton = selectedButton;
    }

    private void setupGuessInput() {
        submitGuessButton.setOnClickListener(v -> {
            String guessText = guessInput.getText().toString().trim();
            if (!guessText.isEmpty()) {
                submitGuess(guessText);
                guessInput.setText(""); // Clear the input field
            }
        });
    }
    
    private void setupGameListeners() {
        // Make sure Firebase is initialized
        if (firebaseHandler == null || lobbyId == null) {
            Log.e(TAG, "Cannot setup game listeners: Firebase or lobbyId not initialized");
            return;
        }
        
        // Listen for game state changes
        firebaseHandler.listenForGameUpdates(lobbyId, new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Game data updated: " + dataSnapshot.toString());
                
                // If we're still waiting and the host has marked the game as started, force start
                if (!gameStartAttempted && dataSnapshot.exists()) {
                    // Check if the game is marked as started in Firebase
                    if (dataSnapshot.hasChild("status")) {
                        String status = dataSnapshot.child("status").getValue(String.class);
                        if ("STARTED".equals(status)) {
                            Log.d(TAG, "Game marked as started in Firebase, starting local game");
                            forceStartGame();
                        }
                    }
                }
                
                // If we have a full Game object, update the UI
                try {
                    Game game = dataSnapshot.getValue(Game.class);
                    if (game != null) {
                        Log.d(TAG, "Received game state: " + game.getStatus());
                        gameManager.setGame(game);
                        updateUI(game);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing game data", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Game data sync failed", databaseError.toException());
            }
        });
        
        // Listen for drawing actions
        firebaseHandler.listenForDrawingActions(lobbyId, new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                DrawingAction action = dataSnapshot.getValue(DrawingAction.class);
                if (action != null && !isDrawer) {
                    processDrawingAction(action);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
        
        // Listen for guesses
        firebaseHandler.listenForGuesses(lobbyId, new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                Guess guess = dataSnapshot.getValue(Guess.class);
                if (guess != null) {
                    gameManager.handleGuess(guess.getPlayerId(), guess.getGuessText());
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
        
        // Listen for game end
        firebaseHandler.addInGameLobbyListener(lobbyId, gameStarted -> {
            if (!gameStarted) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Game ended", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
    
    private void initializeGame() {
        // Create a player object for the current user
        String displayName = firebaseHandler.getCurrentUser().getDisplayName();
        if (displayName == null) {
            displayName = "Player"; // Fallback name if display name is null
        }
        Player currentPlayer = new Player(userId, displayName);
        
        // Set host status based on intent
        boolean isHost = getIntent().getBooleanExtra("IS_HOST", false);
        currentPlayer.setHost(isHost);
        
        // Initialize the game with the current player
        gameManager.addPlayer(userId, currentPlayer.getName());
        
        // Initialize players from lobby immediately
        initializePlayersFromLobby();
        
        Log.d(TAG, "Game initialized with current player: " + currentPlayer.getUsername() + ", isHost: " + isHost);
    }
    
    /**
     * Initialize players from the lobby data to ensure all players are in the game
     */
    private void initializePlayersFromLobby() {
        firebaseHandler.getLobbiesRef().child(lobbyId).child("players").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                for (DataSnapshot playerSnapshot : snapshot.getChildren()) {
                    String playerId = playerSnapshot.getKey();
                    if (playerId != null && !playerId.equals(userId)) { // Skip current user as they're already added
                        // Get player name if available
                        String playerName = "Player";
                        if (playerSnapshot.hasChild("username")) {
                            playerName = playerSnapshot.child("username").getValue(String.class);
                        }
                        
                        // Add player to game
                        if (playerName != null) {
                            gameManager.addPlayer(playerId, playerName);
                            Log.d(TAG, "Added player from lobby: " + playerId + " (" + playerName + ")");
                        }
                    }
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get lobby players", e);
        });
    }

    private void updateUI(Game game) {
        if (game == null) return;
        
        runOnUiThread(() -> {
            // Hide loading indicator
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.GONE);
            }
            
            // Update round info
            roundInfoText.setText(String.format("Round %d/%d", game.getCurrentRound(), game.getTotalRounds()));
            
            // Update score
            Player currentPlayer = game.findPlayerById(userId);
            if (currentPlayer != null) {
                scoreText.setText(String.format("Score: %d", currentPlayer.getScore()));
            }
            
            Log.d(TAG, "Updating UI for game status: " + game.getStatus());
            
            // Update UI based on game status
            switch (game.getStatus()) {
                case WAITING:
                    showWaitingUI();
                    break;
                case WORD_SELECTION:
                    // In the new gameplay, everyone gets the same word
                    // Host selects the word or we auto-select after a timeout
                    if (currentPlayer != null && currentPlayer.isHost()) {
                        showWordSelectionUI(game.getWordChoices());
                    } else {
                        showWaitingForWordSelectionUI();
                    }
                    break;
                case DRAWING:
                    // In the new gameplay, everyone draws the same word
                    showDrawingUI(game.getCurrentWord());
                    break;
                case RATING:
                    // Show the rating UI
                    showRatingUI(game);
                    break;
                case ROUND_ENDED:
                    showRoundEndedUI(game);
                    break;
                case ENDED:
                    showGameEndedUI(game);
                    break;
                default:
                    // If status is null or unknown, force start the game
                    if (!gameStartAttempted) {
                        forceStartGame();
                    }
                    break;
            }
        });
    }
    
    private boolean isCurrentPlayerDrawer(Game game) {
        Player drawer = game.getCurrentDrawer();
        isDrawer = drawer != null && userId.equals(drawer.getId());
        return isDrawer;
    }
    
    // Track if we've already shown the waiting message to avoid duplicates
    private boolean waitingMessageShown = false;
    
    private void showWaitingUI() {
        runOnUiThread(() -> {
            drawingToolsContainer.setVisibility(View.GONE);
            guessingContainer.setVisibility(View.GONE);
            wordToDrawText.setVisibility(View.GONE);
            timerText.setVisibility(View.GONE);
            
            // Show a loading indicator
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.VISIBLE);
            }
            
            // Only show the toast message once to avoid duplicates
            if (!waitingMessageShown) {
                waitingMessageShown = true;
                Toast.makeText(this, "Waiting for game to start...", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showWordSelectionUI(List<String> wordChoices) {
        // This would typically show a dialog with word choices
        // For simplicity, we'll just use the first word
        if (wordChoices != null && !wordChoices.isEmpty()) {
            gameManager.selectWord(userId, wordChoices.get(0));
        }
    }
    
    // Track if we've already shown the word selection waiting message
    private boolean wordSelectionMessageShown = false;
    
    private void showWaitingForWordSelectionUI() {
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.GONE);
        timerText.setVisibility(View.VISIBLE);
        
        // Only show the toast message once per word selection phase
        if (!wordSelectionMessageShown) {
            wordSelectionMessageShown = true;
            Toast.makeText(this, "Waiting for drawer to select a word...", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDrawingUI(String word) {
        drawingToolsContainer.setVisibility(View.VISIBLE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.VISIBLE);
        timerText.setVisibility(View.VISIBLE);
        if (ratingContainer != null) {
            ratingContainer.setVisibility(View.GONE);
        }
        
        wordToDrawText.setText("Draw: " + word);
        drawingView.setEnabled(true);
        startTimer();
        
        // In the new gameplay, we save the drawing actions to the player's own drawing list
        drawingView.setOnDrawingActionListener(action -> {
            Game game = gameManager.getGame();
            if (game != null) {
                // Add to the player's own drawing list
                game.addPlayerDrawingAction(userId, action);
                
                // Sync with Firebase
                firebaseHandler.addPlayerDrawingAction(lobbyId, userId, action);
            }
        });
    }
    
    private void showRatingUI(Game game) {
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.VISIBLE);
        timerText.setVisibility(View.VISIBLE);
        if (ratingContainer != null) {
            ratingContainer.setVisibility(View.VISIBLE);
        }
        
        wordToDrawText.setText("Rate the drawings of: " + game.getCurrentWord());
        drawingView.setEnabled(false);
        
        // Reset rating state
        currentViewingDrawingIndex = 0;
        currentRating = 0;
        highlightRatingStars(0);
        
        // Prepare the list of players to rate (excluding self)
        playerIdsToRate.clear();
        for (Player player : game.getPlayers()) {
            if (!player.getId().equals(userId)) {
                playerIdsToRate.add(player.getId());
            }
        }
        
        // Show the first player's drawing if available
        if (!playerIdsToRate.isEmpty()) {
            showPlayerDrawing(playerIdsToRate.get(0));
            if (nextDrawingButton != null) {
                nextDrawingButton.setEnabled(false); // Disable until rating is selected
            }
        } else {
            // No other players to rate, move to round end
            showRoundEndedUI(game);
        }
    }
    
    private void showGuessingUI() {
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.VISIBLE);
        wordToDrawText.setVisibility(View.GONE);
        timerText.setVisibility(View.VISIBLE);
        
        drawingView.setEnabled(false);
        startTimer();
    }
    
    // Track round end message display to avoid duplicates
    private int lastRoundEndedNumber = -1;
    
    private void showRoundEndedUI(Game game) {
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.VISIBLE);
        timerText.setVisibility(View.GONE);
        
        wordToDrawText.setText("The word was: " + game.getCurrentWord());
        
        // Only show the toast message once per round end
        if (lastRoundEndedNumber != game.getCurrentRound()) {
            lastRoundEndedNumber = game.getCurrentRound();
            Toast.makeText(this, "Round ended! Next round starting soon...", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showGameEndedUI(Game game) {
        drawingToolsContainer.setVisibility(View.GONE);
        guessingContainer.setVisibility(View.GONE);
        wordToDrawText.setVisibility(View.VISIBLE);
        timerText.setVisibility(View.GONE);
        
        wordToDrawText.setText("Game Over!");
        // Show final scores
        StringBuilder scores = new StringBuilder("Final Scores:\n");
        for (Player player : game.getPlayers()) {
            scores.append(player.getName()).append(": ").append(player.getScore()).append("\n");
        }
        Toast.makeText(this, scores.toString(), Toast.LENGTH_LONG).show();
    }
    
    private void submitGuess(String guessText) {
        // Get the current player's name
        Player currentPlayer = gameManager.getGame().findPlayerById(userId);
        if (currentPlayer == null) return;
        
        // Create a properly formatted guess with the correct constructor parameters
        Guess guess = new Guess(userId, currentPlayer.getName(), guessText);
        gameManager.handleGuess(userId, guessText);
        firebaseHandler.submitGuess(lobbyId, guess);
    }
    
    /**
     * Process a drawing action received from another client
     * @param action The drawing action to process
     */
    private void processDrawingAction(DrawingAction action) {
        if (action == null) return;
        
        runOnUiThread(() -> {
            // Apply the drawing action to the canvas
            switch (action.getActionType()) {
                case DrawingAction.ACTION_DRAW:
                case DrawingAction.ACTION_MOVE:
                    // Handle drawing/move action
                    drawingView.drawFromAction(action);
                    break;
                case DrawingAction.ACTION_CLEAR:
                    drawingView.clearCanvas();
                    break;
                case DrawingAction.ACTION_FILL:
                    // Fill functionality could be implemented here
                    break;
                case DrawingAction.ACTION_UNDO:
                    // Undo functionality could be implemented here
                    break;

            }
        });
    }
    
    private void startTimer() {
        // Cancel any existing timer
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        
        // Start a new timer
        roundTimer = new CountDownTimer(ROUND_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update the timer text
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                timerText.setText(String.format("%d:%02d", secondsRemaining / 60, secondsRemaining % 60));
            }
            
            @Override
            public void onFinish() {
                // Round ended
                timerText.setText("0:00");
                
                // Notify the game manager
                Game game = gameManager.getGame();
                if (game != null) {
                    if (game.getStatus() == GameStatus.DRAWING) {
                        // In the new gameplay, transition to rating phase
                        gameManager.startRatingPhase();
                    } else if (game.getStatus() == GameStatus.RATING) {
                        // If we're in the rating phase and the timer finishes, end the rating phase
                        gameManager.handleRatingPhaseTimeout();
                    }
                }
            }
        }.start();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Pause timers when activity is not visible
        if (roundTimer != null) {
            roundTimer.cancel();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel timers
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        // Detach all listeners
        detachAllListeners();
        // End game if needed
        if (firebaseHandler != null && lobbyId != null) {
            firebaseHandler.endGame(lobbyId);
        }
    }
    
    private void detachAllListeners() {
        // Safe cleanup of all Firebase listeners to prevent memory leaks and channel errors
        try {
            if (firebaseHandler != null && lobbyId != null) {
                // Remove game state listeners
                firebaseHandler.removeGameListeners(lobbyId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detaching listeners", e);
        }
    }
    
    // GameEventListener implementation
    @Override
    public void onGameStarted(Game game) {
        Log.d(TAG, "Game started");
        runOnUiThread(() -> {
            // Hide loading indicator
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.GONE);
            }
            
            updateUI(game);
            Toast.makeText(this, "Game started!", Toast.LENGTH_SHORT).show();
            
            // Start the first round immediately
            startRound();
        });
    }
    
    @Override
    public void onRoundStarted(Game game) {
        Log.d(TAG, "Round started: " + game.getCurrentRound());
        // Reset UI flags for new round
        wordSelectionMessageShown = false;
    }
    
    @Override
    public void onWordSelectionStarted(Game game, Player drawer, List<String> wordChoices) {
        Log.d(TAG, "Word selection started");
        // Reset the word selection message flag at the start of each word selection phase
        wordSelectionMessageShown = false;
        
        if (userId.equals(drawer.getId())) {
            showWordSelectionUI(wordChoices);
        } else {
            showWaitingForWordSelectionUI();
        }
    }
    
    @Override
    public void onDrawingPhaseStarted(Game game) {
        Log.d(TAG, "Drawing phase started");
        runOnUiThread(() -> {
            updateUI(game);
        });
    }
    
    @Override
    public void onRatingPhaseStarted(Game game) {
        Log.d(TAG, "Rating phase started");
        runOnUiThread(() -> {
            // Update the UI for rating phase
            updateUI(game);
            Toast.makeText(this, "Time to rate the drawings!", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onDrawingActionReceived(Game game, DrawingAction action) {
        // In the new gameplay, we don't need to process drawing actions in real-time
        // since each player draws their own version of the word
        // We'll only show the drawings during the rating phase
    }

    @Override
    public void onGuessReceived(Game game, Guess guess) {
        Log.d(TAG, "Guess received from " + guess.getPlayerId() + ": " + guess.getGuessText());
        runOnUiThread(() -> {
            // Could display guesses in a chat-like UI
            // For now, just show a toast with the guess
            String playerName = guess.getPlayerName();
            String guessText = guess.getGuessText();
            boolean isCorrect = guess.isCorrect();
            
            if (isCorrect && !isDrawer) {
                Toast.makeText(this, playerName + " guessed correctly!", Toast.LENGTH_SHORT).show();
            } else if (!isCorrect && !isDrawer) {
                Toast.makeText(this, playerName + ": " + guessText, Toast.LENGTH_SHORT).show();
            } else if (isDrawer) {
                // Don't show the guess text to the drawer, just that someone guessed
                Toast.makeText(this, playerName + " made a guess", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRoundEnded(Game game) {
        Log.d(TAG, "Round ended: " + game.getCurrentRound());
        runOnUiThread(() -> {
            updateUI(game);
        });
    }

    @Override
    public void onGameEnded(Game game, String reason) {
        Log.d(TAG, "Game ended: " + reason);
        runOnUiThread(() -> {
            updateUI(game);
            Toast.makeText(this, "Game ended: " + reason, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPlayerJoined(Game game, Player player) {
        Log.d(TAG, "Player joined: " + player.getName());
        runOnUiThread(() -> {
            updateUI(game);
            Toast.makeText(this, player.getName() + " joined the game", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPlayerLeft(Game game, Player player) {
        Log.d(TAG, "Player left: " + player.getName());
        runOnUiThread(() -> {
            updateUI(game);
            Toast.makeText(this, player.getName() + " left the game", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPlayerDisconnected(Game game, Player player) {
        Log.d(TAG, "Player disconnected: " + player.getName());
        runOnUiThread(() -> {
            updateUI(game);
            Toast.makeText(this, player.getName() + " disconnected", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPlayerReconnected(Game game, Player player) {
        Log.d(TAG, "Player reconnected: " + player.getName());
        runOnUiThread(() -> {
            updateUI(game);
            Toast.makeText(this, player.getName() + " reconnected", Toast.LENGTH_SHORT).show();
        });
    }

    // Track the current host to avoid duplicate notifications
    private String currentHostId = null;

    @Override
    public void onHostChanged(Game game, Player newHost) {
        Log.d(TAG, "Host changed to: " + newHost.getName());
        
        // Only show the host change notification once
        if (userId.equals(newHost.getId()) && !userId.equals(currentHostId)) {
            currentHostId = userId;
            runOnUiThread(() -> {
                Toast.makeText(this, "You are now the host", Toast.LENGTH_SHORT).show();
            });
        } else {
            currentHostId = newHost.getId();
        }
    }
    
    /**
     * Force start the game regardless of current state
     */
    private void forceStartGame() {
        if (gameStartAttempted) {
            Log.d(TAG, "Game already started, ignoring force start");
            return;
        }
        
        Log.d(TAG, "Force starting game");
        gameStartAttempted = true;
        
        // Make sure we have at least 2 players
        if (gameManager.getGame().getPlayers().size() < 2) {
            // Add a dummy player if needed for testing
            String dummyId = "dummy_" + System.currentTimeMillis();
            gameManager.addPlayer(dummyId, "Player 2");
            Log.d(TAG, "Added dummy player to allow game to start");
        }
        
        // Start the game
        boolean started = gameManager.startGame();
        Log.d(TAG, "Game start result: " + started);
        
        if (!started) {
            // If game didn't start, try to fix the game state and try again
            Game game = gameManager.getGame();
            game.setStatus(GameStatus.WAITING);
            gameManager.setGame(game);
            
            // Try again
            started = gameManager.startGame();
            Log.d(TAG, "Second game start attempt result: " + started);
            
            if (!started) {
                // If still not started, show error
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to start game. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
    
    /**
     * Start a new round manually
     */
    private void startRound() {
        Game game = gameManager.getGame();
        if (game != null) {
            // Reset for new round
            waitingMessageShown = false;
            wordSelectionMessageShown = false;
            
            // Generate word choices
            List<String> wordChoices = wordProvider.getWords();
            game.setWordChoices(wordChoices);
            game.setStatus(GameStatus.WORD_SELECTION);
            
            // In the new gameplay, the host selects the word for everyone
            Player currentPlayer = game.findPlayerById(userId);
            if (currentPlayer != null && currentPlayer.isHost()) {
                // Update Firebase with the word choices
                firebaseHandler.updateGameWordChoices(lobbyId, wordChoices);
                
                // Automatically select a word after a short delay if not manually selected
                new Handler().postDelayed(() -> {
                    Game currentGame = gameManager.getGame();
                    if (currentGame != null && currentGame.getStatus() == GameStatus.WORD_SELECTION) {
                        // Auto-select the first word
                        if (wordChoices != null && !wordChoices.isEmpty()) {
                            gameManager.selectWord(userId, wordChoices.get(0));
                        }
                    }
                }, 10000); // 10 second delay for word selection
            }
        }
    }}

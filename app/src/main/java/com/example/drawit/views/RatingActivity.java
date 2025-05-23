package com.example.drawit.views;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.R;
import com.example.drawit.game.FirebaseHandler;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.game.models.Game;
import com.example.drawit.models.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity for rating drawings and displaying leaderboard at the end of a game
 */
public class RatingActivity extends AppCompatActivity {
    private static final String TAG = "RatingActivity";
    
    private String lobbyId;
    private String userId;
    private FirebaseHandler firebaseHandler;
    private Game game;
    
    // UI components
    private DrawingView drawingView;
    private TextView artistNameTextView;
    private TextView wordPromptTextView;
    private TextView ratingInstructionTextView;
    private Button rating1Button;
    private Button rating2Button;
    private Button rating3Button;
    private Button nextDrawingButton;
    private TextView leaderboardTextView;
    
    // State variables
    private List<String> playerIds = new ArrayList<>();
    private int currentDrawingIndex = 0;
    private boolean showingLeaderboard = false;
    private Map<String, Integer> playerRatings = new HashMap<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);
        
        // Get intent extras
        lobbyId = getIntent().getStringExtra("lobby_id");
        userId = getIntent().getStringExtra("user_id");
        
        // Initialize Firebase
        firebaseHandler = FirebaseHandler.getInstance();
        
        // Initialize UI components
        initializeUI();
        
        // Load the game data
        loadGame();
    }
    
    private void initializeUI() {
        drawingView = findViewById(R.id.rating_drawing_view);
        artistNameTextView = findViewById(R.id.artist_name_text_view);
        wordPromptTextView = findViewById(R.id.word_prompt_text_view);
        ratingInstructionTextView = findViewById(R.id.rating_instruction_text_view);
        rating1Button = findViewById(R.id.rating_1_button);
        rating2Button = findViewById(R.id.rating_2_button);
        rating3Button = findViewById(R.id.rating_3_button);
        nextDrawingButton = findViewById(R.id.next_drawing_button);
        leaderboardTextView = findViewById(R.id.leaderboard_text_view);
        
        // Set up rating buttons
        rating1Button.setOnClickListener(v -> rateCurrentDrawing(1));
        rating2Button.setOnClickListener(v -> rateCurrentDrawing(2));
        rating3Button.setOnClickListener(v -> rateCurrentDrawing(3));
        
        // Set up next drawing button
        nextDrawingButton.setOnClickListener(v -> showNextDrawing());
        
        // Make drawing view non-interactive
        drawingView.setEnabled(false);
    }
    
    private void loadGame() {
        if (firebaseHandler == null || lobbyId == null) {
            Toast.makeText(this, "Error loading game data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        firebaseHandler.getGameReference(lobbyId).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot dataSnapshot) {
                game = dataSnapshot.getValue(Game.class);
                
                if (game == null) {
                    Toast.makeText(RatingActivity.this, "Game not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                
                // Set up the drawing display
                setupRatingFlow();
            }
            
            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError databaseError) {
                Log.e(TAG, "Error loading game data", databaseError.toException());
                Toast.makeText(RatingActivity.this, "Error loading game data", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    private void setupRatingFlow() {
        // Get all players who have drawings
        Map<String, List<DrawingAction>> drawings = game.getPlayerDrawings();
        if (drawings == null || drawings.isEmpty()) {
            Toast.makeText(this, "No drawings found", Toast.LENGTH_SHORT).show();
            showLeaderboard();
            return;
        }
        
        // Filter out the current user's drawing (you don't rate your own)
        for (String playerId : drawings.keySet()) {
            if (!playerId.equals(userId)) {
                playerIds.add(playerId);
            }
        }
        
        if (playerIds.isEmpty()) {
            Toast.makeText(this, "No drawings to rate", Toast.LENGTH_SHORT).show();
            showLeaderboard();
            return;
        }
        
        // Start with the first drawing
        showDrawingAtIndex(0);
    }
    
    private void showDrawingAtIndex(int index) {
        if (index < 0 || index >= playerIds.size() || game == null) {
            showLeaderboard();
            return;
        }
        
        currentDrawingIndex = index;
        String playerId = playerIds.get(index);
        
        // Get the player's drawing
        List<DrawingAction> drawingActions = game.getPlayerDrawing(playerId);
        
        // Clear the canvas and replay the drawing actions
        drawingView.clearCanvas();
        if (drawingActions != null) {
            for (DrawingAction action : drawingActions) {
                drawingView.drawFromAction(action);
            }
        }
        
        // Get the player name
        String playerName = "Unknown Artist";
        for (Player player : game.getPlayers()) {
            if (player.getId().equals(playerId)) {
                playerName = player.getUsername();
                break;
            }
        }
        
        // Update UI
        artistNameTextView.setText("Artist: " + playerName);
        wordPromptTextView.setText("Word: " + game.getCurrentWord());
        ratingInstructionTextView.setText("Rate this drawing (1-3 stars):");
        
        // Show rating buttons
        boolean alreadyRated = game.getRating(playerId, userId) > 0;
        setRatingButtonsEnabled(!alreadyRated);
        
        // Update next button text
        boolean isLastDrawing = currentDrawingIndex == playerIds.size() - 1;
        nextDrawingButton.setText(isLastDrawing ? "Show Leaderboard" : "Next Drawing");
    }
    
    private void rateCurrentDrawing(int rating) {
        if (currentDrawingIndex < 0 || currentDrawingIndex >= playerIds.size() || game == null) {
            return;
        }
        
        String drawingPlayerId = playerIds.get(currentDrawingIndex);
        
        // Save the rating
        game.addRating(drawingPlayerId, userId, rating);
        playerRatings.put(drawingPlayerId, rating);
        
        // Disable rating buttons after rating
        setRatingButtonsEnabled(false);
        
        // Save the rating to Firebase
        if (firebaseHandler != null) {
            firebaseHandler.getGameReference(lobbyId)
                .child("ratings")
                .child(drawingPlayerId)
                .child(userId)
                .setValue(rating)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RatingActivity.this, "Rating saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving rating", e);
                    Toast.makeText(RatingActivity.this, "Error saving rating", Toast.LENGTH_SHORT).show();
                });
        }
    }
    
    private void setRatingButtonsEnabled(boolean enabled) {
        rating1Button.setEnabled(enabled);
        rating2Button.setEnabled(enabled);
        rating3Button.setEnabled(enabled);
    }
    
    private void showNextDrawing() {
        if (currentDrawingIndex < playerIds.size() - 1) {
            // Show the next drawing
            showDrawingAtIndex(currentDrawingIndex + 1);
        } else {
            // Show the leaderboard
            showLeaderboard();
        }
    }
    
    private void showLeaderboard() {
        // Hide drawing and rating controls
        drawingView.setVisibility(View.GONE);
        artistNameTextView.setVisibility(View.GONE);
        wordPromptTextView.setVisibility(View.GONE);
        ratingInstructionTextView.setVisibility(View.GONE);
        rating1Button.setVisibility(View.GONE);
        rating2Button.setVisibility(View.GONE);
        rating3Button.setVisibility(View.GONE);
        
        // Show leaderboard
        leaderboardTextView.setVisibility(View.VISIBLE);
        
        // Change next button to exit
        nextDrawingButton.setText("Exit");
        nextDrawingButton.setOnClickListener(v -> finish());
        
        // Calculate final scores including ratings
        calculateFinalScores();
    }
    
    private void calculateFinalScores() {
        if (game == null) return;
        
        // Create a copy of the players to sort
        List<Player> sortedPlayers = new ArrayList<>(game.getPlayers());
        
        // Update scores with ratings
        for (Player player : sortedPlayers) {
            int ratingScore = game.getTotalRating(player.getId());
            player.setScore(player.getScore() + ratingScore);
        }
        
        // Sort by score (highest first)
        sortedPlayers.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        
        // Build leaderboard text
        StringBuilder leaderboard = new StringBuilder();
        leaderboard.append("FINAL SCORES\n\n");
        
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            leaderboard.append(String.format("%d. %s: %d points\n", 
                i + 1, player.getUsername(), player.getScore()));
        }
        
        // Display leaderboard
        leaderboardTextView.setText(leaderboard.toString());
        
        // Mark game as ended
        if (firebaseHandler != null) {
            firebaseHandler.getGameReference(lobbyId)
                .child("status")
                .setValue("ENDED");
        }
    }
}

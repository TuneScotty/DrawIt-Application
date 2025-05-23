package com.example.drawit.views;

import android.util.Log;
import android.widget.Toast;
import com.example.drawit.game.FirebaseHandler;
import com.example.drawit.game.GameManager;
import com.example.drawit.game.models.DrawingAction;
import com.example.drawit.game.models.Game;
import com.example.drawit.game.models.Guess;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class FirebaseListenerManager {
    private static final String TAG = "FirebaseListenerManager";
    private final FirebaseHandler firebaseHandler;
    private final String lobbyId;
    private final GameManager gameManager;
    private final boolean isDrawer;
    private final Runnable forceStartGame;
    private final Runnable finishGame;
    private final UIUpdater uiUpdater;
    private final DrawingView drawingView;
    private final Map<String, Object> listeners = new HashMap<>();

    public FirebaseListenerManager(FirebaseHandler firebaseHandler, String lobbyId, GameManager gameManager, boolean isDrawer, Runnable forceStartGame, Runnable finishGame, UIUpdater uiUpdater, DrawingView drawingView) {
        this.firebaseHandler = firebaseHandler;
        this.lobbyId = lobbyId;
        this.gameManager = gameManager;
        this.isDrawer = isDrawer;
        this.forceStartGame = forceStartGame;
        this.finishGame = finishGame;
        this.uiUpdater = uiUpdater;
        this.drawingView = drawingView;
    }

    public void setupGameListeners() {
        Log.d(TAG, "Setting up game listeners for lobby: " + lobbyId);
        
        // Make sure we have a valid lobby ID
        if (lobbyId == null || lobbyId.trim().isEmpty()) {
            Log.e(TAG, "Cannot setup game listeners: Invalid lobby ID");
            return;
        }
        
        try {
            // Listen for game state changes from Firebase
            firebaseHandler.listenForGameUpdates(lobbyId, new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Log.d(TAG, "Firebase game data updated: " + dataSnapshot.toString());

                    // If we have a full Game object, update the local game state
                    try {
                        Game game = dataSnapshot.getValue(Game.class);
                        if (game != null) {
                            Log.d(TAG, "Received game state from Firebase: " + game.getStatus());
                            gameManager.setGame(game);
                            if (uiUpdater != null) {
                                uiUpdater.updateUI(game); // Update UI after setting game state
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing game data from Firebase", e);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Firebase game data sync failed", databaseError.toException());
                }
            });

            // Listen for drawing actions from other players
            firebaseHandler.listenForDrawingActions(lobbyId, new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                    try {
                        DrawingAction action = dataSnapshot.getValue(DrawingAction.class);
                        if (action != null && !isDrawer) {
                            Log.d(TAG, "Received drawing action: " + action.getActionType());
                            processDrawingAction(action);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing drawing action", e);
                    }
                }

                @Override public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}
                @Override public void onChildRemoved(DataSnapshot dataSnapshot) {}
                @Override public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}
                @Override public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Drawing actions listener cancelled", databaseError.toException());
                }
            });

            // Listen for guesses from other players
            firebaseHandler.listenForGuesses(lobbyId, new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                    try {
                        Guess guess = dataSnapshot.getValue(Guess.class);
                        if (guess != null) {
                            Log.d(TAG, "Received guess from player: " + guess.getPlayerId());
                            gameManager.handleGuess(guess.getPlayerId(), guess.getGuessText());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing guess", e);
                    }
                }

                @Override public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {}
                @Override public void onChildRemoved(DataSnapshot dataSnapshot) {}
                @Override public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {}
                @Override public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Guesses listener cancelled", databaseError.toException());
                }
            });
            
            // Listen for game end
            firebaseHandler.addInGameLobbyListener(lobbyId, gameStarted -> {
                Log.d(TAG, "Game started status changed: " + gameStarted);
                if (!gameStarted) {
                    finishGame.run();
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up game listeners", e);
        }
    }

    public void removeListeners() {
        firebaseHandler.removeGameListeners(lobbyId);
        listeners.clear();
    }

    private void processDrawingAction(DrawingAction action) {
        if (drawingView != null) {
            drawingView.drawFromAction(action);
        }
    }
}

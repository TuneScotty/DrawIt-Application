package com.example.drawit.game.models;

/**
 * Enum representing the possible states of a game.
 */
public enum GameStatus {
    // Lobby states
    WAITING_FOR_PLAYERS,  // Waiting for more players to join
    WAITING_FOR_START,    // Waiting for host to start the game
    
    // Game flow states
    STARTED,              // Game has started
    WORD_SELECTION,       // Drawer is selecting a word
    DRAWING,              // Drawer is drawing, others are guessing
    GUESSING,             // Time for guessing the word
    BETWEEN_ROUNDS,       // Time between rounds
    ROUND_ENDED,          // Current round has ended
    RATING,               // Players are rating drawings
    
    // Game end states
    GAME_OVER,            // Game has ended, showing final results
    ENDED                 // Game has completely ended
}

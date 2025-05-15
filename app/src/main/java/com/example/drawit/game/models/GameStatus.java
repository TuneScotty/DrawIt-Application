package com.example.drawit.game.models;

/**
 * Enum representing the possible states of a game.
 */
public enum GameStatus {
    WAITING,           // Players are in the lobby, waiting for the game to start
    WORD_SELECTION,    // The drawer is selecting a word
    DRAWING,           // All players are drawing the same word
    RATING,            // Players are rating each other's drawings
    ROUND_ENDED,       // The current round has ended, showing results
    STARTED,           // The game has started
    ENDED              // The game has ended, showing final leaderboard
}

package com.example.drawit.game;

import java.util.List;

/**
 * Interface for providing words for the drawing-and-guessing game.
 * Implementations can provide words from different sources (e.g., built-in list, custom list, API).
 */
public interface WordProvider {
    
    /**
     * Get a list of words that can be used in the game.
     * @return A list of words
     */
    List<String> getWords();
    
    /**
     * Get a list of words with a specific difficulty level.
     * @param difficulty The difficulty level (e.g., "easy", "medium", "hard")
     * @return A list of words with the specified difficulty
     */
    List<String> getWordsByDifficulty(String difficulty);
    
    /**
     * Get a list of words from a specific category.
     * @param category The category (e.g., "animals", "food", "sports")
     * @return A list of words in the specified category
     */
    List<String> getWordsByCategory(String category);
}

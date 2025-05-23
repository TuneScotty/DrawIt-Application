package com.example.drawit.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of WordProvider with predefined word lists.
 * Provides words categorized by difficulty and category.
 */
public class DefaultWordProvider implements WordProvider {
    
    private final List<String> allWords;
    private final Map<String, List<String>> wordsByDifficulty;
    private final Map<String, List<String>> wordsByCategory;
    
    public DefaultWordProvider() {
        // Initialize collections
        this.allWords = new ArrayList<>();
        this.wordsByDifficulty = new HashMap<>();
        this.wordsByCategory = new HashMap<>();
        
        // Initialize difficulty levels
        wordsByDifficulty.put("easy", new ArrayList<>());
        wordsByDifficulty.put("medium", new ArrayList<>());
        wordsByDifficulty.put("hard", new ArrayList<>());
        
        // Initialize categories
        wordsByCategory.put("animals", new ArrayList<>());
        wordsByCategory.put("food", new ArrayList<>());
        wordsByCategory.put("sports", new ArrayList<>());
        wordsByCategory.put("objects", new ArrayList<>());
        wordsByCategory.put("nature", new ArrayList<>());
        
        // Populate word lists
        populateWordLists();
    }
    
    private void populateWordLists() {
        // Easy animals
        addWord("dog", "easy", "animals");
        addWord("cat", "easy", "animals");
        addWord("fish", "easy", "animals");
        addWord("bird", "easy", "animals");
        addWord("duck", "easy", "animals");
        addWord("pig", "easy", "animals");
        addWord("frog", "easy", "animals");
        addWord("cow", "easy", "animals");
        addWord("rabbit", "easy", "animals");
        addWord("mouse", "easy", "animals");
        
        // Medium animals
        addWord("elephant", "medium", "animals");
        addWord("giraffe", "medium", "animals");
        addWord("kangaroo", "medium", "animals");
        addWord("dolphin", "medium", "animals");
        addWord("penguin", "medium", "animals");
        addWord("octopus", "medium", "animals");
        addWord("panda", "medium", "animals");
        addWord("koala", "medium", "animals");
        addWord("tiger", "medium", "animals");
        addWord("monkey", "medium", "animals");
        addWord("zebra", "medium", "animals");
        
        // Hard animals
        addWord("platypus", "hard", "animals");
        addWord("narwhal", "hard", "animals");
        addWord("armadillo", "hard", "animals");
        addWord("chameleon", "hard", "animals");
        addWord("scorpion", "hard", "animals");
        
        // Easy food
        addWord("pizza", "easy", "food");
        addWord("apple", "easy", "food");
        addWord("bread", "easy", "food");
        addWord("cake", "easy", "food");
        addWord("egg", "easy", "food");
        
        // Medium food
        addWord("hamburger", "medium", "food");
        addWord("spaghetti", "medium", "food");
        addWord("sandwich", "medium", "food");
        addWord("pancake", "medium", "food");
        addWord("popcorn", "medium", "food");
        
        // Hard food
        addWord("croissant", "hard", "food");
        addWord("sushi", "hard", "food");
        addWord("quesadilla", "hard", "food");
        addWord("guacamole", "hard", "food");
        addWord("ratatouille", "hard", "food");
        
        // Easy sports
        addWord("soccer", "easy", "sports");
        addWord("tennis", "easy", "sports");
        addWord("golf", "easy", "sports");
        addWord("baseball", "easy", "sports");
        addWord("swimming", "easy", "sports");
        
        // Medium sports
        addWord("basketball", "medium", "sports");
        addWord("volleyball", "medium", "sports");
        addWord("hockey", "medium", "sports");
        addWord("skiing", "medium", "sports");
        addWord("boxing", "medium", "sports");
        
        // Hard sports
        addWord("badminton", "hard", "sports");
        addWord("lacrosse", "hard", "sports");
        addWord("wrestling", "hard", "sports");
        addWord("archery", "hard", "sports");
        addWord("fencing", "hard", "sports");
        
        // Easy objects
        addWord("chair", "easy", "objects");
        addWord("table", "easy", "objects");
        addWord("book", "easy", "objects");
        addWord("door", "easy", "objects");
        addWord("phone", "easy", "objects");
        
        // Medium objects
        addWord("computer", "medium", "objects");
        addWord("television", "medium", "objects");
        addWord("umbrella", "medium", "objects");
        addWord("backpack", "medium", "objects");
        addWord("microwave", "medium", "objects");
        
        // Hard objects
        addWord("telescope", "hard", "objects");
        addWord("typewriter", "hard", "objects");
        addWord("chandelier", "hard", "objects");
        addWord("microscope", "hard", "objects");
        addWord("thermostat", "hard", "objects");
        
        // Easy nature
        addWord("tree", "easy", "nature");
        addWord("flower", "easy", "nature");
        addWord("sun", "easy", "nature");
        addWord("moon", "easy", "nature");
        addWord("rain", "easy", "nature");
        
        // Medium nature
        addWord("mountain", "medium", "nature");
        addWord("waterfall", "medium", "nature");
        addWord("volcano", "medium", "nature");
        addWord("rainbow", "medium", "nature");
        addWord("lightning", "medium", "nature");
        
        // Hard nature
        addWord("avalanche", "hard", "nature");
        addWord("earthquake", "hard", "nature");
        addWord("hurricane", "hard", "nature");
        addWord("constellation", "hard", "nature");
        addWord("archipelago", "hard", "nature");
    }
    
    private void addWord(String word, String difficulty, String category) {
        allWords.add(word);
        wordsByDifficulty.get(difficulty).add(word);
        wordsByCategory.get(category).add(word);
    }
    
    @Override
    public List<String> getWords() {
        return new ArrayList<>(allWords);
    }
    
    @Override
    public List<String> getWordsByDifficulty(String difficulty) {
        List<String> words = wordsByDifficulty.get(difficulty);
        return words != null ? new ArrayList<>(words) : new ArrayList<>();
    }
    
    @Override
    public List<String> getWordsByCategory(String category) {
        if (category == null || !wordsByCategory.containsKey(category)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(wordsByCategory.get(category));
    }
    
    @Override
    public String getRandomWord() {
        if (allWords.isEmpty()) {
            return "elephant"; // Default fallback if no words are available
        }
        
        // Pick a random word from the list
        int randomIndex = (int) (Math.random() * allWords.size());
        return allWords.get(randomIndex);
    }
    
    @Override
    public List<String> getRandomWords(int count) {
        List<String> result = new ArrayList<>();
        
        if (allWords.isEmpty()) {
            // Add default words if no words are available
            result.add("elephant");
            result.add("dog");
            result.add("house");
            return result;
        }
        
        // Create a copy of all words to avoid duplicates
        List<String> availableWords = new ArrayList<>(allWords);
        
        // Shuffle the list for randomness
        Collections.shuffle(availableWords);
        
        // Take the first 'count' elements or as many as available
        for (int i = 0; i < Math.min(count, availableWords.size()); i++) {
            result.add(availableWords.get(i));
        }
        
        return result;
    }
    
    /**
     * Get a custom list of words for testing
     */
    public static List<String> getTestWords() {
        return Arrays.asList(
            "dog", "cat", "house", "car", "tree", 
            "book", "phone", "computer", "pizza", "apple"
        );
    }
}

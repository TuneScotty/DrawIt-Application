package com.example.drawit_app.view.game;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;

import com.example.drawit_app.repository.GameRepository;
import com.google.android.material.chip.Chip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentGameBinding;
import com.example.drawit_app.model.ChatMessage;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.PlayerScore;
import com.example.drawit_app.model.User;
import com.example.drawit_app.view.custom.DrawingView.BrushType;
import com.example.drawit_app.api.WebSocketService;
import com.example.drawit_app.api.message.GameStateMessage;
import com.example.drawit_app.view.adapter.ChatAdapter;
import com.example.drawit_app.view.adapter.PlayerScoreAdapter;
import com.example.drawit_app.viewmodel.DrawingViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for the active drawing and guessing gameplay
 */
@AndroidEntryPoint
public class GameFragment extends Fragment implements WebSocketService.GameUpdateCallback {

    private FragmentGameBinding binding;
    private DrawingViewModel drawingViewModel;
    private NavController navController;
    private String gameId;
    private User currentUser;
    private ChatAdapter chatAdapter;
    private PlayerScoreAdapter scoreAdapter;
    private CountDownTimer roundTimer;
    private com.example.drawit_app.view.adapter.PlayerAdapter playerAdapter;
    private GameRepository gameRepository;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGameBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        drawingViewModel = new ViewModelProvider(requireActivity()).get(DrawingViewModel.class);
        
        // Get game ID from arguments
        if (getArguments() != null) {
            gameId = getArguments().getString("gameId");
        }
        
        if (gameId == null) {
            // Invalid state, go back
            Toast.makeText(requireContext(), "Invalid game", Toast.LENGTH_SHORT).show();
            navController.navigateUp();
            return;
        }
        
        // Show loading state
        binding.progressBarGame.setVisibility(View.VISIBLE);
        binding.gameContentContainer.setVisibility(View.GONE);
        
        setupRecyclerViews();
        setupColorPalette();
        setupListeners();
        observeViewModel();
        
        // Set WebSocket callback
        drawingViewModel.setGameUpdateCallback(this);

        Log.d("GameFragment", "Joining game with ID: " + gameId);
        
        // Join game with a timeout to ensure we don't get stuck if WebSocket fails
        drawingViewModel.joinGame(gameId);
        
        // CRITICAL FIX: Check for existing game data immediately
        Game existingGame = drawingViewModel.getCurrentGame().getValue();
        if (existingGame != null && existingGame.getGameId().equals(gameId)) {
            Log.d("GameFragment", "✅ Found existing game data, updating UI immediately");
            // Hide loading indicator and show game content
            binding.progressBarGame.setVisibility(View.GONE);
            binding.gameContentContainer.setVisibility(View.VISIBLE);
            // Update game state with existing data
            updateGameState(existingGame);
        }
        
        // Add a safety timeout to check if we've received game data
        new Handler().postDelayed(() -> {
            if (isAdded() && binding != null) {
                Game game = drawingViewModel.getCurrentGame().getValue();
                if (game == null) {
                    // If after 8 seconds we still don't have game data, show an error
                    binding.progressBarGame.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Failed to load game data. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("GameFragment", "Timed out waiting for game data");
                    // Go back to the lobby
                    navController.navigateUp();
                } else if (binding.progressBarGame.getVisibility() == View.VISIBLE) {
                    // We have game data but UI hasn't been updated yet
                    Log.d("GameFragment", "⚠️ Game data received but UI not updated, forcing update");
                    binding.progressBarGame.setVisibility(View.GONE);
                    binding.gameContentContainer.setVisibility(View.VISIBLE);
                    updateGameState(game);
                }
            }
        }, 8000); // Increased timeout to 8 seconds
    }
    
    private void setupRecyclerViews() {
        // Setup chat adapter
        chatAdapter = new ChatAdapter(new ArrayList<ChatMessage>(), currentUser);
        binding.recyclerViewChat.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewChat.setAdapter(chatAdapter);
        
        // Setup score adapter
        scoreAdapter = new PlayerScoreAdapter(new ArrayList<>());
        binding.recyclerViewScores.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewScores.setAdapter(scoreAdapter);
        
        // Setup player adapter
        playerAdapter = new com.example.drawit_app.view.adapter.PlayerAdapter(new ArrayList<>());
        binding.recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewPlayers.setAdapter(playerAdapter);
    }
    
    /**
     * Sets up the color palette with drawing color options
     */
    private void setupColorPalette() {
        // Define colors for the palette
        int[] colors = {
            Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE,
            Color.RED, Color.rgb(255, 165, 0), Color.YELLOW, 
            Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA,
            Color.rgb(139, 69, 19), Color.rgb(255, 105, 180), 
            Color.rgb(0, 128, 0), Color.rgb(75, 0, 130)
        };
        
        // Clear any existing views
        binding.colorPalette.removeAllViews();
        
        // Create a button for each color
        for (int color : colors) {
            Button colorButton = new Button(requireContext());
            int buttonSize = getResources().getDimensionPixelSize(R.dimen.color_button_size);
            
            // Set button appearance
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonSize, buttonSize);
            params.setMargins(8, 8, 8, 8);
            colorButton.setLayoutParams(params);
            colorButton.setBackgroundColor(color);
            
            // Add border to white button for visibility
            if (color == Color.WHITE) {
                colorButton.setBackgroundResource(R.drawable.white_color_button);
            }
            
            // Set click listener to change drawing color
            colorButton.setOnClickListener(v -> {
                if (isDrawingTurn()) {
                    binding.drawingView.setColor(color);
                    Log.d(TAG, "🎨 Color changed to: " + String.format("#%06X", (0xFFFFFF & color)));
                }
            });
            
            // Add to palette
            binding.colorPalette.addView(colorButton);
        }
    }
    
    /**
     * Sets up listeners for brush type selection chips
     */
    private void setupBrushTypeListeners() {
        // Normal brush
        binding.chipBrushNormal.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                clearBrushTypeSelection();
                binding.chipBrushNormal.setChecked(true);
                binding.drawingView.setBrushType(BrushType.NORMAL);
                Log.d(TAG, "🖌️ Brush type set to NORMAL");
            }
        });
        
        // Calligraphy brush
        binding.chipBrushCalligraphy.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                clearBrushTypeSelection();
                binding.chipBrushCalligraphy.setChecked(true);
                binding.drawingView.setBrushType(BrushType.CALLIGRAPHY);
                Log.d(TAG, "🖌️ Brush type set to CALLIGRAPHY");
            }
        });
        
        // Airbrush
        binding.chipBrushAirbrush.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                clearBrushTypeSelection();
                binding.chipBrushAirbrush.setChecked(true);
                binding.drawingView.setBrushType(BrushType.AIRBRUSH);
                Log.d(TAG, "🖌️ Brush type set to AIRBRUSH");
            }
        });
        
        // Marker
        binding.chipBrushMarker.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                clearBrushTypeSelection();
                binding.chipBrushMarker.setChecked(true);
                binding.drawingView.setBrushType(BrushType.MARKER);
                Log.d(TAG, "🖌️ Brush type set to MARKER");
            }
        });
    }
    
    /**
     * Clears all brush type selections
     */
    private void clearBrushTypeSelection() {
        binding.chipBrushNormal.setChecked(false);
        binding.chipBrushCalligraphy.setChecked(false);
        binding.chipBrushAirbrush.setChecked(false);
        binding.chipBrushMarker.setChecked(false);
    }
    
    private void setupListeners() {
        // Setup brush type listeners
        setupBrushTypeListeners();
        
        // Send chat message button click listener
        binding.btnSendMessage.setOnClickListener(v -> {
            String message = binding.etChatMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                if (!isDrawingTurn() && checkIfCorrectGuess(message)) {
                    binding.etChatMessage.setText("");
                } else {
                    drawingViewModel.sendChatMessage(gameId, message);
                    binding.etChatMessage.setText("");
                }
            }
        });
        
        binding.etChatMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                binding.btnSendMessage.performClick();
                return true;
            }
            return false;
        });
        
        binding.btnClearDrawing.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                binding.drawingView.clearDrawing();
                drawingViewModel.updateDrawingPath(gameId, binding.drawingView.getPathsAsJson());
            }
        });
        
        // Brush type selection listeners are already set up
        
        // Pressure sensitivity toggle
        binding.switchPressureSensitivity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isDrawingTurn()) {
                binding.drawingView.setPressureSensitivityEnabled(isChecked);
                Log.d(TAG, "🖌️ Pressure sensitivity " + (isChecked ? "enabled" : "disabled"));
            }
        });
        
        // Brush size slider
        binding.brushSizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isDrawingTurn()) {
                    binding.drawingView.setStrokeWidth(progress);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        binding.drawingView.setOnPathUpdateListener(paths -> {
            if (isDrawingTurn()) {
                drawingViewModel.updateDrawingPath(gameId, paths);
            }
        });
    }
    
    private void observeViewModel() {
        drawingViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentUser = user;
                updateDrawingPermissions();

                Game currentGame = drawingViewModel.getCurrentGame().getValue();
                if (currentGame != null && binding.progressBarGame.getVisibility() == View.VISIBLE) {
                    binding.progressBarGame.setVisibility(View.GONE);
                    binding.gameContentContainer.setVisibility(View.VISIBLE);
                    updateGameState(currentGame);
                }
            }
        });
        
        drawingViewModel.getCurrentGame().observe(getViewLifecycleOwner(), game -> {
            if (game != null) {
                Log.d(TAG, "🎮 Game updated: " + game.getGameId() + ", State: " + 
                      (game.getGameState() != null ? game.getGameState().name() : "NULL"));

                Log.d(TAG, "📊 Game state details:" +
                      "\n - Current drawer: " + (game.getCurrentDrawer() != null ? 
                                              game.getCurrentDrawer().getUsername() : "NULL") +
                      "\n - Current word: " + (game.getCurrentWord() != null ? 
                                           game.getCurrentWord() : "NULL") +
                      "\n - Round: " + game.getCurrentRound() + "/" + game.getTotalRounds() +
                      "\n - Players: " + (game.getPlayers() != null ? 
                                       game.getPlayers().size() : "NULL"));

                binding.progressBarGame.setVisibility(View.GONE);
                binding.gameContentContainer.setVisibility(View.VISIBLE);
                
                updateGameState(game);
            } else {
                Log.w(TAG, "⚠️ Received null game update");
            }
        });

        drawingViewModel.getChatMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages != null) {
                List<ChatMessage> chatMessages = new ArrayList<>();
                if (!messages.isEmpty() && messages.get(0) != null) {
                    for (String msg : messages) {
                        ChatMessage chatMsg = new ChatMessage(null, msg, ChatMessage.MessageType.SYSTEM_MESSAGE);
                        chatMessages.add(chatMsg);
                    }
                } else {
                    for (String message : messages) {
                        ChatMessage chatMsg = new ChatMessage();
                        chatMsg.setMessage(message);
                        chatMsg.setType(ChatMessage.MessageType.SYSTEM_MESSAGE);
                        chatMessages.add(chatMsg);
                    }
                }
                chatAdapter.updateMessages(chatMessages);
                binding.recyclerViewChat.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
        
        drawingViewModel.getCurrentGame().observe(getViewLifecycleOwner(), game -> {
            if (game != null && game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                List<User> players = game.getPlayers();
                Log.d(TAG, "👥 Players updated: " + players.size() + " players");
                
                playerAdapter.updatePlayers(new ArrayList<>(players));
                
                if (game.getCurrentDrawer() == null) {
                    Log.d(TAG, "👥 Players updated but no drawer set, attempting to set drawer");
                    User firstPlayer = players.get(0);
                    game.setCurrentDrawer(firstPlayer);
                    game.setCurrentDrawerId(firstPlayer.getUserId());
                    Log.d(TAG, "✅ Set first player as drawer: " + firstPlayer.getUsername());
                    updateGameState(game);
                }
            }
        });
        
        drawingViewModel.getDrawingPaths().observe(getViewLifecycleOwner(), paths -> {
            if (paths != null && !paths.isEmpty()) {
                Log.d(TAG, "📝 Received drawing paths update: " + paths.substring(0, Math.min(50, paths.length())) + "...");
                
                // Only apply paths if we're not the drawer (otherwise we'd overwrite our own drawing)
                if (!isDrawingTurn()) {
                    try {
                        binding.drawingView.setPathsFromJson(paths);
                        Log.d(TAG, "✅ Applied drawing paths from drawer");
                    } catch (Exception e) {
                        Log.e(TAG, "Error applying drawing paths: " + e.getMessage());
                    }
                } else {
                    Log.d(TAG, "ℹ️ Ignoring drawing paths as we are the drawer");
                }
            }
        });
        
        drawingViewModel.getGameOverEvent().observe(getViewLifecycleOwner(), isGameOver -> {
            if (isGameOver) {
                Bundle args = new Bundle();
                args.putString("gameId", gameId);
                navController.navigate(R.id.action_gameFragment_to_gameResultsFragment, args);
            }
        });
        
        drawingViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded() && getContext() != null) {
                        // Handle specific error messages with clearer user feedback
                        if (errorMsg.contains("Lobby is locked") || errorMsg.contains("Cannot join game - lobby is locked")) {
                            // Show a more user-friendly message for locked lobbies
                            Toast.makeText(getContext(), "Cannot join game - the lobby is locked. The game may have already started.", Toast.LENGTH_LONG).show();
                            
                            // Navigate back to the lobbies list
                            new Handler().postDelayed(() -> {
                                if (isAdded() && navController != null) {
                                    navController.navigateUp();
                                }
                            }, 1500); // Give user time to read the message before navigating back
                        } else if (errorMsg.contains("Game not found")) {
                            // Handle game not found error
                            Toast.makeText(getContext(), "Game not found. It may have been deleted or never existed.", Toast.LENGTH_LONG).show();
                            
                            // Navigate back to the lobbies list
                            new Handler().postDelayed(() -> {
                                if (isAdded() && navController != null) {
                                    navController.navigateUp();
                                }
                            }, 1500);
                        } else {
                            // Generic error handling
                            Toast.makeText(getContext(), "Error: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }
    
    private void updateGameState(Game game) {
        if (game == null) {
            Log.w(TAG, "updateGameState called with null game");
            return;
        }
        
        binding.tvRound.setText(String.format(Locale.getDefault(), getString(R.string.round_format), 
                game.getCurrentRound(), game.getTotalRounds()));
        
        if (currentUser == null) {
            Log.e(TAG, "⚠️ Current user is null when updating game state! Fetching user data...");
            drawingViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    Log.d(TAG, "✅ Successfully retrieved current user: " + user.getUsername() + " (" + user.getUserId() + ")");
                    currentUser = user;
                    updateGameState(game);
                    updateDrawingPermissions(); // Also update drawing permissions once we have the user
                }
            });
            return;
        }
        
        if (game.getCurrentDrawer() == null) {
            Log.w(TAG, "⚠️ Game has no current drawer set! Attempting to recover...");
            
            List<User> players = game.getPlayers();
            if (players != null && !players.isEmpty()) {
                User firstPlayer = players.get(0);
                game.setCurrentDrawer(firstPlayer);
                game.setCurrentDrawerId(firstPlayer.getUserId());
                Log.d(TAG, "✅ Set first player as drawer: " + firstPlayer.getUsername() + 
                      " (ID: " + firstPlayer.getUserId() + ")");
            } else {
                binding.tvCurrentDrawer.setText(getString(R.string.waiting_for_players));
                Log.e(TAG, "❌ Cannot set drawer - no players available in game object");
                return;
            }
        }
        
        User currentDrawer = game.getCurrentDrawer();
        if (currentDrawer != null && "You".equals(currentDrawer.getUsername())) {
            Log.w(TAG, "⚠️ Drawer has placeholder name 'You'! Attempting to fix...");
            
            List<User> players = game.getPlayers();
            if (players != null) {
                for (User player : players) {
                    if (player.getUserId().equals(currentDrawer.getUserId())) {
                        game.setCurrentDrawer(player);
                        Log.d(TAG, "✅ Fixed drawer name from 'You' to: " + player.getUsername());
                        break;
                    }
                }
            }

            if ("You".equals(game.getCurrentDrawer().getUsername()) && 
                currentUser != null && 
                currentUser.getUserId().equals(currentDrawer.getUserId())) {
                game.setCurrentDrawer(currentUser);
                Log.d(TAG, "✅ Set current user as drawer after fixing 'You' placeholder");
            }
        }
        
        // Server should provide the word
        if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
            Log.w(TAG, "⚠️ Warning: Server did not provide a word for this round");
            // We'll wait for the server to provide the word instead of setting a default
        }
        
        // Now we should have both currentUser and currentDrawer set
        boolean isCurrentUserDrawer = currentUser.getUserId().equals(game.getCurrentDrawer().getUserId());
        
        // Log the comparison for debugging
        Log.d(TAG, "👤 Current user: " + currentUser.getUsername() + " (" + currentUser.getUserId() + ")");
        Log.d(TAG, "🎨 Current drawer: " + game.getCurrentDrawer().getUsername() + " (" + game.getCurrentDrawer().getUserId() + ")");
        Log.d(TAG, "❓ Is current user the drawer? " + isCurrentUserDrawer);
        
        // Update UI based on whether current user is the drawer
        if (isCurrentUserDrawer) {
            binding.tvCurrentDrawer.setText(getString(R.string.your_turn_to_draw));
            Log.d(TAG, "✏️ Setting UI for current user as drawer");
        } else {
            binding.tvCurrentDrawer.setText(String.format(Locale.getDefault(), 
                    "%s is drawing", game.getCurrentDrawer().getUsername()));
            Log.d(TAG, "👁️ Setting UI for current user as guesser, drawer is: " + 
                  game.getCurrentDrawer().getUsername());
        }
        
        // Make sure we update drawing permissions
        updateDrawingPermissions();
        
        // Set word to guess
        if (currentUser != null && game.getCurrentDrawer() != null && 
            currentUser.getUserId().equals(game.getCurrentDrawer().getUserId())) {
            // Current user is the drawer, show the word
            binding.tvWord.setText(String.format("Draw: %s", game.getCurrentWord()));
        } else {
            // Current user is guessing
            binding.tvWord.setText("Guess the word!");
        }
        
        // Update drawing permissions based on turn
        updateDrawingPermissions();
        
        // Set drawing paths if available
        if (game.getDrawingPaths() != null && !game.getDrawingPaths().isEmpty()) {
            binding.drawingView.setPathsFromJson(game.getDrawingPaths());
        }
        
        // Start timer if needed and not already running
        if (game.getRemainingTime() > 0) {
            // Always restart the timer with the current remaining time
            stopRoundTimer();
            startRoundTimer(game.getRemainingTime());
            Log.d(TAG, "⏱️ Started round timer with " + game.getRemainingTime() + " seconds");
        } else if (game.getGameState() == Game.GameState.ACTIVE || 
                  game.getGameState() == Game.GameState.DRAWING) {
            stopRoundTimer();
            startRoundTimer(0);
            Log.d(TAG, "⏱️ Started round timer with default time");
        } else {
            Log.d(TAG, "⏱️ Not starting timer - game state: " + game.getGameState() + ", time: " + game.getRemainingTime());
        }
    }
    
    private void updateGameUI() {
        boolean isDrawing = isDrawingTurn();
        
        // Show/hide drawing controls
        binding.drawingView.setEnabled(isDrawing);
        binding.colorPalette.setVisibility(isDrawing ? View.VISIBLE : View.GONE);
        binding.btnClearDrawing.setVisibility(isDrawing ? View.VISIBLE : View.GONE);
        
        // Show/hide chat for drawer
        binding.chatContainer.setVisibility(isDrawing ? View.GONE : View.VISIBLE);
        
        // Update instructions
        if (isDrawing) {
            binding.tvInstruction.setText(R.string.your_turn_to_draw);
        } else {
            binding.tvInstruction.setText(R.string.guess_the_word);
        }
    }
    
    private void updateScores(Game game) {
        if (game.getPlayerScores() != null) {
            List<PlayerScore> playerScores = convertMapToPlayerScores(game.getPlayerScores());
            scoreAdapter.updateScores(playerScores);
        }
    }
    
    /**
     * Converts a map of user IDs to scores into a list of PlayerScore objects
     * @param scoreMap Map of user IDs to scores
     * @return List of PlayerScore objects
     */
    private List<PlayerScore> convertMapToPlayerScores(Map<String, Float> scoreMap) {
        List<PlayerScore> result = new ArrayList<>();
        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            // Create a placeholder User object with just the username
            User user = new User();
            user.setUserId(entry.getKey());
            user.setUsername("Player " + entry.getKey()); // Placeholder username
            
            // Try to get the actual user from the game if available
            Game game = drawingViewModel.getCurrentGame().getValue();
            if (game != null && game.getPlayers() != null) {
                // Try to find the user in the players list
                for (User player : game.getPlayers()) {
                    if (player.getUserId().equals(entry.getKey())) {
                        user = player;
                        break;
                    }
                }
            }
            
            // Create PlayerScore object and add to result list
            PlayerScore playerScore = new PlayerScore(user, entry.getValue().intValue());
            result.add(playerScore);
        }
        return result;
    }
    
    /**
     * Determines if the current user is the drawer for the current round
     * @return true if the current user is the drawer, false otherwise
     */
    private boolean isDrawingTurn() {
        // Get current game state
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null) {
            Log.d(TAG, "❌ isDrawingTurn: Game is null");
            return false;
        }
        
        // Check if current user is available
        if (currentUser == null) {
            Log.d(TAG, "⚠️ isDrawingTurn: Current user is null");
            return false;
        }
        
        // CRITICAL FIX: First check if currentDrawerId is set and matches current user
        // This is the most reliable way to determine the drawer
        if (game.getCurrentDrawerId() != null && game.getCurrentDrawerId().equals(currentUser.getUserId())) {
            // If the IDs match but the drawer object is null or has placeholder name, fix it
            if (game.getCurrentDrawer() == null || "You".equals(game.getCurrentDrawer().getUsername())) {
                Log.d(TAG, "🔄 Setting current user as drawer based on matching IDs");
                game.setCurrentDrawer(currentUser);
                drawingViewModel.updateCurrentGame(game);
            }
            return true;
        }
        
        // If currentDrawerId doesn't match but drawer object exists and matches current user
        if (game.getCurrentDrawer() != null && 
            game.getCurrentDrawer().getUserId() != null && 
            game.getCurrentDrawer().getUserId().equals(currentUser.getUserId())) {
            
            // Fix the inconsistency by updating the drawer ID
            game.setCurrentDrawerId(currentUser.getUserId());
            Log.d(TAG, "🔄 Fixed inconsistent drawer ID to match current user");
            drawingViewModel.updateCurrentGame(game);
            return true;
        }
        
        // Handle the "You" placeholder case
        if (game.getCurrentDrawer() != null && "You".equals(game.getCurrentDrawer().getUsername())) {
            Log.d(TAG, "🔄 Fixing placeholder drawer name 'You' - this is the current user");
            game.getCurrentDrawer().setUsername(currentUser.getUsername());
            game.getCurrentDrawer().setUserId(currentUser.getUserId());
            game.setCurrentDrawerId(currentUser.getUserId());
            drawingViewModel.updateCurrentGame(game);
            return true;
        }
        
        // If no drawer is set at all, we need to recover
        if (game.getCurrentDrawer() == null || game.getCurrentDrawerId() == null) {
            Log.d(TAG, "⚠️ No drawer set, checking if we should recover");
            
            // Check if we're in a state where we should have a drawer
            if (game.getGameState() == Game.GameState.ACTIVE || 
                game.getGameState() == Game.GameState.DRAWING) {
                
                // Try to recover by using the first player or current user
                if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                    // Find if current user is in the players list
                    boolean currentUserInPlayers = false;
                    for (User player : game.getPlayers()) {
                        if (player.getUserId().equals(currentUser.getUserId())) {
                            currentUserInPlayers = true;
                            break;
                        }
                    }
                    
                    // If current user is in players, set them as drawer for this device
                    if (currentUserInPlayers) {
                        game.setCurrentDrawer(currentUser);
                        game.setCurrentDrawerId(currentUser.getUserId());
                        Log.d(TAG, "✅ Set current user as drawer: " + currentUser.getUsername());
                        drawingViewModel.updateCurrentGame(game);
                        return true;
                    } else {
                        // Otherwise use first player
                        User firstPlayer = game.getPlayers().get(0);
                        game.setCurrentDrawer(firstPlayer);
                        game.setCurrentDrawerId(firstPlayer.getUserId());
                        Log.d(TAG, "✅ Set first player as drawer: " + firstPlayer.getUsername());
                        drawingViewModel.updateCurrentGame(game);
                        return currentUser.getUserId().equals(firstPlayer.getUserId());
                    }
                } else if (currentUser != null) {
                    // If no players list, use current user as fallback
                    game.setCurrentDrawer(currentUser);
                    game.setCurrentDrawerId(currentUser.getUserId());
                    Log.d(TAG, "✅ Set current user as drawer fallback: " + currentUser.getUsername());
                    drawingViewModel.updateCurrentGame(game);
                    return true;
                }
            }
        }
        
        // Standard case - check if current user ID matches drawer ID
        boolean isDrawer = currentUser.getUserId().equals(game.getCurrentDrawerId());
        Log.d(TAG, "❓ isDrawingTurn: Is current user the drawer? " + isDrawer + 
              " (User: " + currentUser.getUsername() + ", Drawer ID: " + 
              (game.getCurrentDrawer() != null ? game.getCurrentDrawer().getUsername() : "null") + ")");
        return isDrawer;
    }
    
    private void updateDrawingPermissions() {
        // Get current game state first
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null) {
            Log.e(TAG, "Cannot update drawing permissions - game is null");
            
            // Disable all drawing functionality when game is null
            disableAllDrawingControls();
            return;
        }
        
        // CRITICAL FIX: Ensure we have a valid current user
        if (currentUser == null) {
            Log.e(TAG, "Cannot update drawing permissions - current user is null");
            disableAllDrawingControls();
            return;
        }
        
        // Use the improved isDrawingTurn method to determine if current user is the drawer
        boolean isDrawer = isDrawingTurn();
        Log.d(TAG, "🎨 updateDrawingPermissions: Setting drawing enabled = " + isDrawer);
        
        // CRITICAL FIX: Ensure drawing view is properly configured
        // First disable everything to ensure a clean state
        binding.drawingView.setEnabled(false);
        binding.drawingView.setInteractionEnabled(false);
        
        // Then enable only if user is the drawer
        if (isDrawer) {
            binding.drawingView.setEnabled(true);
            binding.drawingView.setInteractionEnabled(true);
            Log.d(TAG, "✅ Drawing interaction enabled for current user");
        } else {
            Log.d(TAG, "❌ Drawing interaction disabled - user is not the drawer");
        }
        
        // Update UI visibility based on drawer status
        updateDrawingControlsVisibility(isDrawer);
        
        // Update UI to show who's drawing and what word to draw/guess
        updateDrawerAndWordDisplay(game, isDrawer);
        
        // Force redraw to ensure drawing view responds correctly
        binding.drawingView.invalidate();
        Log.d("GameFragment", "Drawing permissions updated: " + (isDrawer ? "You can draw" : "You cannot draw"));
    }
    
    /**
     * Helper method to disable all drawing controls
     */
    private void disableAllDrawingControls() {
        binding.drawingView.setEnabled(false);
        binding.drawingView.setInteractionEnabled(false);
        binding.drawingControlsContainer.setVisibility(View.GONE);
        binding.colorPalette.setVisibility(View.GONE);
        binding.btnClearDrawing.setVisibility(View.GONE);
        binding.brushTypeScrollView.setVisibility(View.GONE);
        binding.switchPressureSensitivity.setVisibility(View.GONE);
    }
    
    /**
     * Helper method to update drawing controls visibility
     * @param isDrawer Whether the current user is the drawer
     */
    private void updateDrawingControlsVisibility(boolean isDrawer) {
        // Show/hide drawing controls based on drawer status
        int visibility = isDrawer ? View.VISIBLE : View.GONE;
        
        binding.drawingControlsContainer.setVisibility(visibility);
        binding.colorPalette.setVisibility(visibility);
        binding.btnClearDrawing.setVisibility(visibility);
        binding.brushTypeScrollView.setVisibility(visibility);
        binding.switchPressureSensitivity.setVisibility(visibility);
        
        // Always show chat for both drawer and guessers
        binding.chatContainer.setVisibility(View.VISIBLE);
    }
    
    /**
     * Helper method to update drawer and word display
     * @param game Current game state
     * @param isDrawer Whether the current user is the drawer
     */
    private void updateDrawerAndWordDisplay(Game game, boolean isDrawer) {
        if (game.getCurrentDrawer() != null) {
            String drawerName = game.getCurrentDrawer().getUsername();
            
            // Update drawer status text
            if (isDrawer) {
                binding.tvCurrentDrawer.setText(getString(R.string.your_turn_to_draw));
                
                // Show the word to draw if available
                if (game.getCurrentWord() != null && !game.getCurrentWord().isEmpty()) {
                    binding.tvWord.setText(String.format("Draw: %s", game.getCurrentWord()));
                    binding.tvWord.setVisibility(View.VISIBLE);
                    Log.d(TAG, "✏️ Showing word to drawer: " + game.getCurrentWord());
                } else {
                    // If no word is set, show a placeholder
                    binding.tvWord.setText("Waiting for word...");
                    binding.tvWord.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "✏️ Setting UI for drawer: " + currentUser.getUsername());
            } else {
                binding.tvCurrentDrawer.setText(String.format(Locale.getDefault(), "%s is drawing", drawerName));
                binding.tvWord.setText(getString(R.string.guess_the_word));
                binding.tvWord.setVisibility(View.VISIBLE);
                Log.d(TAG, "👁️ Setting UI for guesser, drawer is: " + drawerName);
            }
        } else {
            // Fallback if drawer is still null
            binding.tvCurrentDrawer.setText(R.string.waiting_for_players);
            binding.tvWord.setText(R.string.guess_the_word);
            binding.tvWord.setVisibility(View.VISIBLE);
            Log.w(TAG, "⚠️ No drawer available for UI update");
        }
    }
    
    // Track the last timer update timestamp to prevent duplicate timer starts
    private long lastTimerUpdateTimestamp = 0;
    private static final long TIMER_UPDATE_THRESHOLD_MS = 500; // Minimum time between timer updates
    
    /**
     * Stops the current round timer if it's running
     */
    private void stopRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
            Log.d(TAG, "⏱️ Round timer stopped");
        }
    }
    
    /**
     * Starts the round timer with the specified remaining time
     * @param remainingTimeSeconds Time remaining in seconds, or 0 to use game's round duration
     */
    private void startRoundTimer(int remainingTimeSeconds) {
        // Prevent rapid timer restarts by checking timestamp
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTimerUpdateTimestamp < TIMER_UPDATE_THRESHOLD_MS) {
            Log.d(TAG, "⏱️ Ignoring timer restart request - too soon after previous update");
            return;
        }
        lastTimerUpdateTimestamp = currentTime;
        
        // Cancel any existing timer
        stopRoundTimer();
        
        // Get the current game state
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null) {
            Log.e(TAG, "Cannot start timer - game is null");
            return;
        }
        
        // Check if game is in active state
        if (game.getGameState() != Game.GameState.ACTIVE && game.getGameState() != Game.GameState.DRAWING) {
            Log.d(TAG, "⏱️ Not starting timer - game state is " + game.getGameState());
            return;
        }
        
        // Get the time remaining from game state (in seconds)
        int timeRemainingSeconds = remainingTimeSeconds;
        if (timeRemainingSeconds <= 0) {
            // Use round duration if available, otherwise default to 60 seconds
            timeRemainingSeconds = game.getRoundDurationSeconds() > 0 ? 
                game.getRoundDurationSeconds() : 60;
            
            Log.d(TAG, "⏱️ Using round duration: " + timeRemainingSeconds + " seconds");
            
            // Update the game's remaining time
            game.setRemainingTime(timeRemainingSeconds);
            drawingViewModel.updateCurrentGame(game);
        }
        
        // Convert to milliseconds for CountDownTimer
        final long timeRemainingMillis = timeRemainingSeconds * 1000L;
        final int finalTimeRemainingSeconds = timeRemainingSeconds;
        
        Log.d(TAG, "⏱️ Starting timer with " + timeRemainingSeconds + " seconds");
        
        // Create and start a new timer
        roundTimer = new CountDownTimer(timeRemainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isAdded() || getActivity() == null) {
                    Log.d(TAG, "⏱️ Fragment not attached, cancelling timer");
                    cancel();
                    return;
                }
                
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                
                // Update the game's remaining time
                Game currentGame = drawingViewModel.getCurrentGame().getValue();
                if (currentGame != null) {
                    currentGame.setRemainingTime(secondsRemaining);
                }
                
                // Update the timer text
                binding.tvTimer.setText(String.valueOf(secondsRemaining));
                
                // Change color based on time remaining
                if (secondsRemaining <= 10) {
                    // Red for last 10 seconds
                    binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorError));
                } else if (secondsRemaining <= 30) {
                    // Yellow for 11-30 seconds
                    binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary));
                } else {
                    // Green for > 30 seconds
                    binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary));
                }
                
                // Reduce logging frequency to avoid log spam
                if (secondsRemaining % 5 == 0 || secondsRemaining <= 10) {
                    Log.d(TAG, "⏱️ Timer tick: " + secondsRemaining + "s remaining");
                }
            }
            
            @Override
            public void onFinish() {
                if (!isAdded() || getActivity() == null) {
                    return;
                }
                
                // Timer reached zero
                binding.tvTimer.setText("0");
                binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorError));
                
                Log.d(TAG, "⏱️ Timer finished - round complete");
                
                // Notify server to advance to next round
                Game currentGame = drawingViewModel.getCurrentGame().getValue();
                if (currentGame != null) {
                    gameRepository.advanceToNextRound(currentGame.getGameId());
                    
                    // Update local game state to reflect round completion
                    currentGame.setGameState(Game.GameState.FINISHED);
                    currentGame.setRemainingTime(0);
                    drawingViewModel.updateCurrentGame(currentGame);
                }
            }
        }.start();
        
        Log.d(TAG, "⏱️ Round timer started");
    }
    
    // Track the last processed game state message to prevent duplicate processing
    private String lastProcessedGameStateId = null;
    
    @Override
    public void onGameStateChanged(GameStateMessage message) {
        // This is called from WebSocketService when game updates are received
        if (message == null || message.getGamePayload() == null) {
            Log.w(TAG, "Received null game state message or payload");
            return;
        }
        
        // Generate a more reliable message ID based on content to detect duplicates
        String messageId = message.getGamePayload().getCurrentDrawer().getUsername() + "-" +
                          message.getGamePayload().getCurrentRound() + "-" +
                          message.getGamePayload().getTimeRemainingSeconds() + "-" +
                          message.getGamePayload().getStatus();
        
        // Check if we've already processed this exact message
        if (messageId.equals(lastProcessedGameStateId)) {
            Log.d(TAG, "🔄 Skipping duplicate game state message");
            return;
        }
        
        // Store this message ID as the last processed
        lastProcessedGameStateId = messageId;
        
        Log.d(TAG, "Game state update received: " + 
               (message.getGamePayload().getEvent() != null ? message.getGamePayload().getEvent() : "NO_EVENT"));
        
        // Force UI update on main thread to ensure visibility
        if (getActivity() == null) {
            Log.e(TAG, "Cannot update game state - activity is null");
            return;
        }
            
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    Log.e(TAG, "Cannot update game state - fragment not added");
                    return;
                }
                
                // Force show game content
                binding.progressBarGame.setVisibility(View.GONE);
                binding.gameContentContainer.setVisibility(View.VISIBLE);
                
                // Ensure we have a valid game object
                Game currentGame = drawingViewModel.getCurrentGame().getValue();
                if (currentGame == null) {
                    // Create a new game object if needed
                    currentGame = new Game(gameId, "", 3, 60);
                    Log.d(TAG, "🆕 Created new game object in onGameStateChanged");
                    
                    // Initialize with current user if available
                    if (currentUser != null) {
                        List<User> players = new ArrayList<>();
                        players.add(currentUser);
                        currentGame.setPlayers(players);
                    }
                }
                
                // Log detailed game state information for debugging
                Log.d(TAG, "📊 Game state update: " + 
                      (message.getGamePayload().getEvent() != null ? message.getGamePayload().getEvent() : "NO_EVENT") + 
                      ", Round: " + message.getGamePayload().getCurrentRound() + "/" + 
                      message.getGamePayload().getMaxRounds() + 
                      ", Time: " + message.getGamePayload().getTimeRemainingSeconds() + "s");
                
                // Update game state from message
                if (message.getGamePayload().getEvent() != null) {
                    try {
                        Game.GameState newState = Game.GameState.valueOf(message.getGamePayload().getEvent());
                        Game.GameState oldState = currentGame.getGameState();
                        
                        // Check if we're transitioning to a new round
                        boolean isNewRound = (oldState == Game.GameState.FINISHED && 
                                             newState == Game.GameState.ACTIVE) ||
                                            (message.getGamePayload().getCurrentRound() > currentGame.getCurrentRound());
                        
                        if (isNewRound) {
                            // Clear the drawing for the new round
                            binding.drawingView.clearDrawing();
                            Log.d(TAG, "🔄 New round detected - clearing drawing canvas");
                            
                            // Add system message about new round
                            String roundMessage = "Round " + message.getGamePayload().getCurrentRound() + 
                                                 " started!";
                            gameRepository.addSystemChatMessage(roundMessage);
                        }
                        
                        // Update the game state
                        currentGame.setGameState(newState);
                        
                        // Handle timer based on game state
                        if (newState == Game.GameState.ACTIVE || newState == Game.GameState.DRAWING) {
                            // Start or restart timer when game is active
                            int remainingTime = message.getGamePayload().getTimeRemainingSeconds();
                            startRoundTimer(remainingTime);
                        } else if (newState == Game.GameState.FINISHED || 
                                   newState == Game.GameState.WAITING) {
                            // Cancel timer when round is complete or game is over
                            stopRoundTimer();
                        }
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Invalid game state received: " + message.getGamePayload().getEvent());
                    }
                }
                    
                    // Update current round
                if (message.getGamePayload().getCurrentRound() > 0) {
                    currentGame.setCurrentRound(message.getGamePayload().getCurrentRound());
                } else if (currentGame.getCurrentRound() <= 0) {
                    currentGame.setCurrentRound(1); // Default to round 1 if not set
                }
                
                // Update max rounds
                if (message.getGamePayload().getMaxRounds() > 0) {
                    currentGame.setNumRounds(message.getGamePayload().getMaxRounds());
                } else if (currentGame.getNumRounds() <= 0) {
                    currentGame.setNumRounds(3); // Default to 3 rounds if not set
                }
                
                // Update current drawer if provided
                if (message.getGamePayload().getCurrentDrawer() != null) {
                    User drawerFromMessage = message.getGamePayload().getCurrentDrawer();
                    
                    // Fix for drawer with display name "You"
                    if ("You".equals(drawerFromMessage.getUsername()) && drawerFromMessage.getUserId() != null) {
                        // This is likely the current user, fix the username
                        Log.d(TAG, "⚠️ Fixing drawer with name 'You' - setting proper user data");
                        
                        // Check if this is the current user
                        if (currentUser != null && currentUser.getUserId().equals(drawerFromMessage.getUserId())) {
                            // Use the current user object instead
                            currentGame.setCurrentDrawer(currentUser);
                            Log.d(TAG, "✅ Set current user as drawer: " + currentUser.getUsername());
                        } else {
                            // Try to find the user in the players list
                            boolean foundUser = false;
                            if (currentGame.getPlayers() != null) {
                                for (User player : currentGame.getPlayers()) {
                                    if (player.getUserId().equals(drawerFromMessage.getUserId())) {
                                        currentGame.setCurrentDrawer(player);
                                        Log.d(TAG, "✅ Found player in list and set as drawer: " + player.getUsername());
                                        foundUser = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (!foundUser) {
                                // If we can't find the user, use the message drawer but fix the username
                                drawerFromMessage.setUsername("Player " + drawerFromMessage.getUserId().substring(0, 5));
                                currentGame.setCurrentDrawer(drawerFromMessage);
                                Log.d(TAG, "⚠️ Using drawer from message with fixed name: " + drawerFromMessage.getUsername());
                            }
                        }
                    } else {
                        // Normal case - use the drawer from the message
                        currentGame.setCurrentDrawer(drawerFromMessage);
                        Log.d(TAG, "✅ Set drawer from message: " + drawerFromMessage.getUsername());
                    }
                    
                    // Always ensure the drawer ID is set correctly
                    if (currentGame.getCurrentDrawer() != null) {
                        currentGame.setCurrentDrawerId(currentGame.getCurrentDrawer().getUserId());
                    }
                }
                
                // Update the game in the ViewModel
                drawingViewModel.updateCurrentGame(currentGame);
                
                // Force update game state for timer
                if (message.getGamePayload().getTimeRemainingSeconds() > 0) {
                    // Start a new timer with the remaining time
                    stopRoundTimer();
                    int remainingTime = message.getGamePayload().getTimeRemainingSeconds();
                    startRoundTimer(remainingTime);
                    }
                    
                    // Update round display using current round and max rounds
                    int currentRound = message.getGamePayload().getCurrentRound() > 0 ? 
                            message.getGamePayload().getCurrentRound() :
                            currentGame.getCurrentRound();
                    
                    int maxRounds = message.getGamePayload().getMaxRounds() > 0 ? 
                            message.getGamePayload().getMaxRounds() :
                            currentGame.getNumRounds();
                    
                    binding.tvRound.setText("Round: " + currentRound + "/" + maxRounds);
                    
                    // Update word to guess if provided
                    if (message.getGamePayload().getWordToGuess() != null && !message.getGamePayload().getWordToGuess().isEmpty()) {
                        // Save the word in the game object
                        currentGame.setCurrentWord(message.getGamePayload().getWordToGuess());
                        
                        // Update UI based on whether user is drawer or guesser
                        if (isDrawingTurn()) {
                            binding.tvWord.setText(String.format("Draw: %s", message.getGamePayload().getWordToGuess()));
                            binding.tvWord.setVisibility(View.VISIBLE);
                            Log.d(TAG, "✏️ Setting word for drawer: " + message.getGamePayload().getWordToGuess());
                        } else {
                            binding.tvWord.setText(getString(R.string.guess_the_word));
                            binding.tvWord.setVisibility(View.VISIBLE);
                            Log.d(TAG, "👁️ Setting UI for guesser");
                        }
                    } else if (currentGame.getCurrentWord() != null && !currentGame.getCurrentWord().isEmpty()) {
                        // Use existing word if available
                        if (isDrawingTurn()) {
                            binding.tvWord.setText(String.format("Draw: %s", currentGame.getCurrentWord()));
                            binding.tvWord.setVisibility(View.VISIBLE);
                            Log.d(TAG, "✏️ Using existing word for drawer: " + currentGame.getCurrentWord());
                        } else {
                            binding.tvWord.setText(getString(R.string.guess_the_word));
                            binding.tvWord.setVisibility(View.VISIBLE);
                            Log.d(TAG, "👁️ Setting UI for guesser with existing word");
                        }
                    } else {
                        // No word available, set a default one for testing
                        String defaultWord = "apple";
                        currentGame.setCurrentWord(defaultWord);
                        if (isDrawingTurn()) {
                            binding.tvWord.setText(String.format("Draw: %s", defaultWord));
                            Log.d(TAG, "⚠️ Using default word for drawer: " + defaultWord);
                        } else {
                            binding.tvWord.setText(getString(R.string.guess_the_word));
                            Log.d(TAG, "⚠️ Using default word state for guesser");
                        }
                        binding.tvWord.setVisibility(View.VISIBLE);
                    }                  
                    // Update drawing permissions and UI
                    updateDrawingPermissions();
                    
                    // Log detailed game state for debugging
                    Log.d("GameFragment", "Updated game state - Round: " + currentRound + "/" + maxRounds + 
                            ", Drawing tools: " + (isDrawingTurn() ? "Visible" : "Hidden"));
                });
            }
    
    @Override
    public void onError(String errorMessage) {
        // Handle WebSocket error
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onChatMessageReceived(ChatMessage chatMessage) {
        // Handle incoming chat messages
        if (chatMessage == null) {
            Log.e(TAG, "Received null chat message");
            return;
        }

        Log.d(TAG, "💬 Chat message received: " + chatMessage.getMessage() +
                " from " + (chatMessage.getSender() != null ? chatMessage.getSender().getUsername() : "unknown"));

        // Save message in ViewModel to ensure it persists across configuration changes
        drawingViewModel.addChatMessage(chatMessage);

        // Update UI on main thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (isAdded() && binding != null) {
                    // Add the message to the chat adapter
                    if (chatAdapter != null) {
                        try {
                            // Get current messages, add the new one, and update the adapter
                            List<ChatMessage> currentMessages = new ArrayList<>(chatAdapter.getMessages());

                            // Check if message already exists to avoid duplicates
                            boolean isDuplicate = false;
                            for (ChatMessage existingMsg : currentMessages) {
                                if (existingMsg.getMessageId() != null &&
                                        existingMsg.getMessageId().equals(chatMessage.getMessageId())) {
                                    isDuplicate = true;
                                    Log.d(TAG, "⚠️ Duplicate message detected, skipping: " + chatMessage.getMessage());
                                    break;
                                }
                            }

                            if (!isDuplicate) {
                                // Add the new message
                                currentMessages.add(chatMessage);

                                // Update the adapter with the new list
                                chatAdapter.updateMessages(currentMessages);

                                // Force adapter to update
                                chatAdapter.notifyDataSetChanged();

                                // Scroll to bottom
                                if (chatAdapter.getItemCount() > 0) {
                                    binding.recyclerViewChat.post(() -> {
                                        binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                                    });
                                }

                                Log.d(TAG, "✅ Chat message added to adapter: " + chatMessage.getMessage());

                                // Check if this is a correct guess
                                checkForCorrectGuess(chatMessage);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating chat adapter: " + e.getMessage(), e);
                        }
                    } else {
                        Log.e(TAG, "❌ Chat adapter is null");

                        // Try to initialize chat adapter if it's null
                        initializeChatAdapter();
                    }
                }
            });
        }
    }

    /**
     * Initializes the chat adapter if it's null
     */
    private void initializeChatAdapter() {
        if (chatAdapter == null && binding != null) {
            chatAdapter = new ChatAdapter(new ArrayList<>(), currentUser);
            binding.recyclerViewChat.setAdapter(chatAdapter);
            Log.d(TAG, "✅ Chat adapter initialized");
        }
    }

    /**
     * Checks if a chat message contains a correct guess of the current word
     */
    private void checkForCorrectGuess(ChatMessage chatMessage) {
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null || game.getCurrentWord() == null || chatMessage.getSender() == null) {
            return;
        }

        // Only check guesses from non-drawers
        if (chatMessage.getSender().getUserId().equals(game.getCurrentDrawerId())) {
            return;
        }

        // Check if message contains the correct word (case insensitive)
        String message = chatMessage.getMessage().toLowerCase();
        String word = game.getCurrentWord().toLowerCase();

        if (message.contains(word)) {
            // Correct guess!
            String correctGuessMsg = chatMessage.getSender().getUsername() + " guessed the word!";
            gameRepository.addSystemChatMessage(correctGuessMsg);
            Log.d(TAG, "🎉 Correct guess detected: " + correctGuessMsg);
        }
    }

    /**
     * Checks if the user's guess matches the current word (case-insensitive)
     * @param guess The user's guess
     * @return true if the guess is correct, false otherwise
     */
    private boolean checkIfCorrectGuess(String guess) {
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null || game.getCurrentWord() == null || guess == null) {
            return false;
        }

        String currentWord = game.getCurrentWord().toLowerCase().trim();
        String userGuess = guess.toLowerCase().trim();

        boolean isCorrect = currentWord.equals(userGuess);

        if (isCorrect) {
            Log.d(TAG, "🎯 Correct guess: '" + guess + "' matches '" + game.getCurrentWord() + "'");

            if (getContext() != null) {
                Toast.makeText(getContext(), "Correct! The word was '" + game.getCurrentWord() + "'", Toast.LENGTH_SHORT).show();
            }

            drawingViewModel.sendCorrectGuess(gameId);
        }

        return isCorrect;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel timer
        stopRoundTimer();

        // Remove WebSocket callback
        drawingViewModel.setGameUpdateCallback(null);
        binding = null;
    }
}
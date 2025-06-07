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
        chatAdapter = new ChatAdapter(new ArrayList<>());
        binding.recyclerViewChat.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewChat.setAdapter(chatAdapter);
        
        // Setup score adapter
        scoreAdapter = new PlayerScoreAdapter(new ArrayList<>());
        binding.recyclerViewScores.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewScores.setAdapter(scoreAdapter);
        
        // Setup player adapter
        playerAdapter = new com.example.drawit_app.view.adapter.PlayerAdapter(new ArrayList<>());
        if (binding.recyclerViewPlayers != null) {
            binding.recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.recyclerViewPlayers.setAdapter(playerAdapter);
        } else {
            Log.e(TAG, "recyclerViewPlayers is null in GameFragment");
        }
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
            if (!isDrawingTurn() && paths != null) {
                binding.drawingView.setPathsFromJson(paths);
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
                        Toast.makeText(getContext(), "WebSocket error: " + errorMsg, Toast.LENGTH_SHORT).show();
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
        
        // CRITICAL FIX: Ensure current word is set
        if (game.getCurrentWord() == null || game.getCurrentWord().isEmpty()) {
            game.setCurrentWord("apple"); // Default word as fallback
            Log.d(TAG, "📝 Setting default word to 'apple'");
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
            if (roundTimer != null) {
                roundTimer.cancel();
            }
            startRoundTimer();
            Log.d(TAG, "⏱️ Started round timer with " + game.getRemainingTime() + " seconds");
        } else if (game.getGameState() == Game.GameState.ACTIVE || 
                  game.getGameState() == Game.GameState.DRAWING) {
            // If game is active but no time is set, use a default time
            if (roundTimer != null) {
                roundTimer.cancel();
            }
            startRoundTimer();
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
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null) {
            Log.d(TAG, "❌ isDrawingTurn: Game is null");
            return false;
        }

        if (game.getCurrentDrawer() == null) {
            Log.d(TAG, "⚠️ isDrawingTurn: Current drawer is null");

            // Try to recover by setting first player as drawer
            if (game.getPlayers() != null && !game.getPlayers().isEmpty()) {
                User firstPlayer = game.getPlayers().get(0);
                game.setCurrentDrawer(firstPlayer);
                game.setCurrentDrawerId(firstPlayer.getUserId());
                Log.d(TAG, "✅ isDrawingTurn: Set first player as drawer: " + firstPlayer.getUsername());
            } else {
                return false;
            }
        }
        
        // Fix for placeholder username "You" - this indicates the current user is the drawer
        if ("You".equals(game.getCurrentDrawer().getUsername())) {
            Log.d(TAG, "🔄 Fixing placeholder drawer name 'You' - this is the current user");
            if (currentUser != null) {
                // Replace the placeholder with the actual current user
                game.getCurrentDrawer().setUsername(currentUser.getUsername());
                game.getCurrentDrawer().setUserId(currentUser.getUserId());
                return true;
            }
        }
        
        if (currentUser == null) {
            Log.d(TAG, "⚠️ isDrawingTurn: Current user is null");
            return false;
        }
        
        boolean isDrawer = currentUser.getUserId().equals(game.getCurrentDrawer().getUserId());
        Log.d(TAG, "❓ isDrawingTurn: Is current user the drawer? " + isDrawer + 
              " (User: " + currentUser.getUsername() + ", Drawer: " + game.getCurrentDrawer().getUsername() + ")");
        return isDrawer;
    }
    
    private void updateDrawingPermissions() {
        boolean isDrawer = isDrawingTurn();
        Log.d(TAG, "🎨 updateDrawingPermissions: Setting drawing enabled = " + isDrawer);
        
        // Update drawing view permissions
        binding.drawingView.setEnabled(isDrawer);
        binding.drawingView.setInteractionEnabled(isDrawer);
        
        // Show/hide drawing controls based on drawer status
        binding.drawingControlsContainer.setVisibility(isDrawer ? View.VISIBLE : View.GONE);
        binding.colorPalette.setVisibility(isDrawer ? View.VISIBLE : View.GONE);
        binding.btnClearDrawing.setVisibility(isDrawer ? View.VISIBLE : View.GONE);
        binding.brushTypeScrollView.setVisibility(isDrawer ? View.VISIBLE : View.GONE);
        binding.switchPressureSensitivity.setVisibility(isDrawer ? View.VISIBLE : View.GONE);
        
        // Always show chat for both drawer and guessers
        binding.chatContainer.setVisibility(View.VISIBLE);
        
        // Update UI to show who's drawing
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game != null && game.getCurrentDrawer() != null) {
            String drawerName = game.getCurrentDrawer().getUsername();
            if (isDrawer) {
                binding.tvCurrentDrawer.setText(getString(R.string.your_turn_to_draw));
                binding.tvWord.setText(String.format("Draw: %s", game.getCurrentWord()));
                Log.d(TAG, "✏️ Setting UI for drawer: " + currentUser.getUsername());
            } else {
                binding.tvCurrentDrawer.setText(String.format(Locale.getDefault(), "%s is drawing", drawerName));
                binding.tvWord.setText(getString(R.string.guess_the_word));
                Log.d(TAG, "👁️ Setting UI for guesser, drawer is: " + drawerName);
            }
        }
        
        // Force redraw to ensure drawing view responds correctly
        binding.drawingView.invalidate();
        Log.d("GameFragment", "Drawing permissions updated: " + (isDrawer ? "You can draw" : "You cannot draw"));
    }
    
    private void startRoundTimer() {
        // Cancel any existing timer
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
        
        // Get the current game state
        Game game = drawingViewModel.getCurrentGame().getValue();
        if (game == null) {
            Log.e(TAG, "Cannot start timer - game is null");
            return;
        }
        
        // Check if game is in active state
        if (game.getGameState() != Game.GameState.ACTIVE) {
            Log.d(TAG, "⏱️ Not starting timer - game state is " + game.getGameState());
            return;
        }
        
        // Get the time remaining from game state (in seconds)
        int timeRemainingSeconds = game.getRemainingTime();
        if (timeRemainingSeconds <= 0) {
            Log.e(TAG, "Invalid time remaining: " + timeRemainingSeconds + ", using default 60 seconds");
            timeRemainingSeconds = 60; // Default to 60 seconds if invalid
        }
        
        // Convert to milliseconds for CountDownTimer
        long timeRemainingMillis = timeRemainingSeconds * 1000L;
        
        // Create and start a new timer
        roundTimer = new CountDownTimer(timeRemainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                
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
                
                Log.d(TAG, "⏱️ Timer tick: " + secondsRemaining + "s remaining");
            }
            
            @Override
            public void onFinish() {
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
                    drawingViewModel.updateCurrentGame(currentGame);
                }
            }
        }.start();
        
        Log.d(TAG, "⏱️ Started round timer for " + timeRemainingSeconds + " seconds");
    }
    
    @Override
    public void onGameStateChanged(GameStateMessage message) {
        // This is called from WebSocketService when game updates are received
        if (message != null && message.getGamePayload() != null) {
            Log.d("GameFragment", "Game state update received: " + message.getGamePayload().getEvent());
            
            try {
                // Force UI update on main thread to ensure visibility
                requireActivity().runOnUiThread(() -> {
                    // Force show game content
                    binding.progressBarGame.setVisibility(View.GONE);
                    binding.gameContentContainer.setVisibility(View.VISIBLE);
                    
                    // Update the current game in the ViewModel to ensure all components have access to it
                    Game currentGame = drawingViewModel.getCurrentGame().getValue();
                    if (currentGame != null) {
                        // Log detailed game state information for debugging
                        Log.d(TAG, "📊 Game state update: " + 
                              (message.getGamePayload().getEvent() != null ? message.getGamePayload().getEvent() : "NO_EVENT") + 
                              ", Round: " + message.getGamePayload().getCurrentRound() + "/" + 
                              message.getGamePayload().getMaxRounds() + 
                              ", Time: " + message.getGamePayload().getTimeRemainingSeconds() + "s");
                        
                        // Update game state from message
                        if (message.getGamePayload().getEvent() != null) {
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
                            if (newState == Game.GameState.ACTIVE) {
                                // Start or restart timer when game is active
                                startRoundTimer();
                            } else if (newState == Game.GameState.FINISHED || 
                                       newState == Game.GameState.WAITING) {
                                // Cancel timer when round is complete or game is over
                                if (roundTimer != null) {
                                    roundTimer.cancel();
                                    roundTimer = null;
                                }
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
                    }
                    
                    // Force update game state for timer
                    if (message.getGamePayload().getTimeRemainingSeconds() > 0) {
                        // Cancel any existing timer
                        if (roundTimer != null) {
                            roundTimer.cancel();
                        }
                        
                        // Start a new timer
                        startRoundTimer();
                    }
                    
                    // Update round display using current round and max rounds
                    int currentRound = message.getGamePayload().getCurrentRound() > 0 ? 
                            message.getGamePayload().getCurrentRound() : 
                            (currentGame != null ? currentGame.getCurrentRound() : 1);
                    
                    int maxRounds = message.getGamePayload().getMaxRounds() > 0 ? 
                            message.getGamePayload().getMaxRounds() : 
                            (currentGame != null ? currentGame.getNumRounds() : 3);
                    
                    binding.tvRound.setText("Round: " + currentRound + "/" + maxRounds);
                    
                    // Update word to guess if provided
                    if (message.getGamePayload().getWordToGuess() != null && !message.getGamePayload().getWordToGuess().isEmpty()) {
                        binding.tvWordToGuess.setText("Guess the word!");
                        binding.tvWordToGuess.setVisibility(View.VISIBLE);
                    }
                    
                    // Update drawing permissions and UI
                    updateDrawingPermissions();
                    
                    // Log detailed game state for debugging
                    Log.d("GameFragment", "Updated game state - Round: " + currentRound + "/" + maxRounds + 
                            ", Drawing tools: " + (isDrawingTurn() ? "Visible" : "Hidden"));
                });
            } catch (Exception e) {
                Log.e("GameFragment", "Error processing game update: " + e.getMessage(), e);
            }
        }
    }
    
    @Override
    public void onError(String errorMessage) {
        // Handle WebSocket error
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
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
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        
        // Remove WebSocket callback
        drawingViewModel.setGameUpdateCallback(null);
        binding = null;
    }
}

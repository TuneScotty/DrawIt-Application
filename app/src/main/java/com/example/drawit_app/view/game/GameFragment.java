package com.example.drawit_app.view.game;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.example.drawit_app.network.WebSocketService;
import com.example.drawit_app.network.message.GameStateMessage;
import com.example.drawit_app.network.message.LobbyStateMessage;
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
        setupListeners();
        observeViewModel();
        
        // Set WebSocket callback
        drawingViewModel.setGameUpdateCallback(this);

        Log.d("GameFragment", "Joining game with ID: " + gameId);
        
        // Join game with a timeout to ensure we don't get stuck if WebSocket fails
        drawingViewModel.joinGame(gameId);
        
        // Add a safety timeout to check if we've received game data
        new Handler().postDelayed(() -> {
            if (isAdded() && binding != null && drawingViewModel.getCurrentGame().getValue() == null) {
                // If after 5 seconds we still don't have game data, show an error
                binding.progressBarGame.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Failed to load game data. Please try again.", Toast.LENGTH_SHORT).show();
                Log.e("GameFragment", "Timed out waiting for game data");
                // Go back to the lobby
                navController.navigateUp();
            }
        }, 5000);
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
    }
    
    private void setupListeners() {
        // Send chat message button click listener
        binding.btnSendMessage.setOnClickListener(v -> {
            String message = binding.etChatMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                drawingViewModel.sendChatMessage(gameId, message);
                binding.etChatMessage.setText("");
            }
        });
        
        // Clear drawing button click listener
        binding.btnClearDrawing.setOnClickListener(v -> {
            if (isDrawingTurn()) {
                binding.drawingView.clearDrawing();
                drawingViewModel.updateDrawingPath(gameId, binding.drawingView.getPathsAsJson());
            }
        });
        
        // Drawing view path update listener
        binding.drawingView.setOnPathUpdateListener(paths -> {
            if (isDrawingTurn()) {
                drawingViewModel.updateDrawingPath(gameId, paths);
            }
        });
    }
    
    private void observeViewModel() {
        // Observe current user
        drawingViewModel.getCurrentUser().observe(getViewLifecycleOwner(), user -> {
            currentUser = user;
            updateGameUI();
        });
        
        // Observe current game
        drawingViewModel.getCurrentGame().observe(getViewLifecycleOwner(), game -> {
            if (game != null) {
                updateGameState(game);
                updateScores(game);
                
                // Start timer if needed
                if (game.getState() == Game.GameState.DRAWING && game.getRemainingTime() > 0) {
                    startRoundTimer(game.getRemainingTime());
                }
            }
        });
        
        // Observe chat messages
        drawingViewModel.getChatMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages instanceof List<?>) {
                // Check if we need to convert from strings to ChatMessage objects
                if (!messages.isEmpty() && messages.get(0) instanceof String) {
                    List<String> stringMessages = (List<String>) messages;
                    List<ChatMessage> chatMessages = new ArrayList<>();
                    for (String msg : stringMessages) {
                        // Create a system message for each string
                        ChatMessage chatMsg = new ChatMessage(null, msg, ChatMessage.MessageType.SYSTEM_MESSAGE);
                        chatMessages.add(chatMsg);
                    }
                    chatAdapter.updateMessages(chatMessages);
                } else if (messages != null) {
                    // Convert String messages to ChatMessage objects
                    List<ChatMessage> chatMessages = new ArrayList<>();
                    for (String message : messages) {
                        ChatMessage chatMsg = new ChatMessage();
                        chatMsg.setMessage(message);
                        chatMsg.setType(ChatMessage.MessageType.SYSTEM_MESSAGE);
                        chatMessages.add(chatMsg);
                    }
                    chatAdapter.updateMessages(chatMessages);
                }
                // Scroll to bottom
                binding.recyclerViewChat.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
        
        // Observe drawing paths
        drawingViewModel.getDrawingPaths().observe(getViewLifecycleOwner(), paths -> {
            if (!isDrawingTurn() && paths != null) {
                binding.drawingView.setPathsFromJson(paths);
            }
        });
        
        // Observe game over event
        drawingViewModel.getGameOverEvent().observe(getViewLifecycleOwner(), isGameOver -> {
            if (isGameOver) {
                // Navigate to results screen
                Bundle args = new Bundle();
                args.putString("gameId", gameId);
                navController.navigate(R.id.action_gameFragment_to_gameResultsFragment, args);
            }
        });
        
        // Observe error messages
        drawingViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateGameState(Game game) {
        Log.d("GameFragment", "Game state updated: " + game.getGameId() + ", state: " + game.getState());

        // First, hide loading indicator and show game content when we first get game data
        if (binding.progressBarGame.getVisibility() == View.VISIBLE) {
            binding.progressBarGame.setVisibility(View.GONE);
            binding.gameContentContainer.setVisibility(View.VISIBLE);
            Log.d("GameFragment", "Game content is now visible");
        }

        // Update round and game info
        binding.tvRound.setText(getString(R.string.round_format, game.getCurrentRound(), game.getTotalRounds()));
        
        // Update word display
        String word = game.getCurrentWord();
        if (isDrawingTurn()) {
            // Show full word for drawer
            binding.tvWord.setText(word);
        } else if (word != null) {
            // Show word length with underscores for guessers
            StringBuilder hiddenWord = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                if (word.charAt(i) == ' ') {
                    hiddenWord.append(" ");
                } else {
                    hiddenWord.append("_");
                }
            }
            binding.tvWord.setText(hiddenWord.toString());
        }
        
        // Update current drawer
        User drawer = game.getCurrentDrawer();
        if (drawer != null) {
            binding.tvCurrentDrawer.setText(getString(R.string.current_drawer_format, drawer.getUsername()));
        }
        
        // Update UI based on turn
        updateGameUI();
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
            if (game != null && game.getCurrentDrawer() != null && 
                game.getCurrentDrawer().getUserId().equals(entry.getKey())) {
                user = game.getCurrentDrawer();
            }
            
            // Create PlayerScore object and add to result list
            PlayerScore playerScore = new PlayerScore(user, entry.getValue().intValue());
            result.add(playerScore);
        }
        return result;
    }
    
    private boolean isDrawingTurn() {
        Game game = drawingViewModel.getCurrentGame().getValue();
        return game != null && currentUser != null && 
                game.getCurrentDrawer() != null &&
                currentUser.getUsername().equals(game.getCurrentDrawer().getUsername());
    }
    
    private void startRoundTimer(long seconds) {
        // Cancel existing timer
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        
        // Create new timer
        roundTimer = new CountDownTimer(seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update timer display
                long secondsRemaining = millisUntilFinished / 1000;
                binding.tvTimer.setText(String.format(Locale.getDefault(), "%d", secondsRemaining));
                
                // Change color when time is low
                if (secondsRemaining <= 10) {
                    binding.tvTimer.setTextColor(getResources().getColor(R.color.colorError));
                } else {
                    binding.tvTimer.setTextColor(getResources().getColor(R.color.colorPrimary));
                }
            }

            @Override
            public void onFinish() {
                binding.tvTimer.setText("0");
                binding.tvTimer.setTextColor(getResources().getColor(R.color.colorError));
            }
        }.start();
    }
    
    @Override
    public void onGameStateChanged(GameStateMessage message) {
        // This is called from WebSocketService when game updates are received
        if (message != null && message.getGamePayload() != null) {
            drawingViewModel.processGameUpdate(message.getGamePayload().toString());
        }
    }
    
    @Override
    public void onError(String errorMessage) {
        // Handle WebSocket error
        if (errorMessage != null && !errorMessage.isEmpty()) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }
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

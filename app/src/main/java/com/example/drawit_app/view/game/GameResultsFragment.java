package com.example.drawit_app.view.game;

import android.os.Bundle;
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
import com.example.drawit_app.databinding.FragmentGameResultsBinding;
import com.example.drawit_app.model.Game;
import com.example.drawit_app.model.PlayerScore;
import com.example.drawit_app.model.User;
import com.example.drawit_app.view.adapter.GameResultsAdapter;
import com.example.drawit_app.viewmodel.DrawingViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying final game results and scores
 */
@AndroidEntryPoint
public class GameResultsFragment extends Fragment {

    private FragmentGameResultsBinding binding;
    private DrawingViewModel drawingViewModel;
    private NavController navController;
    private String gameId;
    private GameResultsAdapter resultsAdapter;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentGameResultsBinding.inflate(inflater, container, false);
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
        
        setupRecyclerView();
        setupListeners();
        observeViewModel();
        
        // Load game results
        fetchGameResults(gameId);
    }
    
    private void setupRecyclerView() {
        resultsAdapter = new GameResultsAdapter(new ArrayList<>());
        binding.recyclerViewResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewResults.setAdapter(resultsAdapter);
    }
    
    private void setupListeners() {
        // Return to lobbies button click listener
        binding.btnReturnToLobbies.setOnClickListener(v -> 
            navController.navigate(R.id.action_gameResultsFragment_to_lobbiesFragment));
    }
    
    private void observeViewModel() {
        // Observe current game
        observeCurrentGame();
        
        // Observe loading state
        observeLoadingState();
        
        // Observe error messages
        observeErrorMessages();
    }
    
    private void observeCurrentGame() {
        // Simulate observation of current game
        // In a real implementation, this would come from the ViewModel
        Game game = new Game();
        game.setGameId(gameId);
        game.setTotalRounds(5);
        // Create sample player scores
        Map<String, Float> playerScores = game.getPlayerScores();
        playerScores.put("user1", 125.0f);
        playerScores.put("user2", 110.0f);
        playerScores.put("user3", 90.0f);
        
        updateGameResults(game);
    }
    
    private void observeLoadingState() {
        // Simulate loading state
        binding.progressBar.setVisibility(View.GONE);
        binding.resultsContainer.setVisibility(View.VISIBLE);
    }
    
    private void observeErrorMessages() {
        // Nothing to do here in mock implementation
    }
    
    private void fetchGameResults(String gameId) {
        // This would call the ViewModel method in a real implementation
        // For now, we'll use the observeViewModel method to show mock data
    }
    
    private void updateGameResults(Game game) {
        // Set game summary info
        binding.tvGameId.setText(getString(R.string.game_id_format, game.getGameId()));
        binding.tvRounds.setText(getString(R.string.rounds_completed_format, 
                game.getCurrentRound(), game.getTotalRounds()));
        
        // Set total participants
        if (game.getPlayerScores() != null) {
            binding.tvParticipants.setText(getString(R.string.participants_format, 
                    game.getPlayerScores().size()));
        }
        
        // Get winner
        PlayerScore winner = getWinner(convertMapToPlayerScores(game.getPlayerScores()));
        if (winner != null) {
            binding.tvWinner.setText(getString(R.string.winner_format, 
                    winner.getPlayer().getUsername()));
        }
        
        // Update results list
        if (game.getPlayerScores() != null) {
            List<PlayerScore> playerScores = convertMapToPlayerScores(game.getPlayerScores());
            resultsAdapter.updateResults(playerScores);
        }
    }
    
    private List<PlayerScore> convertMapToPlayerScores(Map<String, Float> scoreMap) {
        List<PlayerScore> result = new ArrayList<>();
        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            User user = new User();
            user.setUsername(entry.getKey());
            PlayerScore playerScore = new PlayerScore(user, Math.round(entry.getValue()));
            result.add(playerScore);
        }
        
        // Sort by score (descending)
        Collections.sort(result, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        return result;
    }
    
    private PlayerScore getWinner(List<PlayerScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        
        // First player score is already the winner since the list is sorted
        return scores.get(0);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

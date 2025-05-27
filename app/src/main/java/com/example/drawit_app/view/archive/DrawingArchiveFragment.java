package com.example.drawit_app.view.archive;

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
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentDrawingArchiveBinding;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.view.adapter.DrawingAdapter;
import com.example.drawit_app.viewmodel.DrawingViewModel;

import java.util.ArrayList;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying archived drawings
 */
@AndroidEntryPoint
public class DrawingArchiveFragment extends Fragment implements DrawingAdapter.DrawingClickListener {

    private FragmentDrawingArchiveBinding binding;
    private DrawingViewModel drawingViewModel;
    private NavController navController;
    private DrawingAdapter drawingAdapter;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDrawingArchiveBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        drawingViewModel = new ViewModelProvider(requireActivity()).get(DrawingViewModel.class);
        
        setupRecyclerView();
        setupListeners();
        observeViewModel();
        
        // Load drawings when fragment is created
        fetchDrawings();
    }
    
    private void setupRecyclerView() {
        drawingAdapter = new DrawingAdapter(new ArrayList<>(), this);
        
        // Use a grid layout for drawings (2 columns)
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        binding.rvDrawings.setLayoutManager(layoutManager);
        binding.rvDrawings.setAdapter(drawingAdapter);
    }
    
    private void setupListeners() {
        // Search using TextInputLayout's end icon
        binding.tilSearch.setEndIconOnClickListener(v -> {
            String query = binding.etSearch.getText().toString().trim();
            drawingViewModel.searchDrawings(query);
        });
        
        // Swipe refresh listener
        binding.swipeRefresh.setOnRefreshListener(this::fetchDrawings);
    }
    
    private void observeViewModel() {
        // Observe drawings list
        drawingViewModel.getDrawings().observe(getViewLifecycleOwner(), drawings -> {
            drawingAdapter.updateDrawings(drawings);
            
            // Show empty state if no drawings
            if (drawings.isEmpty()) {
                binding.tvNoDrawings.setVisibility(View.VISIBLE);
            } else {
                binding.tvNoDrawings.setVisibility(View.GONE);
            }
        });
        
        // Observe loading state
        drawingViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.swipeRefresh.setRefreshing(isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
        
        // Observe error messages
        drawingViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void fetchDrawings() {
        // Clear search query
        binding.etSearch.setText("");
        
        // Fetch all drawings
        drawingViewModel.fetchDrawings();
    }
    
    @Override
    public void onDrawingClick(Drawing drawing) {
        // Navigate to drawing detail screen
        Bundle args = new Bundle();
        args.putString("drawingId", drawing.getId());
        navController.navigate(R.id.action_drawingArchiveFragment_to_drawingDetailFragment, args);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

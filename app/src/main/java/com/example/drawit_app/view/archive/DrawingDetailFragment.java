package com.example.drawit_app.view.archive;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.drawit_app.R;
import com.example.drawit_app.databinding.FragmentDrawingDetailBinding;
import com.example.drawit_app.model.Drawing;
import com.example.drawit_app.viewmodel.DrawingViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Fragment for displaying detailed view of a drawing
 */
@AndroidEntryPoint
public class DrawingDetailFragment extends Fragment {

    private FragmentDrawingDetailBinding binding;
    private DrawingViewModel drawingViewModel;
    private NavController navController;
    private String drawingId;
    private Drawing currentDrawing;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDrawingDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        navController = Navigation.findNavController(view);
        drawingViewModel = new ViewModelProvider(requireActivity()).get(DrawingViewModel.class);
        
        // Get drawing ID from arguments
        if (getArguments() != null) {
            drawingId = getArguments().getString("drawingId");
        }
        
        if (drawingId == null) {
            // Invalid state, go back
            Toast.makeText(requireContext(), "Invalid drawing", Toast.LENGTH_SHORT).show();
            navController.navigateUp();
            return;
        }
        
        setupListeners();
        observeViewModel();
        
        // Load drawing details
        drawingViewModel.fetchDrawingDetails(drawingId);
    }
    
    private void setupListeners() {
        // Share drawing button click listener
        binding.btnShareDrawing.setOnClickListener(v -> shareDrawing());
        
        // Download drawing button click listener
        binding.btnDownloadDrawing.setOnClickListener(v -> downloadDrawing());
        
        // Rate drawing buttons
        binding.btnRate1.setOnClickListener(v -> rateDrawing(1));
        binding.btnRate2.setOnClickListener(v -> rateDrawing(2));
        binding.btnRate3.setOnClickListener(v -> rateDrawing(3));
        binding.btnRate4.setOnClickListener(v -> rateDrawing(4));
        binding.btnRate5.setOnClickListener(v -> rateDrawing(5));
    }
    
    private void observeViewModel() {
        // Observe drawing details
        drawingViewModel.getDrawingDetails().observe(getViewLifecycleOwner(), drawing -> {
            if (drawing != null) {
                currentDrawing = drawing;
                updateDrawingUI(drawing);
            }
        });
        
        // Observe loading state
        drawingViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.scrollView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        });
        
        // Observe error messages
        drawingViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void updateDrawingUI(Drawing drawing) {
        // Format date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateStr = dateFormat.format(drawing.getCreatedAt());
        
        // Set drawing details
        binding.tvArtistName.setText(drawing.getArtist().getUsername());
        binding.tvWord.setText(drawing.getWord());
        binding.tvDate.setText(dateStr);
        binding.tvGameId.setText(drawing.getGameId());
        binding.tvAverageRating.setText(String.format(Locale.getDefault(), "%.1f", drawing.getAverageRating()));
        
        // Load drawing image
        if (drawing.getImagePath() != null) {
            binding.drawingView.setPathsFromJson(drawing.getImagePath());
        }
        
        // Update rating buttons
        updateRatingButtons(drawing.getUserRating());
    }
    
    private void updateRatingButtons(int userRating) {
        // Reset all buttons
        binding.btnRate1.setSelected(false);
        binding.btnRate2.setSelected(false);
        binding.btnRate3.setSelected(false);
        binding.btnRate4.setSelected(false);
        binding.btnRate5.setSelected(false);
        
        // Highlight buttons based on user rating
        switch (userRating) {
            case 5:
                binding.btnRate5.setSelected(true);
                // Fall through
            case 4:
                binding.btnRate4.setSelected(true);
                // Fall through
            case 3:
                binding.btnRate3.setSelected(true);
                // Fall through
            case 2:
                binding.btnRate2.setSelected(true);
                // Fall through
            case 1:
                binding.btnRate1.setSelected(true);
                break;
        }
    }
    
    private void rateDrawing(int rating) {
        drawingViewModel.rateDrawing(drawingId, rating);
    }
    
    private void shareDrawing() {
        if (currentDrawing == null) return;
        
        try {
            // Get bitmap from drawing view
            Bitmap bitmap = binding.drawingView.getBitmap();
            
            // Save bitmap to cache directory
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "shared_drawing.png");
            
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            
            // Get URI for the file
            Uri imageUri = FileProvider.getUriForFile(requireContext(), 
                    requireContext().getPackageName() + ".fileprovider", imageFile);
            
            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this drawing of \"" + 
                    currentDrawing.getWord() + "\" by " + currentDrawing.getArtist().getUsername());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Start share activity
            startActivity(Intent.createChooser(shareIntent, "Share drawing via"));
            
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error sharing drawing", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void downloadDrawing() {
        if (currentDrawing == null) return;
        
        // Get bitmap from drawing view
        Bitmap bitmap = binding.drawingView.getBitmap();
        
        // Save to gallery
        drawingViewModel.saveDrawingToGallery(bitmap, currentDrawing.getWord());
        
        Toast.makeText(requireContext(), "Drawing saved to gallery", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

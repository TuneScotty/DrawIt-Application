package com.example.drawit_app.view;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.drawit_app.R;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Main activity for the DrawIt application, hosting all fragments
 * and managing navigation between them.
 */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up the toolbar as the action bar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Set up Navigation Component
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            
            // Only login and lobbies are top-level destinations (no back button)
            // All other screens should have a back button for consistent navigation
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.loginFragment,
                    R.id.lobbiesFragment
            ).build();
            
            // Set up the ActionBar with NavController
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) 
                || super.onSupportNavigateUp();
    }
}

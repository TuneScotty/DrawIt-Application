package com.example.drawit_app;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;

/**
 * Application class for the DrawIt app, serves as the entry point for Hilt dependency injection.
 */
@HiltAndroidApp
public class DrawItApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize any application-wide services or configurations here
    }
}

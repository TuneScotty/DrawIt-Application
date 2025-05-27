package com.example.drawit_app.di;

import android.content.Context;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Dagger module that provides application-level dependencies
 */
@Module
@InstallIn(SingletonComponent.class)
public class ApplicationModule {
    
    /**
     * Provides application context for dependency injection
     */
    @Provides
    @Singleton
    public Context provideApplicationContext(@ApplicationContext Context context) {
        return context;
    }
}

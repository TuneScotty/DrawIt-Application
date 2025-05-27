package com.example.drawit_app.di;

import android.content.Context;

import com.example.drawit_app.data.DrawItDatabase;
import com.example.drawit_app.data.DrawingDao;
import com.example.drawit_app.data.GameDao;
import com.example.drawit_app.data.LobbyDao;
import com.example.drawit_app.data.UserDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * Hilt module that provides database-related dependencies
 */
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {
    
    @Provides
    @Singleton
    public DrawItDatabase provideDatabase(@ApplicationContext Context context) {
        return DrawItDatabase.getInstance(context);
    }
    
    @Provides
    @Singleton
    public UserDao provideUserDao(DrawItDatabase database) {
        return database.userDao();
    }
    
    @Provides
    @Singleton
    public LobbyDao provideLobbyDao(DrawItDatabase database) {
        return database.lobbyDao();
    }
    
    @Provides
    @Singleton
    public GameDao provideGameDao(DrawItDatabase database) {
        return database.gameDao();
    }
    
    @Provides
    @Singleton
    public DrawingDao provideDrawingDao(DrawItDatabase database) {
        return database.drawingDao();
    }
}

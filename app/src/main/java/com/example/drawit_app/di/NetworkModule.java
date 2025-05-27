package com.example.drawit_app.di;

import com.example.drawit_app.network.ApiService;
import com.example.drawit_app.network.WebSocketService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    // Server URLs
    private static final String BASE_URL = "http://145.223.99.251:8080/";

    // WebSocket URL (for real-time communication)
    private static final String WS_URL = "ws://145.223.99.251:8080/ws";
    
    private static final int THREADS = 3;

    @Provides
    @Singleton
    public static OkHttpClient provideOkHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            android.util.Log.d("OkHttp", message);
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        return new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Provides
    @Singleton
    public static Retrofit provideRetrofit(OkHttpClient okHttpClient) {
        // Configure Gson for serialization
        Gson gson = new GsonBuilder()
                .setLenient()
                .setPrettyPrinting() // Makes JSON more readable in logs
                .create();
        
        // Add an interceptor to ensure all requests have the correct Content-Type header
        OkHttpClient clientWithHeaders = okHttpClient.newBuilder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    
                    // Only add the header for POST, PUT requests which likely have a body
                    if (original.method().equals("POST") || original.method().equals("PUT")) {
                        Request request = original.newBuilder()
                                .header("Content-Type", "application/json")
                                .method(original.method(), original.body())
                                .build();
                        return chain.proceed(request);
                    }
                    
                    return chain.proceed(original);
                })
                .build();
                
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(clientWithHeaders)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .callbackExecutor(Executors.newFixedThreadPool(THREADS))
                .build();
    }

    @Provides
    @Singleton
    public static ApiService provideApiService(Retrofit retrofit) {
        return retrofit.create(ApiService.class);
    }
    
    @Provides
    @Singleton
    public static String provideWebSocketUrl() {
        return WS_URL;
    }
    
    /**
     * Provides a WebSocketService instance
     * Note: This returns a stub instance that will be configured properly
     * when needed by repositories with the actual auth token and callbacks
     */
    @Provides
    @Singleton
    public static WebSocketService provideWebSocketService(OkHttpClient client) {
        // Return a stub instance with the URL but no auth token or callback yet
        // These will be set when the service is actually used
        return new WebSocketService(WS_URL, null, null);
    }
}
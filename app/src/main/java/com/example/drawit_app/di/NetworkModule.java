package com.example.drawit_app.di;

import com.example.drawit_app.api.ApiService;
import com.example.drawit_app.api.WebSocketService;
import com.squareup.moshi.Moshi;

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
import retrofit2.converter.moshi.MoshiConverterFactory;

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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Provides
    @Singleton
    public static Moshi provideMoshi() {
        // Configure Moshi for serialization to properly handle @Json annotations, Date and Void type
        Moshi moshi = new Moshi.Builder()
                .add(Void.class, new com.squareup.moshi.JsonAdapter<Void>() {
                    @Override
                    public Void fromJson(com.squareup.moshi.JsonReader reader) throws java.io.IOException {
                        reader.skipValue();
                        return null;
                    }

                    @Override
                    public void toJson(com.squareup.moshi.JsonWriter writer, Void value) throws java.io.IOException {
                        writer.nullValue();
                    }
                })
                // Add custom adapter for java.util.Date
                .add(java.util.Date.class, new com.squareup.moshi.JsonAdapter<java.util.Date>() {
                    @Override
                    public java.util.Date fromJson(com.squareup.moshi.JsonReader reader) throws java.io.IOException {
                        if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) {
                            reader.nextNull();
                            return null;
                        }
                        String dateString = reader.nextString();
                        try {
                            // Try to parse as a timestamp first
                            long timestamp = Long.parseLong(dateString);
                            return new java.util.Date(timestamp);
                        } catch (NumberFormatException e) {
                            // If it's not a timestamp, try to parse as ISO 8601 date
                            try {
                                return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(dateString);
                            } catch (java.text.ParseException pe) {
                                try {
                                    // Try another common format
                                    return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(dateString);
                                } catch (java.text.ParseException pe2) {
                                    android.util.Log.e("NetworkModule", "Failed to parse date: " + dateString, pe2);
                                    return null;
                                }
                            }
                        }
                    }

                    @Override
                    public void toJson(com.squareup.moshi.JsonWriter writer, java.util.Date value) throws java.io.IOException {
                        if (value == null) {
                            writer.nullValue();
                        } else {
                            // Use ISO 8601 format for consistency
                            String dateFormatted = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(value);
                            writer.value(dateFormatted);
                        }
                    }
                })
                .build();
                
        android.util.Log.d("NetworkModule", "DEBUG: Using Moshi for JSON parsing to handle @Json annotations and Date");
        
        return moshi;
    }

    @Provides
    @Singleton
    public static Retrofit provideRetrofit(OkHttpClient okHttpClient, Moshi moshi) {
        
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
                .addConverterFactory(MoshiConverterFactory.create(moshi))
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
     * Provides a WebSocketService instance without LobbyRepository to break circular dependency
     * LobbyRepository will be injected later via setter
     * 
     * @param client The OkHttpClient for network requests
     */
    @Provides
    @Singleton
    public static WebSocketService provideWebSocketService(OkHttpClient client) {
        return new WebSocketService(WS_URL, null, null);
    }
    

}
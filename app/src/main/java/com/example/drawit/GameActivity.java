package com.example.drawit;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Toast;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.drawit.views.DrawingView;

public class GameActivity extends AppCompatActivity {
    private TextView wordToDrawText;
    private TextView timerText;
    private DrawingView drawingView;
    private Button blackColorBtn;
    private Button redColorBtn;
    private Button blueColorBtn;
    private ImageButton eraserBtn;
    private String lobbyId;
    private FirebaseHandler firebaseHandler;
    private CountDownTimer roundTimer;
    private static final long ROUND_DURATION = 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        lobbyId = getIntent().getStringExtra("LOBBY_ID");
        firebaseHandler = FirebaseHandler.getInstance();

        setupViews();
        setupDrawingTools();
        setupGameStateListener();
        startRound();
    }

    private void setupViews() {
        wordToDrawText = findViewById(R.id.wordToDrawText);
        timerText = findViewById(R.id.timerText);
        drawingView = findViewById(R.id.drawingCanvas);
        blackColorBtn = findViewById(R.id.blackColorBtn);
        redColorBtn = findViewById(R.id.redColorBtn);
        blueColorBtn = findViewById(R.id.blueColorBtn);
        eraserBtn = findViewById(R.id.eraserBtn);
    }

    private void setupDrawingTools() {
        findViewById(R.id.smallBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(12f));
        findViewById(R.id.mediumBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(24f));
        findViewById(R.id.largeBrushBtn).setOnClickListener(v -> drawingView.setBrushSize(36f));
        findViewById(R.id.eraserBtn).setOnClickListener(v -> drawingView.setEraser());
        findViewById(R.id.clearCanvasBtn).setOnClickListener(v -> drawingView.clearCanvas());
        
        // Color buttons
        findViewById(R.id.blackColorBtn).setOnClickListener(v -> drawingView.setColor(Color.BLACK));
        findViewById(R.id.redColorBtn).setOnClickListener(v -> drawingView.setColor(Color.RED));
        findViewById(R.id.blueColorBtn).setOnClickListener(v -> drawingView.setColor(Color.BLUE));
        findViewById(R.id.greenColorBtn).setOnClickListener(v -> drawingView.setColor(Color.GREEN));
    }

    private void setupGameStateListener() {
        firebaseHandler.addInGameLobbyListener(lobbyId, gameStarted -> {
            if (!gameStarted) {
                Toast.makeText(this, "Game ended by host", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void startRound() {
        wordToDrawText.setText("Draw: Cat");  // Replace with random word logic
        startTimer();
    }

    private void startTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        roundTimer = new CountDownTimer(ROUND_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                timerText.setText(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60));
            }

            @Override
            public void onFinish() {
                endRound();
            }
        }.start();
    }

    private void endRound() {
        // TODO: Implement round end logic
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        firebaseHandler.endGame(lobbyId);
    }
} 
package com.samrudhasolutions.pronunciation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Button btnStartRecording, btnStopRecording;
    private TextView tvAssessmentResult;
    private RecordAudio.AudioRecorderRunnable audioRecorderRunnable;
    private Thread audioThread;

    private static final String subscriptionKey = "d68a03a87471470f928cc488abe545ff";
    private static final String serviceRegion = "centralindia";
    private static final String lang = "en-US";
    private static final String referenceText = "Hi I am Samrudha"; // The text against which the pronunciation will be assessed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartRecording = findViewById(R.id.btnStartRecording);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        tvAssessmentResult = findViewById(R.id.tvAssessmentResult);

        btnStartRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        btnStopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
                try {
                    pronunciationAssessmentWithMic();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Exception in pronunciationAssessmentWithMic: " + e.getMessage());
                }
            }
        });
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            audioRecorderRunnable = new RecordAudio.AudioRecorderRunnable(this);
            audioThread = new Thread(audioRecorderRunnable);
            audioThread.start();
            btnStartRecording.setVisibility(View.GONE);
            btnStopRecording.setVisibility(View.VISIBLE);
        }
    }

    private void stopRecording() {
        if (audioRecorderRunnable != null) {
            audioRecorderRunnable.stopRecording();
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            audioRecorderRunnable = null;
            audioThread = null;
            btnStartRecording.setVisibility(View.VISIBLE);
            btnStopRecording.setVisibility(View.GONE);
        }
    }

    private void pronunciationAssessmentWithMic() throws ExecutionException, InterruptedException {
        new Thread(() -> {
            try {
                String result = PronunciationAssessment.pronunciationAssessmentWithMic(subscriptionKey, serviceRegion, lang, referenceText);
                Log.d(TAG, "Assessment result: " + result);
                updateAssessmentResult(result);
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.e(TAG, "Exception in pronunciationAssessmentWithMic: " + ex.getMessage());
                updateAssessmentResult("Error during assessment. Check logs for details.");
            }
        }).start();
    }

    private void updateAssessmentResult(String result) {
        runOnUiThread(() -> tvAssessmentResult.setText(result));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording(); // Ensure recording is stopped when activity is destroyed
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Log.e(TAG, "RECORD_AUDIO permission denied.");
        }
    }
}

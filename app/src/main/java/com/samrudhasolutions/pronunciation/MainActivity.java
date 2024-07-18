package com.samrudhasolutions.pronunciation;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PronunciationAssessment";
    private Semaphore stopRecognitionSemaphore;
    private TextView resultsTextView;
    private RecordAudio recordAudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultsTextView = findViewById(R.id.resultsTextView);
        recordAudio = new RecordAudio();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        }
    }

    private void startRecording() {
        recordAudio.startRecording(this);

        // Stop recording after a delay and start the assessment
        new android.os.Handler().postDelayed(() -> {
            recordAudio.stopRecording(this);
            startPronunciationAssessment();
        }, 10000); // Record for 10 seconds
    }

    private void startPronunciationAssessment() {
        try {
            pronunciationAssessmentWithAudioFile();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error during pronunciation assessment", e);
        }
    }

    public void pronunciationAssessmentWithAudioFile() throws ExecutionException, InterruptedException {
        SpeechConfig config = SpeechConfig.fromSubscription("d68a03a87471470f928cc488abe545ff", "centralindia");
        String lang = "en-US";

        // Get the recorded audio file from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("com.example.demospeechtotext", Context.MODE_PRIVATE);
        String base64Audio = prefs.getString("AUDIO", "");
        byte[] wavData = Base64.decode(base64Audio, Base64.DEFAULT);

        File tempAudioFile = new File(getCacheDir(), "sample.wav");
        try (FileOutputStream fos = new FileOutputStream(tempAudioFile)) {
            fos.write(wavData);
        } catch (IOException e) {
            Log.e(TAG, "Error writing audio file", e);
        }

        AudioConfig audioInput = AudioConfig.fromWavFileInput(tempAudioFile.getAbsolutePath());

        stopRecognitionSemaphore = new Semaphore(0);
        List<Double> fluencyScores = new ArrayList<>();
        List<Double> accuracyScores = new ArrayList<>();
        List<Double> completenessScores = new ArrayList<>();
        List<Double> prosodyScores = new ArrayList<>();

        SpeechRecognizer recognizer = new SpeechRecognizer(config, lang, audioInput);

        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                Log.d(TAG, "RECOGNIZED: Text=" + e.getResult().getText());
                PronunciationAssessmentResult pronResult = PronunciationAssessmentResult.fromResult(e.getResult());

                fluencyScores.add(pronResult.getFluencyScore());
                accuracyScores.add(pronResult.getAccuracyScore());
                completenessScores.add(pronResult.getCompletenessScore());
//              prosodyScores.add(pronResult.getProsodyScore()); // Uncomment this if prosody score is supported
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                Log.d(TAG, "NOMATCH: Speech could not be recognized.");
            }
        });

        recognizer.canceled.addEventListener((s, e) -> {
            Log.e(TAG, "CANCELED: Reason=" + e.getReason());
            if (e.getReason() == CancellationReason.Error) {
                Log.e(TAG, "CANCELED: ErrorCode=" + e.getErrorCode());
                Log.e(TAG, "CANCELED: ErrorDetails=" + e.getErrorDetails());
            }
            stopRecognitionSemaphore.release();
        });

        recognizer.sessionStarted.addEventListener((s, e) -> Log.d(TAG, "Session started event."));
        recognizer.sessionStopped.addEventListener((s, e) -> Log.d(TAG, "Session stopped event."));

        String referenceText = "Hi I am Samrudha";
        PronunciationAssessmentConfig pronunciationConfig = new PronunciationAssessmentConfig(referenceText,
                PronunciationAssessmentGradingSystem.HundredMark, PronunciationAssessmentGranularity.Word, true);

        pronunciationConfig.applyTo(recognizer);

        recognizer.startContinuousRecognitionAsync().get();
        stopRecognitionSemaphore.acquire();
        recognizer.stopContinuousRecognitionAsync().get();

        // Calculate final results
        double averageFluency = fluencyScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double averageAccuracy = accuracyScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double averageCompleteness = completenessScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double averageProsody = prosodyScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Prepare result text
        StringBuilder results = new StringBuilder();
        results.append("Fluency Score: ").append(averageFluency).append("\n");
        results.append("Accuracy Score: ").append(averageAccuracy).append("\n");
        results.append("Completeness Score: ").append(averageCompleteness).append("\n");
        results.append("Prosody Score: ").append(averageProsody).append("\n");

        Log.d(TAG, results.toString()); // Display results in log
        resultsTextView.setText(results.toString()); // Set results to TextView

        config.close();
        audioInput.close();
        recognizer.close();

        // Clean up the temporary file if needed
        if (tempAudioFile.exists()) {
            tempAudioFile.delete();
        }
    }
}

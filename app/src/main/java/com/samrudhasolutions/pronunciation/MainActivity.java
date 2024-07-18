package com.samrudhasolutions.pronunciation;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PronunciationAssessment";
    private Semaphore stopRecognitionSemaphore;
    private TextView resultsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultsTextView = findViewById(R.id.resultsTextView);
        startPronunciationAssessment();
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

        // Copy audio file from raw resources to a temporary file
        File tempAudioFile = copyAudioFileToTemp();
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
//              prosodyScores.add(pronResult.getProsodyScore());//getting error just see here
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

        String referenceText = "Today was a beautiful day. We had a great time taking a long walk outside in the morning. \nThe countryside was in full bloom, yet the air was crisp and cold. \nTowards the end of the day, clouds came in, forecasting much needed rain.";
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

    private File copyAudioFileToTemp() {
        File tempFile = new File(getCacheDir(), "sample.wav");
        try (InputStream in = getResources().openRawResource(R.raw.sample);
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying audio file", e);
        }
        return tempFile;
    }
}
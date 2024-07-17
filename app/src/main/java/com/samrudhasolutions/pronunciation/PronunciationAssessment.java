package com.samrudhasolutions.pronunciation;

import android.util.Log;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PronunciationAssessment {

    private static final String TAG = "PronunciationAssessment";

    public static String pronunciationAssessmentWithMic(String subscriptionKey, String serviceRegion, String lang, String referenceText) throws Exception {
        SpeechConfig config = SpeechConfig.fromSubscription(subscriptionKey, serviceRegion);
        AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();

        PronunciationAssessmentConfig pronunciationConfig = new PronunciationAssessmentConfig(referenceText, PronunciationAssessmentGradingSystem.HundredMark, PronunciationAssessmentGranularity.Word);
        SpeechRecognizer recognizer = new SpeechRecognizer(config, lang, audioConfig);

        final StringBuilder resultBuilder = new StringBuilder();

        recognizer.recognizing.addEventListener((s, e) -> {
            Log.d(TAG, "Recognizing: " + e.getResult().getText());
        });

        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                Log.d(TAG, "Recognized Speech: " + e.getResult().getText());
                String jsonResult = e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                Log.d(TAG, "JSON Result: " + jsonResult);
                String pronunciationResult = parsePronunciationResult(jsonResult);
                resultBuilder.append("Recognized: ").append(e.getResult().getText()).append("\n")
                        .append("Pronunciation Assessment Result: ").append(pronunciationResult);
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                resultBuilder.append("No speech could be recognized.");
                Log.d(TAG, "No speech could be recognized.");
            }
        });

        recognizer.canceled.addEventListener((s, e) -> {
            Log.e(TAG, "Recognition canceled. Reason: " + e.getReason() + ". Error details: " + e.getErrorDetails());
        });

        recognizer.sessionStopped.addEventListener((s, e) -> {
            Log.d(TAG, "Session stopped.");
        });

        pronunciationConfig.applyTo(recognizer);

        recognizer.startContinuousRecognitionAsync().get();

        // Wait for a while to ensure recognition is complete
        Thread.sleep(10000);

        recognizer.stopContinuousRecognitionAsync().get();

        Log.d(TAG, "Final result: " + resultBuilder.toString());
        return resultBuilder.toString();
    }

    private static String parsePronunciationResult(String jsonResult) {
        StringBuilder parsedResult = new StringBuilder();
        try {
            JSONObject jsonObject = new JSONObject(jsonResult);
            JSONArray nBestArray = jsonObject.optJSONArray("NBest");
            if (nBestArray != null && nBestArray.length() > 0) {
                JSONObject nBest = nBestArray.getJSONObject(0);
                JSONArray wordsArray = nBest.optJSONArray("Words");
                if (wordsArray != null && wordsArray.length() > 0) {
                    for (int i = 0; i < wordsArray.length(); i++) {
                        JSONObject word = wordsArray.getJSONObject(i);
                        String wordText = word.optString("Word");
                        JSONObject pronunciationAssessment = word.optJSONObject("PronunciationAssessment");
                        if (pronunciationAssessment != null) {
                            double accuracyScore = pronunciationAssessment.optDouble("AccuracyScore", 0);
                            double fluencyScore = pronunciationAssessment.optDouble("FluencyScore", 0);
                            double prosodyScore = pronunciationAssessment.optDouble("ProsodyScore", 0);
                            double completenessScore = pronunciationAssessment.optDouble("CompletenessScore", 0);
                            parsedResult.append("Word: ").append(wordText).append("\n")
                                    .append("Pronunciation Assessment: ").append(prosodyScore).append("\n")
                                    .append("Accuracy Score: ").append(accuracyScore).append("\n")
                                    .append("Fluency Score: ").append(fluencyScore).append("\n")
                                    .append("Completeness Score: ").append(completenessScore).append("\n\n");

                        } else {
                            parsedResult.append("Word: ").append(wordText).append("\n")
                                    .append("Pronunciation Assessment    data not found.").append("\n\n");
                        }
                    }
                } else {
                    parsedResult.append("Words data not found.");
                }
            } else {
                parsedResult.append("NBest data not found.");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "Error parsing pronunciation result.";
        }
        return parsedResult.toString();
    }
}

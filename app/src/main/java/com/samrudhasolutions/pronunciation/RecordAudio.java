package com.samrudhasolutions.pronunciation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class RecordAudio {

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static class AudioRecorderRunnable implements Runnable {

        private boolean isRecording;
        private AudioRecord audioRecord;
        private Context context;

        public AudioRecorderRunnable(Context context) {
            this.context = context;
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e("RecordAudio", "RECORD_AUDIO permission not granted");
                return;
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            try {
                byte[] buffer = new byte[1024];
                audioRecord.startRecording();
                isRecording = true;

                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    // Process audio data read from buffer
                    // Example: sendDataToSpeechRecognition(buffer, bytesRead);
                }
            } catch (IllegalStateException e) {
                Log.e("RecordAudio", "Error reading audio data: " + e.getMessage());
                e.printStackTrace();
            } finally {
                audioRecord.stop();
                audioRecord.release();
            }
        }

        public void stopRecording() {
            isRecording = false;
        }
    }
}

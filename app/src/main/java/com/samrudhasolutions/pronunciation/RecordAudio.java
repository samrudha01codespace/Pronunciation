package com.samrudhasolutions.pronunciation;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RecordAudio {

    public static final String TAG = "MainActivity";

    public static final int SAMPLE_RATE = 44100; // supported on all devices
    public static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // not supported on all devices
    public static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    public static final int BUFFER_SIZE_PLAYING = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

    AudioRecord audioRecord;
    AudioTrack audioTrack;

    private Thread recordingThread;
    private Thread playingThread;

    boolean isRecordingAudio = false;

    byte[] audioData; // Variable to store audio data

    public void startRecording(Context con) {
        audioData = null; // Reset audioData if necessary

        if (ActivityCompat.checkSelfPermission(con, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_RECORDING);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "error initializing AudioRecord");
            return;
        }

        audioRecord.startRecording();
        Log.d(TAG, "recording started with AudioRecord");

        isRecordingAudio = true;

        recordingThread = new Thread(() -> writeAudioDataToVariable(con));
        recordingThread.start();
    }

    public void stopRecording(Context con) {
        isRecordingAudio = false;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
        }

        // Convert PCM to WAV and save it
        byte[] wavData = convertToWav(audioData, SAMPLE_RATE, 16, 1);

        SharedPreferences prefs = con.getSharedPreferences("com.example.demospeechtotext", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String base64Audio = Base64.encodeToString(wavData, Base64.DEFAULT);
        editor.putString("AUDIO", base64Audio);
        editor.apply();
    }

    private void writeAudioDataToVariable(Context con) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[BUFFER_SIZE_RECORDING];

        while (isRecordingAudio) {
            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read > 0) {
                outputStream.write(buffer, 0, read);
            }
        }

        try {
            audioData = outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] convertToWav(byte[] pcmData, int sampleRate, int bitsPerSample, int channels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int totalDataLen = pcmData.length + 36;
        int bitrate = sampleRate * channels * bitsPerSample;
        int byteRate = bitrate / 8;

        try {
            // Write WAV file header
            out.write("RIFF".getBytes()); // ChunkID
            out.write(intToByteArray(totalDataLen), 0, 4); // ChunkSize
            out.write("WAVE".getBytes()); // Format
            out.write("fmt ".getBytes()); // Subchunk1ID
            out.write(intToByteArray(16), 0, 4); // Subchunk1Size
            out.write(shortToByteArray((short) 1), 0, 2); // AudioFormat
            out.write(shortToByteArray((short) channels), 0, 2); // NumChannels
            out.write(intToByteArray(sampleRate), 0, 4); // SampleRate
            out.write(intToByteArray(byteRate), 0, 4); // ByteRate
            out.write(shortToByteArray((short) (channels * bitsPerSample / 8)), 0, 2); // BlockAlign
            out.write(shortToByteArray((short) bitsPerSample), 0, 2); // BitsPerSample
            out.write("data".getBytes()); // Subchunk2ID
            out.write(intToByteArray(pcmData.length), 0, 4); // Subchunk2Size
            out.write(pcmData); // Data

            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private byte[] shortToByteArray(short value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }
}

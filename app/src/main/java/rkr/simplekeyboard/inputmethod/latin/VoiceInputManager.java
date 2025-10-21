/*
 * Copyright (C) 2025 Simple Keyboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.R;

/**
 * Manages Google voice-to-text input functionality.
 */
public class VoiceInputManager {
    private static final String TAG = "VoiceInputManager";

    private static final int LISTENING_TIMEOUT_MS = 10000; // 10 seconds max listening time

    private final Context mContext;
    private SpeechRecognizer mSpeechRecognizer;
    private VoiceInputListener mListener;
    private ImageButton mMicButton;
    private boolean mIsListening = false;
    private android.os.Handler mHandler;
    private Runnable mTimeoutRunnable;
    private Dialog mVoiceDialog;

    /**
     * Callback interface for voice input events.
     */
    public interface VoiceInputListener {
        /**
         * Called when voice input text is ready.
         * @param text The recognized text
         */
        void onVoiceInputText(String text);

        /**
         * Called when voice input encounters an error.
         * @param errorCode Error code from SpeechRecognizer
         */
        void onVoiceInputError(int errorCode);

        /**
         * Called when voice input starts listening.
         */
        void onVoiceInputStarted();

        /**
         * Called when voice input stops listening.
         */
        void onVoiceInputStopped();
    }

    public VoiceInputManager(Context context) {
        this.mContext = context;
        this.mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    }

    /**
     * Initialize the voice input manager with the mic button.
     * @param micButton The mic button from the layout
     */
    public void initialize(ImageButton micButton) {
        this.mMicButton = micButton;

        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
            Log.e(TAG, "Speech recognition not available on this device");
            if (mMicButton != null) {
                mMicButton.setEnabled(false);
                mMicButton.setAlpha(0.5f);
            }
            return;
        }

        // Create speech recognizer
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        mSpeechRecognizer.setRecognitionListener(new VoiceRecognitionListener());

        // Set up mic button click listener
        if (mMicButton != null) {
            mMicButton.setOnClickListener(v -> {
                Log.d(TAG, "Mic button clicked");
                toggleVoiceInput();
            });
            Log.d(TAG, "Mic button initialized successfully");
        } else {
            Log.e(TAG, "Mic button is null in initialize()");
        }
    }

    /**
     * Set the voice input listener.
     * @param listener The listener to receive voice input events
     */
    public void setListener(VoiceInputListener listener) {
        this.mListener = listener;
    }

    /**
     * Toggle voice input on/off.
     */
    private void toggleVoiceInput() {
        Log.d(TAG, "toggleVoiceInput called, mIsListening=" + mIsListening);
        if (mIsListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    /**
     * Show the voice input dialog.
     */
    private void showVoiceDialog() {
        if (mVoiceDialog != null && mVoiceDialog.isShowing()) {
            return;
        }

        try {
            mVoiceDialog = new Dialog(mContext);
            mVoiceDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(mContext).inflate(R.layout.voice_input_dialog, null);
            mVoiceDialog.setContentView(dialogView);

            // Make dialog transparent
            Window window = mVoiceDialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setGravity(Gravity.CENTER);

                // Set as system alert to show above keyboard
                WindowManager.LayoutParams params = window.getAttributes();
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                             | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(params);
            }

            // Click to stop
            dialogView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopListening();
                }
            });

            mVoiceDialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing voice dialog", e);
        }
    }

    /**
     * Hide the voice input dialog.
     */
    private void hideVoiceDialog() {
        if (mVoiceDialog != null && mVoiceDialog.isShowing()) {
            try {
                mVoiceDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error hiding voice dialog", e);
            }
            mVoiceDialog = null;
        }
    }

    /**
     * Start listening for voice input.
     */
    public void startListening() {
        // Check permission (for Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "RECORD_AUDIO permission not granted");
                if (mListener != null) {
                    mListener.onVoiceInputError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
                }
                return;
            }
        }

        if (mSpeechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer not initialized");
            return;
        }

        // Create recognition intent
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        // Use current system locale
        Locale locale = Locale.getDefault();
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale.toLanguageTag());

        // Additional parameters for better recognition
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mContext.getPackageName());

        // CRITICAL: Don't show the speech recognition dialog UI
        // This keeps the keyboard visible
        intent.putExtra("android.speech.extra.HIDE_PARTIAL_TRAILING_PUNCTUATION", true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);

        // Show dialog first
        showVoiceDialog();

        // Start listening
        mSpeechRecognizer.startListening(intent);
        mIsListening = true;

        // Update UI
        updateMicButtonState(true);

        // Set up timeout to auto-stop if no speech detected
        mTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsListening) {
                    Log.d(TAG, "Voice input timeout - stopping");
                    stopListening();
                }
            }
        };
        mHandler.postDelayed(mTimeoutRunnable, LISTENING_TIMEOUT_MS);

        // Notify listener
        if (mListener != null) {
            mListener.onVoiceInputStarted();
        }

        Log.d(TAG, "Started listening for voice input");
    }

    /**
     * Stop listening for voice input.
     */
    public void stopListening() {
        // Hide dialog
        hideVoiceDialog();

        // Cancel timeout
        if (mTimeoutRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutRunnable = null;
        }

        if (mSpeechRecognizer != null && mIsListening) {
            mSpeechRecognizer.stopListening();
            mIsListening = false;
            updateMicButtonState(false);

            // Notify listener
            if (mListener != null) {
                mListener.onVoiceInputStopped();
            }

            Log.d(TAG, "Stopped listening for voice input");
        }
    }

    /**
     * Update mic button appearance based on listening state.
     * @param isListening Whether currently listening
     */
    private void updateMicButtonState(boolean isListening) {
        if (mMicButton != null) {
            if (isListening) {
                // Change to "listening" state with green background
                mMicButton.setAlpha(1.0f);
                mMicButton.setBackgroundResource(R.drawable.mic_button_listening_background);
                // Make the mic icon white when listening
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mMicButton.setImageTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFFFFFF) // White
                    );
                }
            } else {
                // Change back to normal state
                mMicButton.setAlpha(0.8f);
                mMicButton.setBackgroundResource(R.drawable.mic_button_background);
                // Restore original tint
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mMicButton.setImageTintList(null);
                }
            }
        }
    }

    /**
     * Clean up resources.
     */
    public void destroy() {
        hideVoiceDialog();

        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
        mIsListening = false;
    }

    /**
     * Check if currently listening.
     * @return true if listening, false otherwise
     */
    public boolean isListening() {
        return mIsListening;
    }

    /**
     * Recognition listener implementation.
     */
    private class VoiceRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Volume level changed - could be used for visual feedback
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Audio buffer received
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "End of speech");
            mIsListening = false;
            updateMicButtonState(false);
            hideVoiceDialog();
        }

        @Override
        public void onError(int error) {
            String errorMessage;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMessage = "Audio recording error";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMessage = "Client side error";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMessage = "Insufficient permissions";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    errorMessage = "Network error";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMessage = "Network timeout";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage = "No recognition result matched";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMessage = "RecognitionService busy";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    errorMessage = "Server error";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMessage = "No speech input";
                    break;
                default:
                    errorMessage = "Unknown error: " + error;
                    break;
            }

            Log.e(TAG, "Voice recognition error: " + errorMessage);
            mIsListening = false;
            updateMicButtonState(false);
            hideVoiceDialog();

            if (mListener != null) {
                mListener.onVoiceInputError(error);
            }
        }

        @Override
        public void onResults(Bundle results) {
            hideVoiceDialog();

            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches != null && !matches.isEmpty()) {
                // Get the best match (first result)
                String recognizedText = matches.get(0);
                Log.d(TAG, "Recognition result: " + recognizedText);

                if (mListener != null) {
                    mListener.onVoiceInputText(recognizedText);
                }
            }

            mIsListening = false;
            updateMicButtonState(false);

            if (mListener != null) {
                mListener.onVoiceInputStopped();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // Partial results available - could be used for real-time feedback
            ArrayList<String> matches = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches != null && !matches.isEmpty()) {
                Log.d(TAG, "Partial result: " + matches.get(0));
                // Could update UI with partial results here
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Reserved for future use
        }
    }
}

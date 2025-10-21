package com.example.aerogcsclone.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Manages Text-to-Speech functionality for the application.
 * Provides voice feedback for connection status and calibration events.
 */
class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false

    companion object {
        private const val TAG = "TextToSpeechManager"

        // Voice messages
        const val MSG_CONNECTED = "Connected"
        const val MSG_DISCONNECTED = "Disconnected"
        const val MSG_CONNECTION_FAILED = "Connection failed"
        const val MSG_CALIBRATION_STARTED = "Calibration started"
        const val MSG_CALIBRATION_FINISHED = "Calibration finished"
        const val MSG_CALIBRATION_SUCCESS = "Calibration completed successfully"
        const val MSG_CALIBRATION_FAILED = "Calibration failed"
        const val MSG_SELECTED_AUTOMATIC = "Selected automatic"
        const val MSG_SELECTED_MANUAL = "Selected manual"
    }

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported")
                    isReady = false
                } else {
                    Log.d(TAG, "TextToSpeech initialized successfully")
                    isReady = true

                    // Configure TTS settings
                    tts.setSpeechRate(1.0f)
                    tts.setPitch(1.0f)
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
            isReady = false
        }
    }

    /**
     * Speaks the given text if TTS is ready
     */
    fun speak(text: String) {
        if (isReady && textToSpeech != null) {
            Log.d(TAG, "Speaking: $text")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(TAG, "TTS not ready or not initialized. Cannot speak: $text")
        }
    }

    /**
     * Announces connection status
     */
    fun announceConnectionStatus(isConnected: Boolean) {
        val message = if (isConnected) MSG_CONNECTED else MSG_DISCONNECTED
        speak(message)
    }

    /**
     * Announces calibration started
     */
    fun announceCalibrationStarted() {
        speak(MSG_CALIBRATION_STARTED)
    }

    /**
     * Announces calibration finished with success/failure status
     */
    fun announceCalibrationFinished(isSuccess: Boolean) {
        val message = if (isSuccess) MSG_CALIBRATION_SUCCESS else MSG_CALIBRATION_FAILED
        speak(message)
    }

    /**
     * Announces general calibration finished message
     */
    fun announceCalibrationFinished() {
        speak(MSG_CALIBRATION_FINISHED)
    }

    /**
     * Announces connection failed status
     */
    fun announceConnectionFailed() {
        speak(MSG_CONNECTION_FAILED)
    }

    /**
     * Announces automatic mode selection
     */
    fun announceSelectedAutomatic() {
        speak(MSG_SELECTED_AUTOMATIC)
    }

    /**
     * Announces manual mode selection
     */
    fun announceSelectedManual() {
        speak(MSG_SELECTED_MANUAL)
    }

    /**
     * Announces calibration type when entering calibration screens
     */
    fun announceCalibration(calibrationType: String) {
        speak("$calibrationType calibration")
    }

    /**
     * Stops any ongoing speech
     */
    fun stop() {
        textToSpeech?.stop()
    }

    /**
     * Releases TTS resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        Log.d(TAG, "TextToSpeech shutdown")
    }

    /**
     * Checks if TTS is ready to use
     */
    fun isReady(): Boolean = isReady
}

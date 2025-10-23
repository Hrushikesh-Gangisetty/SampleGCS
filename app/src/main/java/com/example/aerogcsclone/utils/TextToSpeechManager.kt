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

        // Voice messages (Telugu)
        const val MSG_CONNECTED = "కనెక్ట్ అయింది" // Connected
        const val MSG_DISCONNECTED = "డిస్కనెక్ట్ అయింది" // Disconnected
        const val MSG_CONNECTION_FAILED = "కనెక్షన్ విఫలమైంది" // Connection failed
        const val MSG_CALIBRATION_STARTED = "కేలిబ్రేషన్ ప్రారంభమైంది" // Calibration started
        const val MSG_CALIBRATION_FINISHED = "కేలిబ్రేషన్ ముగిసింది" // Calibration finished
        const val MSG_CALIBRATION_SUCCESS = "కేలిబ్రేషన్ విజయవంతంగా పూర్తయింది" // Calibration completed successfully
        const val MSG_CALIBRATION_FAILED = "కేలిబ్రేషన్ విఫలమైంది" // Calibration failed
        const val MSG_SELECTED_AUTOMATIC = "ఆటోమేటిక్ ఎంచుకున్నారు" // Selected automatic
        const val MSG_SELECTED_MANUAL = "మాన్యువల్ ఎంచుకున్నారు" // Selected manual
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
                // Prefer Telugu locale, fall back to US English if Telugu is not supported
                val telugu = Locale("te", "IN")
                var result = tts.setLanguage(telugu)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Telugu not supported on this device, falling back to US English")
                    result = tts.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Fallback language (en-US) not supported")
                        isReady = false
                        return
                    }
                }

                Log.d(TAG, "TextToSpeech initialized successfully")
                isReady = true

                // Configure TTS settings
                tts.setSpeechRate(1.0f)
                tts.setPitch(1.0f)
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
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
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
        // Speak in Telugu: append the translated word for "calibration" (కేలిబ్రేషన్)
        speak("$calibrationType కేలిబ్రేషన్")
    }

    /**
     * Announces IMU calibration position
     * Converts position names to natural speech (e.g., LEVEL -> Telugu for "Level")
     */
    fun announceIMUPosition(position: String) {
        val spokenText = when (position.uppercase(Locale.US)) {
            "LEVEL" -> "సమంగా" // Level
            "LEFT" -> "ఎడమ" // Left
            "RIGHT" -> "కుడి" // Right
            "NOSEDOWN", "NOSE_DOWN" -> "నోస్ దిగింది" // Nose down
            "NOSEUP", "NOSE_UP" -> "నోస్ పైకి" // Nose up
            "BACK" -> "తిరగబడిన స్థితి" // Inverted down/back
            else -> position.replace("_", " ")
                .lowercase(Locale.US)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        speak(spokenText)
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

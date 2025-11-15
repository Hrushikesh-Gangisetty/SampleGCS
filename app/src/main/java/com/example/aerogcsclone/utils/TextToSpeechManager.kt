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
        const val MSG_DISCONNECTED = "డిస్కనెక్టర్ అయింది" // Disconnected
        const val MSG_CONNECTION_FAILED = "కనెక్షన్ విఫలమైంది" // Connection failed
        const val MSG_CALIBRATION_STARTED = "కేలిబ్రేషన్ ప్రారంభమైంది" // Calibration started
        const val MSG_CALIBRATION_FINISHED = "కేలిబ్రేషన్ ముగిసింది" // Calibration finished
        const val MSG_CALIBRATION_SUCCESS = "కేలిబ్రేషన్ విజయవంతంగా పూర్తయింది" // Calibration completed successfully
        const val MSG_CALIBRATION_FAILED = "కేలిబ్రేష్ విఫలమైంది" // Calibration failed (note: preserved meaning)
        const val MSG_SELECTED_AUTOMATIC = "ఆటోమేటిక్ ఎంచుకున్నారు" // Selected automatic
        const val MSG_SELECTED_MANUAL = "మాన్యువల్ ఎంచుకున్నారు" // Selected manual
        const val MSG_DRONE_ARMED = "డ్రోన్ ఆర్మ్ అయింది" // Drone armed
        const val MSG_DRONE_DISARMED = "డ్రోన్ డిసార్మ్ అయింది" // Drone disarmed
        const val MSG_COMPASS_CALIBRATION_STARTED = "కంపాస్ కేలిబ్రేషన్ ప్రారంభమైంది" // Compass calibration started
        const val MSG_COMPASS_CALIBRATION_COMPLETED = "కంపాస్ కేలిబ్రేషన్ విజయవంతంగా పూర్తయింది" // Compass calibration completed successfully
        const val MSG_COMPASS_CALIBRATION_FAILED = "కంపాస్ కేలిబ్రేషన్ విఫలమైంది" // Compass calibration failed
        const val MSG_REBOOT_DRONE = "దయచేసి మీ డ్రోన్ రీబూట్ చేయండి" // Please reboot your drone

        // --- Shared deduplication state so repeated TTS is suppressed across instances ---
        private val dedupeLock = Any()
        @JvmStatic
        private var lastSpokenText: String? = null
        @JvmStatic
        private var lastSpokenAt: Long = 0L
        @JvmStatic
        private val dedupeWindowMillis: Long = 2000L // 2 seconds cooldown for the same message

        // Per-key tracking to guarantee a message is spoken only once per logical key
        @JvmStatic
        private val spokenKeys: MutableSet<String> = Collections.synchronizedSet(HashSet())
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
                val telugu = Locale.forLanguageTag("te-IN")
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
     * Speaks the given text if TTS is ready. Prevents repeating the exact same message within a short window.
     */
    fun speak(text: String) {
        // Quick ready check
        if (!isReady || textToSpeech == null) {
            Log.w(TAG, "TTS not ready or not initialized. Cannot speak: $text")
            return
        }

        val now = System.currentTimeMillis()
        synchronized(dedupeLock) {
            if (text == lastSpokenText && (now - lastSpokenAt) < dedupeWindowMillis) {
                // Ignore repeated request within cooldown
                Log.d(TAG, "Ignoring repeated TTS for: $text")
                return
            }

            lastSpokenText = text
            lastSpokenAt = now
        }

        Log.d(TAG, "Speaking: $text")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
    }

    /**
     * Speak the given text only once per key. Subsequent calls with the same key will be ignored
     * until the key is reset via resetSpokenKey or resetAllSpoken.
     */
    fun speakOnce(key: String, text: String) {
        if (!isReady || textToSpeech == null) {
            Log.w(TAG, "TTS not ready or not initialized. Cannot speak: $text")
            return
        }

        synchronized(dedupeLock) {
            if (spokenKeys.contains(key)) {
                Log.d(TAG, "speakOnce: already spoken for key=$key")
                return
            }
            spokenKeys.add(key)
        }

        speak(text)
    }

    /**
     * Reset a specific spoken key so speakOnce can be used again for that key.
     */
    fun resetSpokenKey(key: String) {
        synchronized(dedupeLock) {
            spokenKeys.remove(key)
        }
    }

    /**
     * Clears all spoken keys so speakOnce can be used again for any key.
     */
    fun resetAllSpoken() {
        synchronized(dedupeLock) {
            spokenKeys.clear()
        }
    }

    /**
     * Allows callers to reset the dedupe state so the same message can be spoken again immediately.
     * Useful when calibration finishes or different calibration steps should re-announce the same phrase.
     */
    fun resetLastSpoken() {
        synchronized(dedupeLock) {
            lastSpokenText = null
            lastSpokenAt = 0L
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
        // Use immediate speak for calibration start to guarantee audible feedback even if dedupe
        // would otherwise suppress it (e.g., UI pressed multiple times).
        speakImmediate(MSG_CALIBRATION_STARTED)
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
     * Announces drone armed status
     */
    fun announceDroneArmed() {
        speak(MSG_DRONE_ARMED)
    }

    /**
     * Announces drone disarmed status
     */
    fun announceDroneDisarmed() {
        speak(MSG_DRONE_DISARMED)
    }

    /**
     * Announces compass calibration started
     */
    fun announceCompassCalibrationStarted() {
        speakImmediate(MSG_COMPASS_CALIBRATION_STARTED)
    }

    /**
     * Announces compass calibration completed successfully
     */
    fun announceCompassCalibrationCompleted() {
        speak(MSG_COMPASS_CALIBRATION_COMPLETED)
    }

    /**
     * Announces compass calibration failed
     */
    fun announceCompassCalibrationFailed() {
        speak(MSG_COMPASS_CALIBRATION_FAILED)
    }

    /**
     * Announces reboot drone message
     */
    fun announceRebootDrone() {
        speak(MSG_REBOOT_DRONE)
    }

    /**
     * Announces calibration type when entering calibration screens
     */
    fun announceCalibration(calibrationType: String) {
        // Speak in Telugu: append the translated word for "calibration" (కేలిబ్రేషన్)
        // Use grammatically correct Telugu: "<type> కేలిబ్రేషన్ ప్రారంభమైంది"
        speak("$calibrationType కేలిబ్రేషన్ ప్రారంభమైంది")
    }

    /**
     * Announces IMU calibration position
     * Converts position names to natural speech (e.g., LEVEL -> Telugu for "Level")
     */
    fun announceIMUPosition(position: String) {
        val spokenText = when (position.uppercase(Locale.US)) {
            "LEVEL" -> "అన్ని వైపులా సమానంగా పెంటండి" // Level
            "LEFT" -> "ఎడమ" // Left
            "RIGHT" -> "కుడి" // Right
            "NOSEDOWN", "NOSE_DOWN" -> "నోస్ కిందకి పెంటండి" // Nose down
            "NOSEUP", "NOSE_UP" -> "నోస్ పైకి పెంటండి" // Nose up
            "BACK" -> "వెనక్కి తిప్పండి" // Inverted down/back
            else -> position.replace("_", " ")
                .lowercase(Locale.US)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        speak(spokenText)
    }

    /**
     * Announces IMU calibration position only once per position key.
     * Useful to avoid repeated announcements when user clicks Next/Start multiple times.
     */
    fun announceIMUPositionOnce(position: String) {
        val spokenText = when (position.uppercase(Locale.US)) {
            "LEVEL" -> "అన్ని వైపులా సమానంగా పెంటండి" // Level
            "LEFT" -> "ఎడమ వైపు పెంటండి " // Left
            "RIGHT" -> "కుడి వైపు పెంటండి" // Right
            "NOSEDOWN", "NOSE_DOWN" -> "నోస్ కిందకి పెంటండి" // Nose down
            "NOSEUP", "NOSE_UP" -> "నోస్ పైకి పెంటండి" // Nose up
            "BACK" -> "వెనక్కి తిప్పండి" // Inverted down/back
            else -> position.replace("_", " ")
                .lowercase(Locale.US)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        // Use a stable key per position so repeated UI actions won't replay the same phrase
        val key = "IMU_POS_${position.uppercase(Locale.US)}"
        speakOnce(key, spokenText)
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

    /**
     * Speak immediately bypassing the time-based and key-based dedupe logic.
     * This still updates the dedupe state so subsequent calls in the short cooldown
     * won't cause another immediate playback.
     */
    fun speakImmediate(text: String) {
        if (!isReady || textToSpeech == null) {
            Log.w(TAG, "TTS not ready or not initialized. Cannot speak immediate: $text")
            return
        }

        val now = System.currentTimeMillis()
        // Update dedupe state to reflect this utterance, so normal speak() won't replay it
        synchronized(dedupeLock) {
            lastSpokenText = text
            lastSpokenAt = now
            // Also mark a generic spoken key so speakOnce won't replay same logical key
            try {
                spokenKeys.add(text)
            } catch (_: Exception) {
            }
        }

        Log.d(TAG, "Speaking immediately: $text")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
    }
}

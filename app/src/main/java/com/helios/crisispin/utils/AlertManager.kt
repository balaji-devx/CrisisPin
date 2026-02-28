package com.helios.crisispin.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AlertManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Debounce — track last time each message was announced
    private val lastAlertTime = mutableMapOf<String, Long>()
    private val cooldownMs = 10_000L // 10 seconds between repeat alerts

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e("AlertManager", "TTS language not supported")
                } else {
                    isTtsReady = true
                    Log.d("AlertManager", "TTS ready")
                }
            } else {
                Log.e("AlertManager", "TTS init failed")
            }
        }
    }

    fun triggerAlert(message: String) {
        val now = System.currentTimeMillis()
        val lastTime = lastAlertTime[message] ?: 0L

        // Ignore if we already alerted for this message within the cooldown window
        if (now - lastTime < cooldownMs) {
            Log.d("AlertManager", "Alert debounced — too soon since last '$message' alert")
            return
        }

        lastAlertTime[message] = now
        vibrate()
        speak(message)
    }

    private fun vibrate() {
        val pattern = longArrayOf(
            0,
            200, 100,
            200, 100,
            200, 300,
            500, 100,
            500, 100,
            500, 300,
            200, 100,
            200, 100,
            200, 100
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = intArrayOf(
                0,
                255, 0,
                255, 0,
                255, 0,
                255, 0,
                255, 0,
                255, 0,
                255, 0,
                255, 0,
                255, 0
            )
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun speak(message: String) {
        if (!isTtsReady) {
            Log.e("AlertManager", "TTS not ready yet")
            return
        }

        val announcement = when (message.uppercase()) {
            "SOS" -> "Emergency SOS alert received! Someone nearby needs help!"
            "FIRE" -> "Fire alert received! There is a fire nearby!"
            "MED" -> "Medical emergency alert received! Someone needs medical attention!"
            else -> "Emergency alert received: $message"
        }

        tts?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "alert_utterance")
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
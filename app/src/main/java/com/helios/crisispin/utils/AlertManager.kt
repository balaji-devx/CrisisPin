package com.helios.crisispin.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AlertManager(context: Context) {

    // Always applicationContext — avoids leaks when constructed from a Service
    private val appContext: Context = context.applicationContext

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // FIX 3: Single source of truth for flags — no more redundant params in triggerAlert()
    private var soundEnabled = true
    private var vibrationEnabled = true

    private val lastAlertTime = mutableMapOf<String, Long>()
    private val cooldownMs = 5_000L

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d("AlertManager", "TTS ready: $isTtsReady")
            } else {
                Log.e("AlertManager", "TTS init failed: $status")
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        if (!enabled) tts?.stop()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
        if (!enabled) try { getVibrator()?.cancel() } catch (e: Exception) { }
    }

    // FIX 3: Removed redundant playSound/doVibrate params — internal flags are the source of truth
    fun triggerAlert(message: String) {
        val now = System.currentTimeMillis()
        if (now - (lastAlertTime[message] ?: 0L) < cooldownMs) {
            Log.d("AlertManager", "Backup cooldown: skipping '$message'")
            return
        }
        lastAlertTime[message] = now
        Log.d("AlertManager", "Triggering alert: $message (sound=$soundEnabled vib=$vibrationEnabled)")

        if (vibrationEnabled) vibrate()
        if (soundEnabled) speak(message)
    }

    private fun getVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    } catch (e: Exception) {
        Log.e("AlertManager", "getVibrator failed: ${e.message}")
        null
    }

    private fun vibrate() {
        val vibrator = getVibrator()
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.e("AlertManager", "Vibrator unavailable")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Arrays must be same length — mismatch causes silent failure
                val timings    = longArrayOf(0, 400, 200, 400, 200, 400)
                val amplitudes = intArrayOf(  0, 255,   0, 255,   0, 255)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    // Xiaomi chips without amplitude control — simple oneshot always works
                    VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
                Log.d("AlertManager", "Vibration triggered")
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), -1)
            }
        } catch (e: SecurityException) {
            Log.e("AlertManager", "VIBRATE permission missing from manifest!")
        } catch (e: Exception) {
            Log.e("AlertManager", "Vibration exception: ${e.message}")
        }
    }

    private fun speak(message: String) {
        if (!isTtsReady) { Log.e("AlertManager", "TTS not ready"); return }
        val text = when (message.uppercase().trim()) {
            "SOS"   -> "Emergency! S O S alert received. Someone nearby needs help!"
            "FIRE"  -> "Warning! Fire alert received. There is a fire nearby!"
            "MED"   -> "Medical emergency! Someone nearby needs medical attention!"
            "PANIC" -> "Alert! Someone nearby is in danger!"
            "HELP"  -> "Attention! Someone nearby needs assistance!"
            else    -> "Emergency alert received."
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "crisispin_alert")
        Log.d("AlertManager", "TTS speaking: $text")
    }

    fun release() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
    }
}
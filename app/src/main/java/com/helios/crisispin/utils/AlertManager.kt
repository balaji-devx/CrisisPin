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

    private val ctx = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var soundEnabled = true
    private var vibrationEnabled = true

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d("AlertManager", "TTS ready=$ttsReady")
            }
        }
    }

    fun setSoundEnabled(e: Boolean)     { soundEnabled = e;     if (!e) tts?.stop() }
    fun setVibrationEnabled(e: Boolean) { vibrationEnabled = e; if (!e) vibrator()?.cancel() }

    fun triggerAlert(msg: String, playSound: Boolean = soundEnabled, doVibrate: Boolean = vibrationEnabled) {
        Log.d("AlertManager", "triggerAlert msg=$msg sound=$playSound vib=$doVibrate")
        if (doVibrate && vibrationEnabled) vibrate()
        if (playSound && soundEnabled) speak(msg)
    }

    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else
            @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    } catch (e: Exception) { null }

    private fun vibrate() {
        val v = vibrator()
        if (v == null || !v.hasVibrator()) { Log.e("AlertManager", "No vibrator"); return }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (v.hasAmplitudeControl()) {
                    v.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 150, 400, 150, 400),
                        intArrayOf(  0, 255,   0, 255,   0, 255), -1))
                } else {
                    v.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 400, 150, 400, 150, 400), -1)
            }
            Log.d("AlertManager", "Vibrated ✓")
        } catch (e: Exception) { Log.e("AlertManager", "Vibrate failed: ${e.message}") }
    }

    private fun speak(msg: String) {
        if (!ttsReady) { Log.e("AlertManager", "TTS not ready"); return }
        val text = when (msg.uppercase()) {
            "SOS"   -> "Emergency! Someone nearby needs immediate help!"
            "MED"   -> "Medical emergency nearby! Someone needs medical attention!"
            "FIRE"  -> "Fire alert! There is a fire nearby!"
            "PANIC" -> "Alert! Someone nearby is in danger!"
            "HELP"  -> "Someone nearby needs assistance!"
            else    -> "Emergency alert received."
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cp_alert")
    }

    fun release() { try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }; tts = null }
}
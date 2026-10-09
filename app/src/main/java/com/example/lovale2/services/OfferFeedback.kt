package com.example.lovale2.services

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import com.example.lovale2.domain.DriverOptions

object OfferFeedback {
    private var last = -5000L
    fun play(context: Context, options: DriverOptions) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 5000 || options.quietAt() || (!options.sound && !options.vibration)) return
        if (context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        last = now
        try {
            if (options.sound && context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
            }
            if (options.vibration && context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_SILENT) {
                @Suppress("DEPRECATION") val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                else { @Suppress("DEPRECATION") vibrator.vibrate(120) }
            }
        } catch (_: Exception) { /* Feedback must never block offer evaluation. */ }
    }
}

package com.trio.today.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.trio.today.R

/**
 * The "success moment".
 *
 * The report names this the single most important design lever for the app
 * (§Key Findings 4, §4): ADHD brains run on immediate reward, and completion
 * feedback is a solved craft worth copying outright -- sound-matched haptics,
 * a short animation, then out of the way.
 *
 * The Peak-End rule is the reason the highest-fidelity feedback is spent here
 * and nowhere else. Adding a flourish to capture or navigation would dilute the
 * one moment that has to feel good enough to bring someone back tomorrow.
 *
 * Sound and haptics are prepared once and fired together so they land in the
 * same instant -- a delay between them reads as jank rather than delight.
 */
class CompletionFeedback(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var completeSoundId: Int = 0
    private var loaded = false

    init {
        runCatching {
            completeSoundId = soundPool.load(context, R.raw.complete, 1)
            soundPool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        }
    }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }

    /**
     * Fires the completion moment.
     *
     * The haptic is a two-beat figure -- a firm tap, a short gap, then a softer
     * one -- that mirrors the two-note sound. A single buzz reads as a
     * notification; two beats read as an acknowledgement.
     */
    fun celebrate(withSound: Boolean, withHaptics: Boolean) {
        if (withSound && loaded) {
            runCatching { soundPool.play(completeSoundId, 0.6f, 0.6f, 1, 0, 1f) }
        }
        if (withHaptics) vibrateSuccess()
    }

    /** A light tick for a swipe crossing the completion threshold. */
    fun tick(withHaptics: Boolean) {
        if (!withHaptics) return
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        }
    }

    private fun vibrateSuccess() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // Amplitudes give the figure its shape: a confident first
                    // beat, then a lighter echo.
                    val timings = longArrayOf(0, 28, 60, 46)
                    val amplitudes = intArrayOf(0, 210, 0, 130)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 28, 60, 46), -1)
                }
            }
        }
    }

    fun release() {
        runCatching { soundPool.release() }
    }
}

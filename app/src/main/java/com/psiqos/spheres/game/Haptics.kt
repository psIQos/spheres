package com.psiqos.spheres.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short vibrations through the vibration motor. View.performHapticFeedback is not
 * used: it depends on the system "touch feedback" setting and its tick is too weak
 * to notice on many phones.
 */
class Haptics(context: Context) {

    var enabled = true

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val amplitude = vibrator?.hasAmplitudeControl() == true

    /** A dot was added to or removed from the path: short and light. */
    fun tick() = oneShot(millis = 14, strength = 110)

    /** A square was closed: two strong pulses. */
    fun square() = waveform(longArrayOf(0, 35, 60, 45), intArrayOf(0, 255, 0, 255))

    /** The round is over. */
    fun gameOver() = oneShot(millis = 120, strength = 180)

    private fun oneShot(millis: Long, strength: Int) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(millis, if (amplitude) strength else VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun waveform(timings: LongArray, strengths: IntArray) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        if (amplitude) {
            v.vibrate(VibrationEffect.createWaveform(timings, strengths, -1))
        } else {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }
}

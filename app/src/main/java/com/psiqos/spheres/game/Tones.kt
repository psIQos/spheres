package com.psiqos.spheres.game

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Renders the game's "plink" notes as 16-bit mono PCM. Plain Kotlin, so it is unit tested. */
object Tones {
    const val SAMPLE_RATE = 44100

    /** Frequency of the note [semitones] above C5. */
    fun freq(semitones: Int): Double = 523.25 * 2.0.pow(semitones / 12.0)

    /** Note index along a major pentatonic scale starting at C5. */
    fun pentatonic(index: Int): Double {
        val steps = intArrayOf(0, 2, 4, 7, 9)
        return freq(steps[index % steps.size] + 12 * (index / steps.size))
    }

    /**
     * A soft bell-like tone: short attack, exponential decay and a fade to exact
     * silence at the end, so the sound never stops with a click.
     */
    fun render(frequencies: DoubleArray, seconds: Double, volume: Double = 0.3): ShortArray {
        val count = (SAMPLE_RATE * seconds).toInt()
        val fadeOut = (SAMPLE_RATE * 0.05).toInt()
        val pcm = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.006).coerceAtMost(1.0)
            val release = ((count - 1 - i).toDouble() / fadeOut).coerceIn(0.0, 1.0)
            val envelope = attack * exp(-t * 7.0) * release
            var v = 0.0
            for (f in frequencies) {
                v += sin(2 * PI * f * t) + 0.2 * sin(4 * PI * f * t) * exp(-t * 14.0)
            }
            v /= frequencies.size * 1.2
            pcm[i] = (v * envelope * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return pcm
    }

    /** Wraps PCM data in a WAV file. */
    fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }
        return ByteArrayOutputStream().apply { write(header.array()); write(data.array()) }.toByteArray()
    }
}

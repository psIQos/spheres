package com.psiqos.spheres.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Tiny synthesizer for the rising "plink" notes played while connecting dots.
 * Notes are rendered once into static [AudioTrack]s so playback has no latency.
 */
object Sound {
    private const val SAMPLE_RATE = 44100
    private const val NOTE_COUNT = 14

    var enabled = true

    private var notes: Array<AudioTrack?> = arrayOfNulls(NOTE_COUNT)
    private var square: AudioTrack? = null
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        // Major pentatonic scale starting at C5.
        val steps = intArrayOf(0, 2, 4, 7, 9)
        for (i in 0 until NOTE_COUNT) {
            val semitone = steps[i % steps.size] + 12 * (i / steps.size)
            notes[i] = createTrack(floatArrayOf(freq(semitone)), 0.22f)
        }
        // Square: a bright major chord an octave up.
        square = createTrack(floatArrayOf(freq(12), freq(16), freq(19), freq(24)), 0.5f)
    }

    fun release() {
        notes.forEach { it?.release() }
        notes = arrayOfNulls(NOTE_COUNT)
        square?.release()
        square = null
        loaded = false
    }

    fun playNote(index: Int) = play(notes.getOrNull(index.coerceIn(0, NOTE_COUNT - 1)))

    fun playSquare() = play(square)

    private fun play(track: AudioTrack?) {
        if (!enabled || track == null) return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: IllegalStateException) {
        }
    }

    private fun freq(semitonesAboveC5: Int): Float = (523.25 * 2.0.pow(semitonesAboveC5 / 12.0)).toFloat()

    private fun createTrack(frequencies: FloatArray, seconds: Float): AudioTrack? {
        val count = (SAMPLE_RATE * seconds).toInt()
        val pcm = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.004).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 9.0)
            var v = 0.0
            for (f in frequencies) {
                v += sin(2 * PI * f * t) + 0.25 * sin(4 * PI * f * t)
            }
            v /= frequencies.size
            pcm[i] = (v * envelope * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(count * 2)
                .build()
                .also { it.write(pcm, 0, count) }
        } catch (_: Exception) {
            null
        }
    }
}

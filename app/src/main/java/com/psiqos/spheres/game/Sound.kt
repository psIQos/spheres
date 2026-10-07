package com.psiqos.spheres.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/**
 * Plays the rising notes while connecting dots. The notes are rendered once into
 * WAV files and played through a [SoundPool]: low latency, and a new note does not
 * cut off the previous one (which would click).
 */
object Sound {
    private const val NOTE_COUNT = 14

    var enabled = true

    private var pool: SoundPool? = null
    private val notes = IntArray(NOTE_COUNT)
    private var square = 0
    private var freeze = 0
    private var thaw = 0

    fun load(context: Context) {
        if (pool != null) return
        val pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        this.pool = pool
        val dir = File(context.cacheDir, "tones").apply { mkdirs() }
        fun loadTone(name: String, pcm: ShortArray): Int {
            val file = File(dir, "$name.wav")
            file.writeBytes(Tones.wav(pcm))
            return pool.load(file.path, 1)
        }
        for (i in 0 until NOTE_COUNT) {
            notes[i] = loadTone("note$i", Tones.render(doubleArrayOf(Tones.pentatonic(i)), 0.35))
        }
        // Square: a bright major chord an octave up.
        square = loadTone(
            "square",
            Tones.render(doubleArrayOf(Tones.freq(12), Tones.freq(16), Tones.freq(19), Tones.freq(24)), 0.6),
        )
        // Time stop: an icy glide down when the clock freezes, a short one up when it thaws.
        freeze = loadTone("freeze", Tones.sweep(Tones.freq(24), Tones.freq(7), 0.7))
        thaw = loadTone("thaw", Tones.sweep(Tones.freq(0), Tones.freq(12), 0.3, decay = 2.0))
    }

    fun release() {
        pool?.release()
        pool = null
    }

    fun playNote(index: Int) = play(notes[index.coerceIn(0, NOTE_COUNT - 1)])

    fun playSquare() = play(square)

    fun playFreeze() = play(freeze)

    fun playThaw() = play(thaw)

    private fun play(id: Int) {
        if (!enabled || id == 0) return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }
}

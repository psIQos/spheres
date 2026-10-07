package com.psiqos.spheres.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TonesTest {

    private val tones = listOf(
        Tones.render(doubleArrayOf(Tones.pentatonic(0)), 0.35),
        Tones.render(doubleArrayOf(Tones.pentatonic(13)), 0.35),
        Tones.render(doubleArrayOf(Tones.freq(12), Tones.freq(16), Tones.freq(19), Tones.freq(24)), 0.6),
        Tones.sweep(Tones.freq(24), Tones.freq(7), 0.7),
        Tones.sweep(Tones.freq(0), Tones.freq(12), 0.3, decay = 2.0),
    )

    /** An abrupt end of a sample is heard as a click. */
    @Test
    fun endsInSilence() {
        for (pcm in tones) {
            assertEquals(0, pcm.last().toInt())
            val tail = pcm.takeLast(Tones.SAMPLE_RATE / 200) // last 5 ms
            assertTrue("tail too loud: ${tail.maxOf { abs(it.toInt()) }}", tail.all { abs(it.toInt()) < 300 })
        }
    }

    @Test
    fun startsSoftly() {
        for (pcm in tones) assertTrue(abs(pcm[1].toInt()) < 300)
    }

    /** Clipping distorts ("grisselig"). */
    @Test
    fun doesNotClip() {
        for (pcm in tones) {
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("peak $peak", peak in 3000..(Short.MAX_VALUE * 0.6).toInt())
        }
    }

    @Test
    fun wavHeader() {
        val wav = Tones.wav(ShortArray(10))
        assertEquals(44 + 20, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
    }
}

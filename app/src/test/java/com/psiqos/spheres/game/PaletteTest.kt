package com.psiqos.spheres.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteTest {

    /** WCAG relative luminance of an opaque ARGB color. */
    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = (color shr shift and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (dark, light) = listOf(luminance(a), luminance(b)).sorted()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun nightDotsStandOutOnBlack() {
        val black = 0xFF000000.toInt()
        for (c in Palette.colors(night = true)) {
            assertTrue("contrast of %08X: %.1f".format(c, contrast(c, black)), contrast(c, black) >= 7.0)
        }
    }

    @Test
    fun nightDotsAreLighterVariantsOfTheDayDots() {
        val day = Palette.colors(night = false)
        val night = Palette.colors(night = true)
        assertEquals(day.size, night.size)
        for (i in day.indices) assertTrue("dot $i", luminance(night[i]) > luminance(day[i]))
    }

    @Test
    fun dotFollowsTheShownDesign() {
        try {
            Palette.night = true
            assertEquals(Palette.colors(night = true)[3], Palette.dot(3))
            Palette.night = false
            assertEquals(Palette.colors(night = false)[3], Palette.dot(3))
        } finally {
            Palette.night = false
        }
    }
}

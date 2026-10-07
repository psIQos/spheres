package com.psiqos.spheres.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

class PaletteTest {

    @After
    fun reset() {
        Palette.colorblind = false
    }

    /** How a color is seen: identity, or a Machado et al. (2009) simulation of full color blindness. */
    private val visions = mapOf(
        "normal" to doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
        "protanopia" to doubleArrayOf(
            0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998,
        ),
        "deuteranopia" to doubleArrayOf(
            0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881,
        ),
        "tritanopia" to doubleArrayOf(
            1.255528, -0.076749, -0.178779, -0.078411, 0.930809, 0.147602, 0.004733, 0.691367, 0.303900,
        ),
    )

    private fun linear(color: Int, shift: Int): Double {
        val c = (color shr shift and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** CIE L*a*b* of [color] as seen with [vision]. */
    private fun lab(color: Int, vision: DoubleArray): DoubleArray {
        val rgb = doubleArrayOf(linear(color, 16), linear(color, 8), linear(color, 0))
        val (r, g, b) = DoubleArray(3) { i ->
            (vision[3 * i] * rgb[0] + vision[3 * i + 1] * rgb[1] + vision[3 * i + 2] * rgb[2]).coerceIn(0.0, 1.0)
        }
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    /** Perceived difference (CIE76 ΔE) of two colors as seen with [vision]. */
    private fun distance(a: Int, b: Int, vision: DoubleArray): Double {
        val la = lab(a, vision)
        val lb = lab(b, vision)
        return sqrt((0..2).sumOf { (la[it] - lb[it]).pow(2) })
    }

    private fun closestPair(colors: IntArray, vision: DoubleArray): Double =
        colors.indices.minOf { i -> (i + 1 until colors.size).minOfOrNull { j -> distance(colors[i], colors[j], vision) } ?: Double.MAX_VALUE }

    @Test
    fun colorblindPaletteStaysDistinctForEveryVision() {
        for ((name, vision) in visions) {
            val closest = closestPair(Palette.COLORBLIND, vision)
            assertTrue("$name: closest colors differ by ΔE $closest", closest >= 30)
        }
    }

    /** The reason for the colorblind palette: blue and purple of the standard palette look alike. */
    @Test
    fun colorblindPaletteSeparatesWhatTheStandardPaletteMixesUp() {
        val blue = 3
        val purple = 4
        for (name in listOf("protanopia", "deuteranopia")) {
            val vision = visions.getValue(name)
            val standard = distance(Palette.STANDARD[blue], Palette.STANDARD[purple], vision)
            val colorblind = distance(Palette.COLORBLIND[blue], Palette.COLORBLIND[purple], vision)
            assertTrue("$name: blue/purple ΔE $standard -> $colorblind", colorblind > 2 * standard)
        }
    }

    @Test
    fun palettesKeepTheirMeaning() {
        assertEquals(Palette.STANDARD.size, Palette.COLORBLIND.size)
        assertEquals(Palette.STANDARD.size, Symbol.entries.size)
        Palette.colorblind = true
        assertEquals(Palette.COLORBLIND[3], Palette.dot(3))
        assertEquals(Palette.COLORBLIND[0], Palette.dot(Palette.COLORBLIND.size))
        Palette.colorblind = false
        assertEquals(Palette.STANDARD[3], Palette.dot(3))
    }

    @Test
    fun everyColorHasItsOwnSymbol() {
        val symbols = Palette.STANDARD.indices.map(Palette::symbol)
        assertEquals(symbols.size, symbols.toSet().size)
    }

    /** Symbols are graphical objects: WCAG asks for a contrast of at least 3:1 to the dot. */
    @Test
    fun symbolsContrastWithTheirDot() {
        for (color in Palette.STANDARD + Palette.COLORBLIND) {
            val contrast = Palette.contrast(color, Palette.ink(color))
            assertTrue("symbol on %08X has contrast %.2f".format(color, contrast), contrast >= 3.0)
        }
    }

    @Test
    fun withAlphaKeepsTheColor() {
        assertEquals(0x405CA8EC, Palette.withAlpha(Palette.STANDARD[3], 0x40))
        assertEquals(0x005CA8EC, Palette.withAlpha(Palette.STANDARD[3], -5))
    }
}

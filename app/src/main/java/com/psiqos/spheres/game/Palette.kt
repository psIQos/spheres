package com.psiqos.spheres.game

import kotlin.math.pow

/** Shape drawn on a dot so colors can be told apart without seeing them. */
enum class Symbol { TRIANGLE, DOT, SQUARE, PLUS, RING, STAR }

/** Dot colors. Android-free, so the colors can be checked in unit tests. */
object Palette {
    val STANDARD = intArrayOf(
        0xFFEC5B57.toInt(), // red
        0xFFF4C842.toInt(), // yellow
        0xFF83D66A.toInt(), // green
        0xFF5CA8EC.toInt(), // blue
        0xFF9E6CDB.toInt(), // purple
        0xFF27B9A6.toInt(), // teal (hard only), in the widest hue gap between green and blue
    )

    /**
     * Same hues in the same order, chosen so that all six stay far apart in lightness or hue
     * also for red-green (protan, deutan) and blue-yellow (tritan) color blindness.
     */
    val COLORBLIND = intArrayOf(
        0xFFEE1A10.toInt(), // red
        0xFFFFCD00.toInt(), // yellow
        0xFF88B871.toInt(), // green
        0xFF224DCE.toInt(), // blue
        0xFFB00864.toInt(), // purple (magenta)
        0xFF06E0FF.toInt(), // cyan
    )

    /** Switched in the settings. */
    var colorblind = false

    fun dot(index: Int): Int {
        val dots = if (colorblind) COLORBLIND else STANDARD
        return dots[index.mod(dots.size)]
    }

    fun symbol(index: Int): Symbol = Symbol.entries[index.mod(Symbol.entries.size)]

    /** Color of a symbol on a dot of [color]: dark on light dots, white on dark ones. */
    fun ink(color: Int): Int = if (contrast(color, WHITE) >= contrast(color, DARK)) WHITE else DARK

    fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    /** Relative luminance (WCAG 2) of an opaque color. */
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = (color shr shift and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Contrast ratio (WCAG 2) between two opaque colors, from 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DARK = 0xFF2E2E33.toInt()
}

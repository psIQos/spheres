package com.psiqos.spheres.game

/**
 * Pattern on a dot, so that colors can be told apart without seeing them (accessibility,
 * switched on in the settings). Each dot color has its own pattern.
 */
enum class DotPattern {
    SOLID, HORIZONTAL_STRIPES, DIAGONAL_STRIPES, RINGS, SPOTS, CROSSHATCH;

    companion object {
        /** Red plain, yellow horizontal stripes, green diagonal stripes, blue rings, purple spots, teal crosshatch. */
        private val byColor = listOf(SOLID, HORIZONTAL_STRIPES, DIAGONAL_STRIPES, RINGS, SPOTS, CROSSHATCH)

        fun forColor(index: Int): DotPattern = byColor[index.mod(byColor.size)]
    }
}

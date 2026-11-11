package com.psiqos.spheres.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DotPatternTest {

    @Test
    fun everyColorHasItsOwnPattern() {
        val patterns = (0 until 6).map { DotPattern.forColor(it) }
        assertEquals(6, patterns.toSet().size)
    }

    @Test
    fun redStaysPlain() {
        assertEquals(DotPattern.SOLID, DotPattern.forColor(0))
    }
}

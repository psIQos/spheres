package com.psiqos.spheres

import com.psiqos.spheres.game.Cell
import com.psiqos.spheres.game.DoubleTap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapTest {

    @Test
    fun twoQuickTapsOnTheSameDot() {
        val d = DoubleTap(300)
        assertFalse(d.tap(Cell(2, 3), 1000, 1080))
        assertTrue(d.tap(Cell(2, 3), 1300, 1370))
    }

    @Test
    fun tooSlowOrAnotherDotIsNoDoubleTap() {
        val d = DoubleTap(300)
        assertFalse(d.tap(Cell(2, 3), 1000, 1080))
        assertFalse(d.tap(Cell(2, 3), 1400, 1450)) // 320 ms after the first tap ended
        assertFalse(d.tap(Cell(2, 4), 1500, 1550))
        assertTrue(d.tap(Cell(2, 4), 1600, 1650))
    }

    @Test
    fun aThirdTapStartsOver() {
        val d = DoubleTap(300)
        d.tap(Cell(0, 0), 0, 50)
        assertTrue(d.tap(Cell(0, 0), 100, 150))
        assertFalse(d.tap(Cell(0, 0), 200, 250))
        assertTrue(d.tap(Cell(0, 0), 300, 350))
    }

    @Test
    fun resetForgetsTheFirstTap() {
        val d = DoubleTap(300)
        d.tap(Cell(1, 1), 0, 50)
        d.reset()
        assertFalse(d.tap(Cell(1, 1), 100, 150))
    }
}

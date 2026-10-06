package com.psiqos.spheres.game

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Replays finger gestures as a phone delivers them: a series of touch samples.
 * A fast swipe gives only a few samples per dot, and a finger never follows the
 * line exactly, so the samples wobble sideways.
 */
class PathTrackerTest {

    private val cell = 100f

    // Row 2 is red (0) all the way; column 5 continues red downwards. The rest has no red.
    private fun board() = Board(6, 6, 4, Random(1)).apply {
        setColors(
            arrayOf(
                intArrayOf(1, 2, 3, 1, 2, 3),
                intArrayOf(2, 3, 1, 2, 3, 1),
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(3, 1, 2, 3, 1, 0),
                intArrayOf(1, 2, 3, 1, 2, 0),
                intArrayOf(2, 3, 1, 2, 3, 1),
            )
        )
    }

    private fun tracker(board: Board) = PathTracker(board).apply { cellSize = cell }

    /** Center of a cell in pixels, for (row, col) given as floats so waypoints can lie between dots. */
    private fun px(row: Float, col: Float) = (col + 0.5f) * cell to (row + 0.5f) * cell

    /**
     * Moves the finger along [waypoints] (row, col) with one touch sample every
     * [spacing] cells, wobbling up to [wobble] cells sideways.
     */
    private fun PathTracker.swipe(
        waypoints: List<Pair<Float, Float>>,
        spacing: Float,
        wobble: Float = 0f,
        seed: Int = 0,
    ) {
        val rnd = Random(seed)
        val phase = rnd.nextFloat() * 6f
        var travelled = 0f
        for (i in 1 until waypoints.size) {
            val (r0, c0) = waypoints[i - 1]
            val (r1, c1) = waypoints[i]
            val length = hypot(r1 - r0, c1 - c0)
            val n = max(1, (length / spacing).toInt())
            for (k in 1..n) {
                val t = k / n.toFloat()
                travelled += length / n
                // Perpendicular offset, smooth like a real finger, not white noise.
                val off = wobble * sin(travelled * 2.1f + phase)
                val nr = (c1 - c0) / length
                val nc = -(r1 - r0) / length
                val (x, y) = px(r0 + (r1 - r0) * t + nr * off, c0 + (c1 - c0) * t + nc * off)
                moveTo(x, y) { _, _ -> }
            }
        }
    }

    private fun row(vararg cols: Int) = cols.map { 2f to it.toFloat() }

    @Test
    fun forwardAcrossRow() {
        val b = board()
        val t = tracker(b)
        t.begin(px(2f, 0f).first, px(2f, 0f).second)
        t.swipe(row(0, 5), spacing = 0.25f)
        assertEquals(6, b.path.size)
    }

    @Test
    fun fastSloppyForward() {
        for (seed in 0 until 20) {
            val b = board()
            val t = tracker(b)
            t.begin(px(2f, 0f).first, px(2f, 0f).second)
            t.swipe(row(0, 5), spacing = 0.8f, wobble = 0.3f, seed = seed)
            assertEquals("seed $seed", 6, b.path.size)
        }
    }

    @Test
    fun slowExactBacktrack() {
        val b = board()
        val t = tracker(b)
        t.begin(px(2f, 0f).first, px(2f, 0f).second)
        t.swipe(row(0, 5), spacing = 0.25f)
        t.swipe(row(5, 0), spacing = 0.25f)
        assertEquals(1, b.path.size)
    }

    /** The reported problem: swiping back quickly over the line should undo the whole path. */
    @Test
    fun fastSloppyBacktrack() {
        var failures = 0
        for (seed in 0 until 50) {
            val b = board()
            val t = tracker(b)
            t.begin(px(2f, 0f).first, px(2f, 0f).second)
            t.swipe(row(0, 5), spacing = 0.25f)
            t.swipe(row(5, 0), spacing = 0.8f, wobble = 0.5f, seed = seed)
            if (b.path.size != 1) failures++
        }
        assertEquals("gestures out of 50 that did not undo the path", 0, failures)
    }

    /** Back along an L-shaped path, around the corner. */
    @Test
    fun fastBacktrackAroundCorner() {
        var failures = 0
        for (seed in 0 until 50) {
            val b = board()
            val t = tracker(b)
            t.begin(px(2f, 2f).first, px(2f, 2f).second)
            t.swipe(listOf(2f to 2f, 2f to 5f, 4f to 5f), spacing = 0.25f)
            check(b.path.size == 6)
            t.swipe(listOf(4f to 5f, 2f to 5f, 2f to 2f), spacing = 0.8f, wobble = 0.35f, seed = seed)
            if (b.path.size != 1) failures++
        }
        assertEquals("gestures out of 50 that did not undo the path", 0, failures)
    }

    /** Partly back: the path must end where the finger stops. */
    @Test
    fun partialFastBacktrack() {
        for (seed in 0 until 20) {
            val b = board()
            val t = tracker(b)
            t.begin(px(2f, 0f).first, px(2f, 0f).second)
            t.swipe(row(0, 5), spacing = 0.25f)
            t.swipe(row(5, 2), spacing = 0.8f, wobble = 0.35f, seed = seed)
            assertEquals("seed $seed", listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2)), b.path)
        }
    }

    /** Closing a square with a fast, sloppy circle, then backing out of it again. */
    @Test
    fun sloppySquareAndBackOut() {
        for (seed in 0 until 20) {
            val b = Board(3, 3, 3, Random(1)).apply {
                setColors(arrayOf(intArrayOf(0, 0, 1), intArrayOf(0, 0, 2), intArrayOf(1, 2, 1)))
            }
            val t = tracker(b)
            t.begin(px(0f, 0f).first, px(0f, 0f).second)
            t.swipe(listOf(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f, 0f to 0f), spacing = 0.7f, wobble = 0.3f, seed = seed)
            assertEquals("seed $seed square", true, b.isSquare)
            t.swipe(listOf(0f to 0f, 1f to 0f, 1f to 1f), spacing = 0.7f, wobble = 0.3f, seed = seed)
            assertEquals("seed $seed back", listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)), b.path)
        }
    }

    /** Passing close beside a same-colored dot that is not on the path must not pick it up. */
    @Test
    fun passingBesideDotDoesNotConnect() {
        val b = board()
        val t = tracker(b)
        t.begin(px(2f, 3f).first, px(2f, 3f).second)
        // Along row 2 towards (2,5), 0.55 cells below the line: (3,5) is red and close by.
        t.swipe(listOf(2f to 3f, 2.55f to 4f, 2.55f to 5f), spacing = 0.2f)
        assertEquals(false, Cell(3, 5) in b.path)
    }

    /** A diagonal move must not pick up the dots beside the diagonal. */
    @Test
    fun diagonalDoesNotConnect() {
        val b = Board(2, 2, 2, Random(1)).apply { setColors(arrayOf(intArrayOf(0, 0), intArrayOf(0, 0))) }
        val t = tracker(b)
        t.begin(px(0f, 0f).first, px(0f, 0f).second)
        t.swipe(listOf(0f to 0f, 1f to 1f), spacing = 0.1f)
        assertEquals(listOf(Cell(0, 0)), b.path)
    }
}

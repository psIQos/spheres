package com.psiqos.spheres.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardTest {

    // 0 = red, 1 = yellow, 2 = green, 3 = blue
    private fun board(vararg rows: String): Board {
        val colors = rows.map { row -> row.map { it - '0' }.toIntArray() }.toTypedArray()
        return Board(rows.size, rows[0].length, 4, Random(1)).apply { setColors(colors) }
    }

    @Test
    fun connectsAdjacentDotsOfSameColor() {
        val b = board(
            "0012",
            "1203",
            "2301",
            "3012",
        )
        b.begin(Cell(0, 0))
        assertTrue(b.extend(Cell(0, 1)))
        assertFalse("different color", b.extend(Cell(0, 2)))
        assertFalse("not adjacent", b.extend(Cell(1, 2)))
        assertEquals(2, b.path.size)
        assertFalse(b.isSquare)
    }

    @Test
    fun movingBackUndoesLastStep() {
        val b = board(
            "0001",
            "1212",
            "2121",
            "1212",
        )
        b.begin(Cell(0, 0))
        b.extend(Cell(0, 1))
        b.extend(Cell(0, 2))
        assertTrue(b.extend(Cell(0, 1)))
        assertEquals(listOf(Cell(0, 0), Cell(0, 1)), b.path)
    }

    @Test
    fun singleDotIsNoMove() {
        val b = board(
            "0012",
            "1203",
            "2301",
            "3012",
        )
        b.begin(Cell(0, 0))
        assertNull(b.commit())
        assertTrue(b.path.isEmpty())
    }

    @Test
    fun removesConnectedDotsAndDropsColumn() {
        val b = board(
            "1230",
            "2301",
            "0012",
            "3123",
        )
        b.begin(Cell(2, 0))
        b.extend(Cell(2, 1))
        val result = requireResult(b.commit())
        assertEquals(setOf(Cell(2, 0), Cell(2, 1)), result.removed)
        assertFalse(result.isSquare)
        // Column 0: "1","2" above the removed dot fall one row; one new dot enters.
        assertEquals(1, b[1, 0])
        assertEquals(2, b[2, 0])
        assertEquals(3, b[3, 0])
        assertEquals(1, result.drops[0][0])
        assertEquals(1, result.drops[2][0])
        assertEquals(0, result.drops[3][0])
    }

    @Test
    fun squareRemovesAllDotsOfColorAndRefillsWithOtherColors() {
        val b = board(
            "0012",
            "0031",
            "1203",
            "2130",
        )
        b.begin(Cell(0, 0))
        b.extend(Cell(0, 1))
        b.extend(Cell(1, 1))
        b.extend(Cell(1, 0))
        assertFalse(b.isSquare)
        assertTrue(b.extend(Cell(0, 0)))
        assertTrue(b.isSquare)
        val result = requireResult(b.commit())
        assertTrue(result.isSquare)
        assertEquals(6, result.removed.size)
        if (result.shuffled) return
        for (r in 0 until 4) for (c in 0 until 4) {
            val cell = Cell(r, c)
            if (result.drops[r][c] > 0 && r < result.drops[r][c]) {
                // newly spawned dot
                assertTrue("refill must avoid square color", b[cell] != 0)
            }
        }
    }

    @Test
    fun reachingAnEarlierDotUndoesEverythingAfterIt() {
        val b = board(
            "0000",
            "1212",
            "2121",
            "1212",
        )
        b.begin(Cell(0, 0))
        b.extend(Cell(0, 1))
        b.extend(Cell(0, 2))
        b.extend(Cell(0, 3))
        // (0,2) was skipped, e.g. by a fast swipe back
        assertTrue(b.extend(Cell(0, 1)))
        assertEquals(listOf(Cell(0, 0), Cell(0, 1)), b.path)
        assertTrue(b.extend(Cell(0, 2)))
        assertEquals(3, b.path.size)
    }

    @Test
    fun cannotReuseSegment() {
        val b = board(
            "0012",
            "0031",
            "1203",
            "2130",
        )
        b.begin(Cell(0, 0))
        b.extend(Cell(0, 1))
        b.extend(Cell(1, 1))
        b.extend(Cell(1, 0))
        b.extend(Cell(0, 0))
        assertFalse(b.extend(Cell(0, 1)))
        // stepping back opens the loop again
        assertTrue(b.extend(Cell(1, 0)))
        assertFalse(b.isSquare)
    }

    @Test
    fun allDifficultiesStayPlayableAndUseAllColors() {
        for ((size, colors) in listOf(6 to 4, 6 to 5, 7 to 6)) {
            val b = Board(size, size, colors, Random(size * 10 + colors))
            val seen = HashSet<Int>()
            val rnd = Random(3)
            repeat(300) {
                assertTrue(b.hasMove())
                for (r in 0 until size) for (c in 0 until size) seen += b[r, c]
                val pairs = (0 until size).flatMap { r -> (0 until size).flatMap { c ->
                    listOf(Cell(r, c) to Cell(r, c + 1), Cell(r, c) to Cell(r + 1, c))
                } }.filter { (a, n) -> b.contains(n) && b[a] == b[n] }
                val (a, n) = pairs[rnd.nextInt(pairs.size)]
                b.begin(a)
                b.extend(n)
                requireResult(b.commit())
            }
            assertEquals("${size}x$size with $colors colors", (0 until colors).toSet(), seen)
        }
    }

    @Test
    fun alwaysPlayableAfterMoves() {
        val b = Board(random = Random(42))
        val rnd = Random(7)
        repeat(500) {
            assertTrue(b.hasMove())
            // find any pair and play it
            loop@ for (r in 0 until b.rows) for (c in 0 until b.cols) {
                val here = Cell(r, c)
                for (n in listOf(Cell(r, c + 1), Cell(r + 1, c))) {
                    if (b.contains(n) && b[n] == b[here] && rnd.nextBoolean()) {
                        b.begin(here)
                        b.extend(n)
                        requireResult(b.commit())
                        break@loop
                    }
                }
            }
        }
    }

    private fun requireResult(value: MoveResult?): MoveResult {
        assertTrue("expected a move", value != null)
        return value!!
    }
}

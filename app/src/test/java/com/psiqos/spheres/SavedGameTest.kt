package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedGameTest {

    private fun game(difficulty: Difficulty) = SavedGame(
        difficulty = difficulty,
        score = 123,
        moves = 7,
        remainingMs = 41_500,
        timerStarted = true,
        colors = List(difficulty.size) { r -> IntArray(difficulty.size) { c -> (r * 3 + c) % difficulty.colors } },
    )

    @Test
    fun roundTripsForEveryDifficulty() {
        for (d in Difficulty.entries) {
            val g = game(d)
            assertEquals(g, SavedGame.decode(g.encode()))
        }
    }

    @Test
    fun keepsPowerUpStateAndReadsGamesSavedBeforePowerUps() {
        val g = game(Difficulty.NORMAL).copy(bonusMoves = 5, timeStopLeftMs = 3200)
        val back = SavedGame.decode(g.encode())!!
        assertEquals(5, back.bonusMoves)
        assertEquals(3200, back.timeStopLeftMs)
        // the format of 1.0.0-beta.5 and earlier: 7 fields
        val old = g.encode().split(";").take(7).joinToString(";")
        val oldBack = SavedGame.decode(old)!!
        assertEquals(0, oldBack.bonusMoves)
        assertEquals(0, oldBack.timeStopLeftMs)
    }

    @Test
    fun rejectsMissingOrBrokenData() {
        assertNull(SavedGame.decode(null))
        assertNull(SavedGame.decode(""))
        assertNull(SavedGame.decode("garbage"))
        val text = game(Difficulty.NORMAL).encode()
        assertNull("truncated", SavedGame.decode(text.substringBeforeLast("/")))
        assertNull("unknown version", SavedGame.decode("9" + text.drop(1)))
    }

    /** A board that does not fit the difficulty (e.g. edited or from a future version). */
    @Test
    fun rejectsBoardNotMatchingDifficulty() {
        val hard = game(Difficulty.HARD).encode()
        assertNull(SavedGame.decode(hard.replace("HARD", "NORMAL")))
        // color 5 does not exist with 4 colors
        val easy = game(Difficulty.EASY).copy(colors = List(6) { IntArray(6) { 5 } })
        assertNull(SavedGame.decode(easy.encode()))
    }
}

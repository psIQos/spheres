package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    @Test
    fun harderMeansMoreColorsAndTighterLimits() {
        val (easy, normal, hard) = Difficulty.entries
        assertTrue(easy.colors < normal.colors && normal.colors < hard.colors)
        assertTrue(easy.seconds > normal.seconds && normal.seconds > hard.seconds)
        assertTrue(easy.moves > normal.moves && normal.moves > hard.moves)
        assertTrue(hard.size >= normal.size)
    }

    @Test
    fun normalKeepsTheOriginalRules() {
        assertEquals(60, GameMode.TIMED.limit(Difficulty.NORMAL))
        assertEquals(30, GameMode.MOVES.limit(Difficulty.NORMAL))
        assertEquals(5, Difficulty.NORMAL.colors)
        assertEquals(6, Difficulty.NORMAL.size)
    }

    @Test
    fun cyclesThroughAllLevels() {
        assertEquals(Difficulty.HARD, Difficulty.NORMAL.next())
        assertEquals(Difficulty.EASY, Difficulty.HARD.next())
        assertEquals(Difficulty.NORMAL, Difficulty.EASY.next())
    }

    @Test
    fun unknownStoredValueFallsBackToNormal() {
        assertEquals(Difficulty.NORMAL, Difficulty.parse(null))
        assertEquals(Difficulty.NORMAL, Difficulty.parse("NIGHTMARE"))
        assertEquals(Difficulty.HARD, Difficulty.parse("HARD"))
    }

    @Test
    fun bestScoresAreSeparatePerModeAndDifficulty() {
        val keys = GameMode.entries.flatMap { m -> Difficulty.entries.map { d -> Prefs.bestKey(m, d) } }
        assertEquals(keys.size, keys.toSet().size)
    }

    /** Best scores from before difficulties existed are kept as Normal. */
    @Test
    fun normalUsesTheKeyOfEarlierVersions() {
        assertEquals("best_TIMED", Prefs.bestKey(GameMode.TIMED, Difficulty.NORMAL))
        assertEquals("best_MOVES", Prefs.bestKey(GameMode.MOVES, Difficulty.NORMAL))
        assertEquals("best_ENDLESS", Prefs.bestKey(GameMode.ENDLESS, Difficulty.NORMAL))
    }
}

package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerUpTest {

    @Test
    fun walletPaysOnlyWhatItHas() {
        val shrinker = PowerUp.SHRINKER.cost
        val expander = PowerUp.EXPANDER.cost
        val w = Wallet(shrinker + 20)
        assertTrue(w.canAfford(PowerUp.SHRINKER))
        assertFalse(w.canAfford(PowerUp.EXPANDER))
        assertFalse(w.buy(PowerUp.EXPANDER))
        assertEquals(shrinker + 20, w.dots)
        assertTrue(w.buy(PowerUp.SHRINKER))
        assertEquals(20, w.dots)
        w.earn(expander - 20)
        assertTrue(w.buy(PowerUp.EXPANDER))
        assertEquals(0, w.dots)
    }

    @Test
    fun eachModeOffersFittingPowerUps() {
        assertTrue(PowerUp.TIME_STOP in PowerUp.forMode(GameMode.TIMED))
        assertFalse(PowerUp.EXTRA_MOVES in PowerUp.forMode(GameMode.TIMED))
        assertTrue(PowerUp.EXTRA_MOVES in PowerUp.forMode(GameMode.MOVES))
        assertFalse(PowerUp.TIME_STOP in PowerUp.forMode(GameMode.MOVES))
        assertEquals(listOf(PowerUp.SHRINKER, PowerUp.EXPANDER), PowerUp.forMode(GameMode.ENDLESS))
    }

    @Test
    fun extraMovesGivesFiveLikeTheOriginal() {
        assertEquals(5, PowerUp.EXTRA_MOVES_COUNT)
    }

    @Test
    fun endlessEarnsNoDots() {
        assertFalse(PowerUp.earnsDots(GameMode.ENDLESS))
        assertTrue(PowerUp.earnsDots(GameMode.TIMED))
        assertTrue(PowerUp.earnsDots(GameMode.MOVES))
    }

    @Test
    fun accountsAreSeparatePerDifficulty() {
        val keys = Difficulty.entries.map { Prefs.walletKey(it) }
        assertEquals(keys.size, keys.toSet().size)
        // dots collected before accounts per difficulty existed stay with Normal
        assertEquals("wallet", Prefs.walletKey(Difficulty.NORMAL))
    }

    /** Cheap to strong, so the order on screen reads naturally. */
    @Test
    fun pricesRiseWithStrength() {
        assertTrue(PowerUp.SHRINKER.cost < PowerUp.TIME_STOP.cost)
        assertTrue(PowerUp.TIME_STOP.cost < PowerUp.EXPANDER.cost)
        assertEquals(PowerUp.TIME_STOP.cost, PowerUp.EXTRA_MOVES.cost)
    }

    /** Prices of the original Dots shop, a fifth of a five-pack each (issue #3). */
    @Test
    fun pricesFollowTheOriginal() {
        assertEquals(100, PowerUp.SHRINKER.cost)
        assertEquals(1000, PowerUp.EXPANDER.cost)
        assertEquals(200, PowerUp.TIME_STOP.cost)
    }
}

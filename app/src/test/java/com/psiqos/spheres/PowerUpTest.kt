package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerUpTest {

    @Test
    fun walletPaysOnlyWhatItHas() {
        val w = Wallet(50)
        assertTrue(w.canAfford(PowerUp.SHRINKER))
        assertFalse(w.canAfford(PowerUp.EXPANDER))
        assertFalse(w.buy(PowerUp.EXPANDER))
        assertEquals(50, w.dots)
        assertTrue(w.buy(PowerUp.SHRINKER))
        assertEquals(20, w.dots)
        w.earn(100)
        assertTrue(w.buy(PowerUp.EXPANDER))
        assertEquals(0, w.dots)
    }

    @Test
    fun dotsReachTheAccountOnlyWhenTheGameIsCompleted() {
        val w = Wallet(40)
        val game = GameEarnings()
        game.collect(12)
        game.collect(5)
        assertEquals("nothing credited while playing", 40, w.dots)
        assertEquals(17, game.payOut(w))
        assertEquals(57, w.dots)
        // a second game-over (e.g. save() and endGame() both noticing the end) pays nothing more
        assertEquals(0, game.payOut(w))
        assertEquals(57, w.dots)
    }

    @Test
    fun abandonedGameEarnsNothing() {
        val w = Wallet(40)
        var game = GameEarnings()
        game.collect(25)
        game = GameEarnings() // "new round" or a new game after leaving
        game.collect(3)
        game.payOut(w)
        assertEquals(43, w.dots)
    }

    @Test
    fun interruptedGameKeepsItsDotsUntilCompleted() {
        val w = Wallet(0)
        val before = GameEarnings()
        before.collect(30)
        // leaving the app saves the game, coming back restores it
        val back = SavedGame.decode(saved(earnedDots = before.dots).encode())!!
        assertEquals(0, w.dots)
        val resumed = GameEarnings(back.earnedDots)
        resumed.collect(8)
        resumed.payOut(w)
        assertEquals(38, w.dots)
    }

    @Test
    fun spendingUsesOnlyTheAccount() {
        val w = Wallet(20)
        val game = GameEarnings()
        game.collect(100)
        assertFalse("dots of the running game are not spendable yet", w.canAfford(PowerUp.SHRINKER))
        assertFalse(w.buy(PowerUp.SHRINKER))
        assertEquals(20, w.dots)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotCollectNegativeDots() {
        GameEarnings().collect(-1)
    }

    private fun saved(earnedDots: Int) = SavedGame(
        Difficulty.NORMAL, score = earnedDots, moves = 3, remainingMs = 0, timerStarted = false,
        colors = List(Difficulty.NORMAL.size) { IntArray(Difficulty.NORMAL.size) },
        earnedDots = earnedDots,
    )

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
}

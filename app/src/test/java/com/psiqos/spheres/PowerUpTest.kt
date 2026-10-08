package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerUpTest {

    @Test
    fun packsAreBoughtOnlyWithEnoughDots() {
        val w = Wallet(PowerUp.SHRINKER.packPrice + 20)
        assertTrue(w.canBuyPack(PowerUp.SHRINKER))
        assertFalse(w.canBuyPack(PowerUp.EXPANDER))
        assertFalse(w.buyPack(PowerUp.EXPANDER))
        assertEquals(PowerUp.SHRINKER.packPrice + 20, w.dots)
        assertEquals(0, w.count(PowerUp.EXPANDER))
        assertTrue(w.buyPack(PowerUp.SHRINKER))
        assertEquals(20, w.dots)
        assertEquals(5, w.count(PowerUp.SHRINKER))
    }

    @Test
    fun usingAnItemCostsNoDots() {
        val w = Wallet(100, mapOf(PowerUp.EXPANDER to 1))
        assertTrue(w.use(PowerUp.EXPANDER))
        assertEquals(0, w.count(PowerUp.EXPANDER))
        assertEquals(100, w.dots)
        assertFalse(w.use(PowerUp.EXPANDER))
        assertFalse(w.use(PowerUp.TIME_STOP))
        assertEquals(0, w.count(PowerUp.TIME_STOP))
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
        val w = Wallet(400)
        val game = GameEarnings()
        game.collect(200)
        assertFalse("dots of the running game are not spendable yet", w.canBuyPack(PowerUp.SHRINKER))
        assertFalse(w.buyPack(PowerUp.SHRINKER))
        assertEquals(400, w.dots)
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
        // In moves mode the time stop gives extra moves, as in the original.
        assertEquals(PowerUp.entries, PowerUp.forMode(GameMode.TIMED))
        assertEquals(PowerUp.entries, PowerUp.forMode(GameMode.MOVES))
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

    @Test
    fun itemKeysAreSeparatePerPowerUpAndDifficulty() {
        val keys = PowerUp.entries.flatMap { p -> Difficulty.entries.map { Prefs.itemKey(p, it) } }
        assertEquals(keys.size, keys.toSet().size)
        assertFalse(keys.any { it in Difficulty.entries.map(Prefs::walletKey) })
    }

    /** Unit prices of the original Dots shop: 100, 200 and 1,000 dots (issue #3). */
    @Test
    fun pricesFollowTheOriginal() {
        val unit = PowerUp.entries.associateWith { it.packPrice / it.packSize }
        assertEquals(mapOf(PowerUp.SHRINKER to 100, PowerUp.TIME_STOP to 200, PowerUp.EXPANDER to 1000), unit)
        assertEquals(listOf(5, 5, 3), PowerUp.entries.map { it.packSize })
    }
}

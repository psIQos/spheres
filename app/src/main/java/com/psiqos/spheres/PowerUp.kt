package com.psiqos.spheres

/**
 * Special moves, as in Dots. They are items: bought in packs in the shop with collected
 * dots (every dot a player clears in a completed game goes into a dot account), and used up in a game.
 *
 * Unit prices follow the original Dots shop (shrinkers 5 for 500, time stops 5 for 1,000,
 * expanders 5 for 5,000). The expensive expander comes in packs of three (issue #3).
 */
enum class PowerUp(val packSize: Int, val packPrice: Int) {
    /** Removes one dot of the player's choice. */
    SHRINKER(5, 500),
    /** Timed mode: stops the clock for [TIME_STOP_SECONDS]. Moves mode: [EXTRA_MOVES_COUNT] more moves. */
    TIME_STOP(5, 1000),
    /** Removes all dots of the color the player taps. */
    EXPANDER(3, 3000);

    /** Whether the player picks a dot when using it. */
    val needsTarget: Boolean get() = this == SHRINKER || this == EXPANDER

    companion object {
        const val TIME_STOP_SECONDS = 5
        const val EXTRA_MOVES_COUNT = 5

        /** Endless mode has no limit, so dots cleared there would be free money. */
        fun earnsDots(mode: GameMode): Boolean = mode != GameMode.ENDLESS

        /** Power-ups usable in [mode], in the order shown below the board. */
        fun forMode(mode: GameMode): List<PowerUp> = when (mode) {
            GameMode.TIMED, GameMode.MOVES -> listOf(SHRINKER, TIME_STOP, EXPANDER)
            GameMode.ENDLESS -> listOf(SHRINKER, EXPANDER)
        }
    }
}

/**
 * The dot account and the power-ups owned at one difficulty: pure bookkeeping,
 * persisted through [Prefs].
 */
class Wallet(dots: Int, items: Map<PowerUp, Int> = emptyMap()) {
    var dots = dots
        private set
    private val items = PowerUp.entries.associateWith { maxOf(0, items[it] ?: 0) }.toMutableMap()

    fun count(p: PowerUp): Int = items.getValue(p)

    fun canBuyPack(p: PowerUp) = dots >= p.packPrice

    fun earn(count: Int) {
        require(count >= 0)
        dots += count
    }

    /** Buys a pack of [p]; false if there are not enough dots. */
    fun buyPack(p: PowerUp): Boolean {
        if (!canBuyPack(p)) return false
        dots -= p.packPrice
        items[p] = count(p) + p.packSize
        return true
    }

    /** Uses up one [p]; false if there is none left. */
    fun use(p: PowerUp): Boolean {
        if (count(p) <= 0) return false
        items[p] = count(p) - 1
        return true
    }
}

/**
 * Dots collected in the running game. They reach the account only once the game is
 * completed (time or moves used up); a game that is abandoned earns nothing.
 */
class GameEarnings(dots: Int = 0) {
    var dots = dots
        private set

    fun collect(count: Int) {
        require(count >= 0)
        dots += count
    }

    /** The game is completed: moves the dots to [wallet] and returns how many; only once. */
    fun payOut(wallet: Wallet): Int {
        val paid = dots
        wallet.earn(paid)
        dots = 0
        return paid
    }
}

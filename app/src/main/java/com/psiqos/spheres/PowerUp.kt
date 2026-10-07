package com.psiqos.spheres

/**
 * Special moves, as in Dots. They are paid with collected dots: every dot a player
 * clears goes into a dot account, kept across games.
 */
enum class PowerUp(val cost: Int) {
    /** Removes one dot of the player's choice. */
    SHRINKER(30),
    /** Timed mode: stops the clock for [TIME_STOP_SECONDS]. */
    TIME_STOP(60),
    /** Moves mode: [EXTRA_MOVES_COUNT] more moves. */
    EXTRA_MOVES(60),
    /** Removes all dots of the color the player taps. */
    EXPANDER(120);

    /** Whether the player picks a dot after buying it. */
    val needsTarget: Boolean get() = this == SHRINKER || this == EXPANDER

    companion object {
        const val TIME_STOP_SECONDS = 5
        const val EXTRA_MOVES_COUNT = 5

        /** Endless mode has no limit, so dots cleared there would be free money. */
        fun earnsDots(mode: GameMode): Boolean = mode != GameMode.ENDLESS

        /** Power-ups offered in [mode], in the order shown below the board. */
        fun forMode(mode: GameMode): List<PowerUp> = when (mode) {
            GameMode.TIMED -> listOf(SHRINKER, TIME_STOP, EXPANDER)
            GameMode.MOVES -> listOf(SHRINKER, EXTRA_MOVES, EXPANDER)
            GameMode.ENDLESS -> listOf(SHRINKER, EXPANDER)
        }
    }
}

/** The dot account of one difficulty: pure bookkeeping, persisted through [Prefs]. */
class Wallet(dots: Int) {
    var dots = dots
        private set

    fun canAfford(p: PowerUp) = dots >= p.cost

    fun earn(count: Int) {
        require(count >= 0)
        dots += count
    }

    /** Pays for [p]; false if there are not enough dots. */
    fun buy(p: PowerUp): Boolean {
        if (!canAfford(p)) return false
        dots -= p.cost
        return true
    }
}

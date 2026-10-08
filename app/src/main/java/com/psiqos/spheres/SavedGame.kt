package com.psiqos.spheres

/** A game in progress, stored so it can be resumed after leaving the screen or the app. */
data class SavedGame(
    val difficulty: Difficulty,
    val score: Int,
    val moves: Int,
    /** Timed mode: time left, and whether the clock has started. */
    val remainingMs: Long,
    val timerStarted: Boolean,
    /** Dot colors, row by row. */
    val colors: List<IntArray>,
    /** Moves added with the +5 moves power-up. */
    val bonusMoves: Int = 0,
    /** Time stop power-up still to run, in ms. */
    val timeStopLeftMs: Long = 0,
    /** Dots collected in this game, credited to the account when it is completed. */
    val earnedDots: Int = 0,
) {
    fun encode(): String = listOf(
        VERSION,
        difficulty.name,
        score,
        moves,
        remainingMs,
        if (timerStarted) 1 else 0,
        colors.joinToString("/") { row -> row.joinToString("") },
        bonusMoves,
        timeStopLeftMs,
        earnedDots,
    ).joinToString(";")

    override fun equals(other: Any?): Boolean =
        other is SavedGame && encode() == other.encode()

    override fun hashCode(): Int = encode().hashCode()

    companion object {
        private const val VERSION = "1"

        /** Null for anything that is not a complete, consistent saved game. */
        fun decode(text: String?): SavedGame? = runCatching {
            val p = text!!.split(";")
            // Fewer fields: saved by an earlier version, before power-ups existed or before
            // dots were credited only for completed games (those were already credited).
            require(p.size in 7..10 && p[0] == VERSION)
            val difficulty = Difficulty.valueOf(p[1])
            val colors = p[6].split("/").map { row -> IntArray(row.length) { row[it].digitToInt() } }
            require(colors.size == difficulty.size && colors.all { it.size == difficulty.size })
            require(colors.all { row -> row.all { it in 0 until difficulty.colors } })
            val bonus = p.getOrNull(7)?.toInt() ?: 0
            val timeStop = p.getOrNull(8)?.toLong() ?: 0
            val earned = p.getOrNull(9)?.toInt() ?: 0
            require(earned >= 0)
            SavedGame(difficulty, p[2].toInt(), p[3].toInt(), p[4].toLong(), p[5] == "1", colors, bonus, timeStop, earned)
        }.getOrNull()
    }
}

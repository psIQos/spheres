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
) {
    fun encode(): String = listOf(
        VERSION,
        difficulty.name,
        score,
        moves,
        remainingMs,
        if (timerStarted) 1 else 0,
        colors.joinToString("/") { row -> row.joinToString("") },
    ).joinToString(";")

    override fun equals(other: Any?): Boolean =
        other is SavedGame && encode() == other.encode()

    override fun hashCode(): Int = encode().hashCode()

    companion object {
        private const val VERSION = "1"

        /** Null for anything that is not a complete, consistent saved game. */
        fun decode(text: String?): SavedGame? = runCatching {
            val p = text!!.split(";")
            require(p.size == 7 && p[0] == VERSION)
            val difficulty = Difficulty.valueOf(p[1])
            val colors = p[6].split("/").map { row -> IntArray(row.length) { row[it].digitToInt() } }
            require(colors.size == difficulty.size && colors.all { it.size == difficulty.size })
            require(colors.all { row -> row.all { it in 0 until difficulty.colors } })
            SavedGame(difficulty, p[2].toInt(), p[3].toInt(), p[4].toLong(), p[5] == "1", colors)
        }.getOrNull()
    }
}

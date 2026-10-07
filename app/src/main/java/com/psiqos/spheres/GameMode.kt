package com.psiqos.spheres

import android.content.Context

enum class GameMode {
    /** Score as much as possible within a time limit. */
    TIMED,
    /** Score as much as possible with a limited number of moves. */
    MOVES,
    /** No limit, just play. */
    ENDLESS;

    /** Seconds (timed) or moves (moves mode) at [difficulty]; 0 for endless. */
    fun limit(difficulty: Difficulty): Int = when (this) {
        TIMED -> difficulty.seconds
        MOVES -> difficulty.moves
        ENDLESS -> 0
    }

    companion object {
        const val EXTRA = "mode"
    }
}

/**
 * Fewer colors make long paths and squares more likely, so the color count is the
 * main lever; board size and limits fine-tune it.
 */
enum class Difficulty(val colors: Int, val size: Int, val seconds: Int, val moves: Int) {
    EASY(colors = 4, size = 6, seconds = 75, moves = 35),
    NORMAL(colors = 5, size = 6, seconds = 60, moves = 30),
    HARD(colors = 6, size = 7, seconds = 45, moves = 25);

    fun next(): Difficulty = entries[(ordinal + 1) % entries.size]

    companion object {
        const val EXTRA = "difficulty"

        fun parse(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("spheres", Context.MODE_PRIVATE)

    /**
     * Preference key of the best score. Normal keeps the key used before difficulties
     * existed, so earlier best scores stay where they were.
     */
    fun bestKey(mode: GameMode, difficulty: Difficulty): String =
        if (difficulty == Difficulty.NORMAL) "best_${mode.name}" else "best_${mode.name}_${difficulty.name}"

    fun best(context: Context, mode: GameMode, difficulty: Difficulty): Int =
        prefs(context).getInt(bestKey(mode, difficulty), 0)

    /** Stores [score] if it beats the best score. Returns true if it is a new best. */
    fun submit(context: Context, mode: GameMode, difficulty: Difficulty, score: Int): Boolean {
        if (score <= best(context, mode, difficulty)) return false
        prefs(context).edit().putInt(bestKey(mode, difficulty), score).apply()
        return true
    }

    fun difficulty(context: Context): Difficulty = Difficulty.parse(prefs(context).getString("difficulty", null))

    fun setDifficulty(context: Context, difficulty: Difficulty) =
        prefs(context).edit().putString("difficulty", difficulty.name).apply()

    /** Deletes the best scores of all modes and difficulties. */
    fun resetBest(context: Context) {
        val editor = prefs(context).edit()
        for (m in GameMode.entries) for (d in Difficulty.entries) editor.remove(bestKey(m, d))
        editor.apply()
    }

    fun savedGame(context: Context, mode: GameMode): SavedGame? =
        SavedGame.decode(prefs(context).getString("saved_${mode.name}", null))

    fun saveGame(context: Context, mode: GameMode, game: SavedGame) =
        prefs(context).edit().putString("saved_${mode.name}", game.encode()).apply()

    fun clearSavedGame(context: Context, mode: GameMode) =
        prefs(context).edit().remove("saved_${mode.name}").apply()

    /** Dots collected over all games, the currency for power-ups. */
    fun walletDots(context: Context): Int = prefs(context).getInt("wallet", 0)

    fun setWalletDots(context: Context, dots: Int) = prefs(context).edit().putInt("wallet", dots).apply()

    fun vibrationEnabled(context: Context): Boolean = prefs(context).getBoolean("vibration", true)

    fun setVibrationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("vibration", enabled).apply()

    fun soundEnabled(context: Context): Boolean = prefs(context).getBoolean("sound", true)

    fun setSoundEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("sound", enabled).apply()
}

val Difficulty.label: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.NORMAL -> R.string.difficulty_normal
        Difficulty.HARD -> R.string.difficulty_hard
    }

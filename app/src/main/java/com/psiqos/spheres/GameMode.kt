package com.psiqos.spheres

import android.content.Context

enum class GameMode(val limit: Int) {
    /** Score as much as possible in [limit] seconds. */
    TIMED(60),
    /** Score as much as possible with [limit] moves. */
    MOVES(30),
    /** No limit, just play. */
    ENDLESS(0);

    companion object {
        const val EXTRA = "mode"
    }
}

object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("spheres", Context.MODE_PRIVATE)

    fun best(context: Context, mode: GameMode): Int = prefs(context).getInt("best_${mode.name}", 0)

    /** Stores [score] if it beats the best score. Returns true if it is a new best. */
    fun submit(context: Context, mode: GameMode, score: Int): Boolean {
        if (score <= best(context, mode)) return false
        prefs(context).edit().putInt("best_${mode.name}", score).apply()
        return true
    }

    fun soundEnabled(context: Context): Boolean = prefs(context).getBoolean("sound", true)

    fun setSoundEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("sound", enabled).apply()
}

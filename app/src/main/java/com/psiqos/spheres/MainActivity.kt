package com.psiqos.spheres

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

class MainActivity : Activity() {

    private val modes = listOf(
        Triple(GameMode.TIMED, R.id.mode_timed, R.id.best_timed),
        Triple(GameMode.MOVES, R.id.mode_moves, R.id.best_moves),
        Triple(GameMode.ENDLESS, R.id.mode_endless, R.id.best_endless),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // When the app was first opened in another way (e.g. "Open" right after installing),
        // tapping the launcher icon later starts a second menu on top of a running game
        // instead of bringing the game back. Close it again so the game shows.
        if (!isTaskRoot && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        for ((mode, buttonId, bestId) in modes) {
            val start = View.OnClickListener {
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameMode.EXTRA, mode.name)
                        .putExtra(Difficulty.EXTRA, Prefs.difficulty(this).name)
                )
            }
            findViewById<View>(buttonId).setOnClickListener(start)
            // The line below a button can say "tap to resume", so it starts the game too.
            findViewById<View>(bestId).setOnClickListener(start)
        }
        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val difficulty = Prefs.difficulty(this)
        findViewById<TextView>(R.id.difficulty_summary).text =
            getString(R.string.difficulty, getString(difficulty.label))
        findViewById<TextView>(R.id.menu_wallet).text = getString(R.string.wallet, Prefs.walletDots(this))
        findViewById<TextView>(R.id.mode_timed).text = getString(R.string.mode_timed, difficulty.seconds)
        findViewById<TextView>(R.id.mode_moves).text = getString(R.string.mode_moves, difficulty.moves)
        for ((mode, _, bestId) in modes) {
            val saved = Prefs.savedGame(this, mode)
            val best = Prefs.best(this, mode, difficulty)
            findViewById<TextView>(bestId).text = when {
                saved != null && saved.difficulty != difficulty ->
                    getString(R.string.saved_game_at, saved.score, getString(saved.difficulty.label))
                saved != null -> getString(R.string.saved_game, saved.score)
                best > 0 -> getString(R.string.best_score, best)
                else -> getString(R.string.no_score)
            }
        }
    }
}

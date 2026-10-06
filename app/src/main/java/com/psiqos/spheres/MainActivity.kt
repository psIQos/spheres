package com.psiqos.spheres

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.psiqos.spheres.game.Sound

class MainActivity : Activity() {

    private lateinit var soundToggle: TextView
    private lateinit var difficultyToggle: TextView

    private val modes = listOf(
        Triple(GameMode.TIMED, R.id.mode_timed, R.id.best_timed),
        Triple(GameMode.MOVES, R.id.mode_moves, R.id.best_moves),
        Triple(GameMode.ENDLESS, R.id.mode_endless, R.id.best_endless),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        for ((mode, buttonId, _) in modes) {
            findViewById<View>(buttonId).setOnClickListener {
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameMode.EXTRA, mode.name)
                        .putExtra(Difficulty.EXTRA, Prefs.difficulty(this).name)
                )
            }
        }

        difficultyToggle = findViewById(R.id.difficulty_toggle)
        difficultyToggle.setOnClickListener {
            Prefs.setDifficulty(this, Prefs.difficulty(this).next())
            refresh()
        }

        soundToggle = findViewById(R.id.sound_toggle)
        soundToggle.setOnClickListener {
            Prefs.setSoundEnabled(this, !Prefs.soundEnabled(this))
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val difficulty = Prefs.difficulty(this)
        difficultyToggle.text = getString(R.string.difficulty, getString(difficulty.label))
        findViewById<TextView>(R.id.mode_timed).text = getString(R.string.mode_timed, difficulty.seconds)
        findViewById<TextView>(R.id.mode_moves).text = getString(R.string.mode_moves, difficulty.moves)
        for ((mode, _, bestId) in modes) {
            val best = Prefs.best(this, mode, difficulty)
            findViewById<TextView>(bestId).text =
                if (best > 0) getString(R.string.best_score, best) else getString(R.string.no_score)
        }
        val on = Prefs.soundEnabled(this)
        Sound.enabled = on
        soundToggle.setText(if (on) R.string.sound_on else R.string.sound_off)
    }
}

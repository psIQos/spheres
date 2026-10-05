package com.psiqos.spheres

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.psiqos.spheres.game.Sound

class MainActivity : Activity() {

    private lateinit var soundToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindMode(R.id.mode_timed, R.id.best_timed, GameMode.TIMED)
        bindMode(R.id.mode_moves, R.id.best_moves, GameMode.MOVES)
        bindMode(R.id.mode_endless, R.id.best_endless, GameMode.ENDLESS)

        soundToggle = findViewById(R.id.sound_toggle)
        soundToggle.setOnClickListener {
            Prefs.setSoundEnabled(this, !Prefs.soundEnabled(this))
            updateSoundLabel()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBest(R.id.best_timed, GameMode.TIMED)
        updateBest(R.id.best_moves, GameMode.MOVES)
        updateBest(R.id.best_endless, GameMode.ENDLESS)
        updateSoundLabel()
    }

    private fun bindMode(buttonId: Int, bestId: Int, mode: GameMode) {
        findViewById<android.view.View>(buttonId).setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java).putExtra(GameMode.EXTRA, mode.name))
        }
        updateBest(bestId, mode)
    }

    private fun updateBest(bestId: Int, mode: GameMode) {
        val best = Prefs.best(this, mode)
        findViewById<TextView>(bestId).text =
            if (best > 0) getString(R.string.best_score, best) else getString(R.string.no_score)
    }

    private fun updateSoundLabel() {
        val on = Prefs.soundEnabled(this)
        Sound.enabled = on
        soundToggle.setText(if (on) R.string.sound_on else R.string.sound_off)
    }
}

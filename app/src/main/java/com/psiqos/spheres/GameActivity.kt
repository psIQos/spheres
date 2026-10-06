package com.psiqos.spheres

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.psiqos.spheres.game.GameView
import com.psiqos.spheres.game.MoveResult
import com.psiqos.spheres.game.Palette
import com.psiqos.spheres.game.Sound

class GameActivity : Activity(), GameView.Listener {

    private lateinit var mode: GameMode
    private lateinit var gameView: GameView
    private lateinit var limitLabel: TextView
    private lateinit var limitValue: TextView
    private lateinit var scoreValue: TextView
    private lateinit var overlay: View
    private lateinit var finalScore: TextView
    private lateinit var finalBest: TextView
    private lateinit var newBest: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var score = 0
    private var moves = 0
    private var gameOver = false

    // Timed mode
    private var timerStarted = false
    private var remainingMs = 0L
    private var lastTick = 0L
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            remainingMs -= now - lastTick
            lastTick = now
            if (remainingMs <= 0) {
                remainingMs = 0
                ticking = false
                updateHud()
                endGame()
                return
            }
            updateHud()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        mode = runCatching { GameMode.valueOf(intent.getStringExtra(GameMode.EXTRA)!!) }.getOrDefault(GameMode.TIMED)

        gameView = findViewById(R.id.game_view)
        limitLabel = findViewById(R.id.limit_label)
        limitValue = findViewById(R.id.limit_value)
        scoreValue = findViewById(R.id.score_value)
        overlay = findViewById(R.id.game_over)
        finalScore = findViewById(R.id.final_score)
        finalBest = findViewById(R.id.final_best)
        newBest = findViewById(R.id.new_best)

        limitLabel.setText(
            when (mode) {
                GameMode.TIMED -> R.string.label_time
                GameMode.MOVES -> R.string.label_moves_left
                GameMode.ENDLESS -> R.string.label_moves
            }
        )
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.play_again).setOnClickListener { restart() }
        findViewById<View>(R.id.to_menu).setOnClickListener { finish() }

        Sound.enabled = Prefs.soundEnabled(this)
        Sound.load(this)
        gameView.listener = this
        restart()
    }

    private fun restart() {
        handler.removeCallbacksAndMessages(null)
        score = 0
        moves = 0
        gameOver = false
        timerStarted = false
        ticking = false
        remainingMs = mode.limit * 1000L
        overlay.visibility = View.GONE
        gameView.newGame()
        updateHud()
    }

    override fun onFirstTouch() {
        if (mode == GameMode.TIMED && !timerStarted) {
            timerStarted = true
            startTicking()
        }
    }

    override fun onMove(result: MoveResult) {
        if (gameOver) return
        score += result.removed.size
        moves++
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, score)
        updateHud()
        if (mode == GameMode.MOVES && moves >= mode.limit) {
            gameView.inputEnabled = false
            handler.postDelayed({ endGame() }, 500)
        }
    }

    private fun startTicking() {
        if (ticking || gameOver) return
        ticking = true
        lastTick = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    private fun stopTicking() {
        if (!ticking) return
        handler.removeCallbacks(tick)
        remainingMs -= SystemClock.elapsedRealtime() - lastTick
        ticking = false
    }

    private fun updateHud() {
        // Only touch the views when something changed; the timer calls this ten times a second.
        setIfChanged(scoreValue, score.toString())
        setIfChanged(limitValue, when (mode) {
            GameMode.TIMED -> ((remainingMs + 999) / 1000).toString()
            GameMode.MOVES -> (mode.limit - moves).toString()
            GameMode.ENDLESS -> moves.toString()
        })
        val low = when (mode) {
            GameMode.TIMED -> remainingMs <= 10_000
            GameMode.MOVES -> mode.limit - moves <= 5
            GameMode.ENDLESS -> false
        }
        val color = if (low) Palette.dot(0) else getColor(R.color.text_primary)
        if (limitValue.currentTextColor != color) limitValue.setTextColor(color)
    }

    private fun setIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        gameView.inputEnabled = false
        val best = Prefs.best(this, mode)
        val isNewBest = Prefs.submit(this, mode, score)
        finalScore.text = score.toString()
        finalBest.text = getString(R.string.best_score, maxOf(best, score))
        newBest.visibility = if (isNewBest) View.VISIBLE else View.GONE
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(250).start()
    }

    override fun onPause() {
        super.onPause()
        stopTicking()
    }

    override fun onResume() {
        super.onResume()
        if (timerStarted && !gameOver) startTicking()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Sound.release()
        super.onDestroy()
    }
}

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
    private var difficulty = Difficulty.NORMAL
    private var limit = 0
    private lateinit var gameView: GameView
    private lateinit var limitLabel: TextView
    private lateinit var limitValue: TextView
    private lateinit var scoreValue: TextView
    private lateinit var overlay: View
    private lateinit var pauseOverlay: View
    private lateinit var finalScore: TextView
    private lateinit var finalBest: TextView
    private lateinit var newBest: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var score = 0
    private var moves = 0
    private var gameOver = false
    private var paused = false

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
        pauseOverlay = findViewById(R.id.pause_overlay)
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
        findViewById<View>(R.id.pause_button).setOnClickListener { pause() }
        findViewById<View>(R.id.resume).setOnClickListener { resume() }
        findViewById<View>(R.id.new_round).setOnClickListener { restart() }
        findViewById<View>(R.id.pause_to_menu).setOnClickListener { finish() }
        findViewById<View>(R.id.play_again).setOnClickListener { restart() }
        findViewById<View>(R.id.to_menu).setOnClickListener { finish() }

        Sound.enabled = Prefs.soundEnabled(this)
        Sound.load(this)
        gameView.haptics = Prefs.vibrationEnabled(this)
        gameView.listener = this

        val saved = Prefs.savedGame(this, mode)
        if (saved != null) {
            // Continue where the player left, paused so no time is lost.
            setDifficulty(saved.difficulty)
            score = saved.score
            moves = saved.moves
            remainingMs = saved.remainingMs
            timerStarted = saved.timerStarted
            gameView.newGame(difficulty.size, difficulty.colors, restore = saved.colors)
            updateHud()
            pause()
        } else {
            restart()
        }
    }

    private fun setDifficulty(d: Difficulty) {
        difficulty = d
        limit = mode.limit(d)
        findViewById<TextView>(R.id.difficulty_label).setText(d.label)
    }

    /** Starts a new round with the difficulty chosen in the settings. */
    private fun restart() {
        handler.removeCallbacksAndMessages(null)
        Prefs.clearSavedGame(this, mode)
        setDifficulty(Difficulty.parse(intent.getStringExtra(Difficulty.EXTRA)))
        score = 0
        moves = 0
        gameOver = false
        paused = false
        timerStarted = false
        ticking = false
        remainingMs = limit * 1000L
        overlay.visibility = View.GONE
        pauseOverlay.visibility = View.GONE
        gameView.newGame(difficulty.size, difficulty.colors)
        updateHud()
    }

    private fun pause() {
        if (gameOver) return
        paused = true
        stopTicking()
        gameView.inputEnabled = false
        pauseOverlay.visibility = View.VISIBLE
        save()
    }

    private fun resume() {
        paused = false
        pauseOverlay.visibility = View.GONE
        gameView.inputEnabled = true
        if (timerStarted) startTicking()
    }

    @Deprecated("Still called for apps that do not opt in to predictive back")
    override fun onBackPressed() {
        when {
            gameOver -> finish()
            paused -> resume()
            else -> pause()
        }
    }

    /** Stores the running game, or forgets it once there is nothing left to resume. */
    private fun save() {
        val finished = gameOver ||
            (mode == GameMode.MOVES && moves >= limit) ||
            (mode == GameMode.TIMED && timerStarted && remainingMs <= 0)
        val untouched = score == 0 && moves == 0 && !timerStarted
        if (finished || untouched) {
            Prefs.clearSavedGame(this, mode)
        } else {
            Prefs.saveGame(this, mode, SavedGame(difficulty, score, moves, remainingMs, timerStarted, gameView.colors()))
        }
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
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, difficulty, score)
        updateHud()
        save()
        if (mode == GameMode.MOVES && moves >= limit) {
            gameView.inputEnabled = false
            handler.postDelayed({ endGame() }, 500)
        }
    }

    private fun startTicking() {
        if (ticking || gameOver || paused) return
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
            GameMode.MOVES -> (limit - moves).toString()
            GameMode.ENDLESS -> moves.toString()
        })
        val low = when (mode) {
            GameMode.TIMED -> remainingMs <= 10_000
            GameMode.MOVES -> limit - moves <= 5
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
        Prefs.clearSavedGame(this, mode)
        val best = Prefs.best(this, mode, difficulty)
        val isNewBest = Prefs.submit(this, mode, difficulty, score)
        finalScore.text = score.toString()
        finalBest.text = getString(R.string.best_score_at, getString(difficulty.label), maxOf(best, score))
        newBest.visibility = if (isNewBest) View.VISIBLE else View.GONE
        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(250).start()
    }

    override fun onPause() {
        super.onPause()
        // Home button, a call, ...: pause, so the clock stops and the game is saved.
        if (!gameOver && !isFinishing) pause() else save()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Sound.release()
        super.onDestroy()
    }
}

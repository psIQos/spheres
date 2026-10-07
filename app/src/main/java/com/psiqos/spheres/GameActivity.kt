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

    // Power-ups
    private lateinit var wallet: Wallet
    private var bonusMoves = 0
    private var pendingTarget: PowerUp? = null
    private var frozenUntil = 0L
    /** Time stop left over when the clock was stopped (pause, leaving the app). */
    private var frozenLeftMs = 0L
    private lateinit var powerUpHint: TextView
    private lateinit var walletValue: TextView
    private lateinit var powerUpButtons: Map<PowerUp, TextView>

    // Timed mode
    private var timerStarted = false
    private var remainingMs = 0L
    private var lastTick = 0L
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            remainingMs -= runningSince(now)
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

        powerUpHint = findViewById(R.id.powerup_hint)
        walletValue = findViewById(R.id.wallet_value)
        val special = findViewById<TextView>(R.id.powerup_special)
        val offered = PowerUp.forMode(mode)
        powerUpButtons = buildMap {
            put(PowerUp.SHRINKER, findViewById(R.id.powerup_shrinker))
            offered.firstOrNull { it == PowerUp.TIME_STOP || it == PowerUp.EXTRA_MOVES }?.let { put(it, special) }
            put(PowerUp.EXPANDER, findViewById(R.id.powerup_expander))
        }
        if (powerUpButtons.values.none { it === special }) special.visibility = View.GONE
        for ((powerUp, button) in powerUpButtons) button.setOnClickListener { onPowerUp(powerUp) }

        Sound.enabled = Prefs.soundEnabled(this)
        Sound.load(this)
        gameView.haptics.enabled = Prefs.vibrationEnabled(this)
        gameView.listener = this

        val saved = Prefs.savedGame(this, mode)
        if (saved != null) {
            // Continue right where the player left; the clock waits for the next touch.
            setDifficulty(saved.difficulty)
            score = saved.score
            moves = saved.moves
            remainingMs = saved.remainingMs
            timerStarted = saved.timerStarted
            bonusMoves = saved.bonusMoves
            limit += bonusMoves
            frozenLeftMs = saved.timeStopLeftMs
            gameView.newGame(difficulty.size, difficulty.colors, restore = saved.colors)
            updateHud()
        } else {
            restart()
        }
    }

    private fun setDifficulty(d: Difficulty) {
        difficulty = d
        limit = mode.limit(d)
        wallet = Wallet(Prefs.walletDots(this, d))
        findViewById<TextView>(R.id.difficulty_label).setText(d.label)
    }

    /** Starts a new round with the difficulty chosen in the settings. */
    private fun restart() {
        handler.removeCallbacksAndMessages(null)
        Prefs.clearSavedGame(this, mode)
        setDifficulty(Difficulty.parse(intent.getStringExtra(Difficulty.EXTRA)))
        score = 0
        moves = 0
        bonusMoves = 0
        frozenUntil = 0L
        frozenLeftMs = 0L
        pendingTarget = null
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
        cancelTarget()
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
            Prefs.saveGame(
                this, mode,
                SavedGame(
                    difficulty, score, moves, remainingMs, timerStarted, gameView.colors(), bonusMoves,
                    timeStopLeftMs = if (ticking) maxOf(0, frozenUntil - SystemClock.elapsedRealtime()) else frozenLeftMs,
                ),
            )
        }
    }

    /** First touch of a new or continued game: the clock (re)starts now. */
    override fun onFirstTouch() {
        if (mode == GameMode.TIMED) {
            timerStarted = true
            startTicking()
        }
    }

    override fun onMove(result: MoveResult) {
        if (gameOver) return
        score += result.removed.size
        moves++
        if (PowerUp.earnsDots(mode)) {
            wallet.earn(result.removed.size)
            Prefs.setWalletDots(this, difficulty, wallet.dots)
        }
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, difficulty, score)
        updateHud()
        save()
        if (mode == GameMode.MOVES && moves >= limit) {
            gameView.inputEnabled = false
            handler.postDelayed({ endGame() }, 500)
        }
    }

    /** A power-up button was tapped. */
    private fun onPowerUp(p: PowerUp) {
        if (gameOver || paused) return
        if (pendingTarget == p) {
            cancelTarget()
            return
        }
        cancelTarget()
        if (!wallet.canAfford(p)) {
            flashHint(getString(R.string.powerup_too_expensive, p.cost - wallet.dots))
            return
        }
        when (p) {
            PowerUp.SHRINKER, PowerUp.EXPANDER -> {
                // Paid once the player has picked a dot.
                pendingTarget = p
                gameView.target = if (p == PowerUp.SHRINKER) GameView.Target.ONE_DOT else GameView.Target.ONE_COLOR
                powerUpHint.setText(if (p == PowerUp.SHRINKER) R.string.powerup_hint_shrinker else R.string.powerup_hint_expander)
                powerUpHint.visibility = View.VISIBLE
            }
            PowerUp.TIME_STOP -> {
                if (!ticking) return
                pay(p)
                val now = SystemClock.elapsedRealtime()
                frozenUntil = maxOf(now, frozenUntil) + PowerUp.TIME_STOP_SECONDS * 1000L
            }
            PowerUp.EXTRA_MOVES -> {
                if (moves >= limit) return
                pay(p)
                bonusMoves += PowerUp.EXTRA_MOVES_COUNT
                limit += PowerUp.EXTRA_MOVES_COUNT
                save()
            }
        }
        updateHud()
    }

    override fun onTargetUsed(result: MoveResult) {
        val p = pendingTarget ?: return
        pendingTarget = null
        powerUpHint.visibility = View.INVISIBLE
        pay(p)
        // Points count, but it is not a move and earns no dots for the account.
        score += result.removed.size
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, difficulty, score)
        updateHud()
        save()
    }

    private fun pay(p: PowerUp) {
        if (!wallet.buy(p)) return
        Prefs.setWalletDots(this, difficulty, wallet.dots)
        if (!p.needsTarget) {
            Sound.playSquare()
            gameView.haptics.square()
        }
    }

    private fun cancelTarget() {
        pendingTarget = null
        gameView.target = GameView.Target.NONE
        powerUpHint.visibility = View.INVISIBLE
    }

    private fun flashHint(text: String) {
        powerUpHint.text = text
        powerUpHint.visibility = View.VISIBLE
        handler.postDelayed({ if (pendingTarget == null) powerUpHint.visibility = View.INVISIBLE }, 1500)
    }

    /** Clock time since the last tick that counts, i.e. outside a time stop. */
    private fun runningSince(now: Long): Long {
        val from = maxOf(lastTick, frozenUntil)
        return if (now > from) now - from else 0
    }

    private fun startTicking() {
        if (ticking || gameOver || paused) return
        ticking = true
        lastTick = SystemClock.elapsedRealtime()
        if (frozenLeftMs > 0) {
            frozenUntil = lastTick + frozenLeftMs
            frozenLeftMs = 0
        }
        handler.post(tick)
    }

    private fun stopTicking() {
        if (!ticking) return
        handler.removeCallbacks(tick)
        val now = SystemClock.elapsedRealtime()
        remainingMs -= runningSince(now)
        frozenLeftMs = maxOf(0, frozenUntil - now)
        frozenUntil = 0
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
        val frozen = mode == GameMode.TIMED && SystemClock.elapsedRealtime() < frozenUntil
        val color = when {
            frozen -> Palette.dot(3) // blue while the time stop runs
            low -> Palette.dot(0)
            else -> getColor(R.color.text_primary)
        }
        if (limitValue.currentTextColor != color) limitValue.setTextColor(color)
        updatePowerUps()
    }

    private fun updatePowerUps() {
        setIfChanged(walletValue, getString(R.string.wallet, wallet.dots))
        for ((p, button) in powerUpButtons) {
            val name = when (p) {
                PowerUp.SHRINKER -> getString(R.string.powerup_shrinker)
                PowerUp.TIME_STOP -> getString(R.string.powerup_time_stop)
                PowerUp.EXTRA_MOVES -> getString(R.string.powerup_extra_moves, PowerUp.EXTRA_MOVES_COUNT)
                PowerUp.EXPANDER -> getString(R.string.powerup_expander)
            }
            setIfChanged(button, getString(R.string.powerup_label, name, p.cost))
            val alpha = if (wallet.canAfford(p) && !gameOver) 1f else 0.4f
            if (button.alpha != alpha) button.alpha = alpha
            val selected = pendingTarget == p
            if (button.isSelected != selected) {
                button.isSelected = selected
                button.setBackgroundResource(if (selected) R.drawable.btn_selected else R.drawable.btn_grey)
            }
        }
    }

    private fun setIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    private fun endGame() {
        if (gameOver) return
        gameOver = true
        cancelTarget()
        gameView.inputEnabled = false
        gameView.haptics.gameOver()
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
        // Home button, a call, ...: stop the clock and save. Coming back continues the
        // game directly (no pause menu); the clock runs again with the next touch.
        stopTicking()
        save()
        gameView.awaitTouch()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Sound.release()
        super.onDestroy()
    }
}

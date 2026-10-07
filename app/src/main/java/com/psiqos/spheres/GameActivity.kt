package com.psiqos.spheres

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.psiqos.spheres.game.Cell
import com.psiqos.spheres.game.GameView
import com.psiqos.spheres.game.MoveResult
import com.psiqos.spheres.game.Palette
import com.psiqos.spheres.game.Sound
import com.psiqos.spheres.game.TimeStopBar

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
    /** Length of the running time stop, for the bar (more than 5 s if stacked). */
    private var timeStopTotalMs = 0L
    private var timeStopActive = false
    private lateinit var timeStopBar: TimeStopBar
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
            if (timeStopActive && now >= frozenUntil) endTimeStop()
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
        timeStopBar = findViewById(R.id.time_stop_bar)
        walletValue = findViewById(R.id.wallet_value)
        val special = findViewById<TextView>(R.id.powerup_special)
        powerUpButtons = buildMap {
            put(PowerUp.SHRINKER, findViewById(R.id.powerup_shrinker))
            if (PowerUp.TIME_STOP in PowerUp.forMode(mode)) put(PowerUp.TIME_STOP, special)
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
            if (frozenLeftMs > 0) {
                timeStopTotalMs = PowerUp.TIME_STOP_SECONDS * 1000L
                timeStopBar.hold(frozenLeftMs)
                gameView.frost = true
            }
            gameView.newGame(difficulty.size, difficulty.colors, restore = saved.colors)
            updateHud()
        } else {
            restart()
        }
    }

    private fun setDifficulty(d: Difficulty) {
        difficulty = d
        limit = mode.limit(d)
        wallet = Prefs.wallet(this, d)
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
        timeStopActive = false
        timeStopBar.hide()
        pendingTarget = null
        gameOver = false
        paused = false
        timerStarted = false
        ticking = false
        remainingMs = limit * 1000L
        overlay.visibility = View.GONE
        pauseOverlay.visibility = View.GONE
        gameView.newGame(difficulty.size, difficulty.colors)
        gameView.frost = false
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
            Prefs.saveWallet(this, difficulty, wallet)
        }
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, difficulty, score)
        updateHud()
        save()
        if (mode == GameMode.MOVES && moves >= limit) {
            gameView.inputEnabled = false
            handler.postDelayed({ endGame() }, 500)
        }
    }

    /** A power-up button was tapped: use one, or get more in the shop if none is left. */
    private fun onPowerUp(p: PowerUp) {
        if (gameOver || paused) return
        if (pendingTarget == p) {
            cancelTarget()
            return
        }
        cancelTarget()
        if (wallet.count(p) <= 0) {
            openShop()
            return
        }
        when {
            p.needsTarget -> {
                // Used up once the player has picked a dot.
                pendingTarget = p
                gameView.target = if (p == PowerUp.SHRINKER) GameView.Target.ONE_DOT else GameView.Target.ONE_COLOR
                powerUpHint.setText(if (p == PowerUp.SHRINKER) R.string.powerup_hint_shrinker else R.string.powerup_hint_expander)
                powerUpHint.visibility = View.VISIBLE
            }
            mode == GameMode.TIMED -> {
                if (!ticking) return
                use(p)
                val now = SystemClock.elapsedRealtime()
                frozenUntil = maxOf(now, frozenUntil) + PowerUp.TIME_STOP_SECONDS * 1000L
                timeStopTotalMs = frozenUntil - now
                timeStopActive = true
                timeStopBar.run(frozenUntil - now, timeStopTotalMs)
                gameView.frost = true
                Sound.playFreeze()
                gameView.haptics.freeze()
            }
            mode == GameMode.MOVES -> {
                // In moves mode the time stop gives extra moves, as in the original.
                if (moves >= limit) return
                use(p)
                bonusMoves += PowerUp.EXTRA_MOVES_COUNT
                limit += PowerUp.EXTRA_MOVES_COUNT
                Sound.playSquare()
                gameView.haptics.square()
                save()
            }
        }
        updateHud()
    }

    /** Shrinker shortcut: a double tap removes that dot, if there is a shrinker left. */
    override fun onDoubleTap(cell: Cell) {
        if (gameOver || paused || pendingTarget != null) return
        if (wallet.count(PowerUp.SHRINKER) <= 0) {
            flashHint(getString(R.string.powerup_none_shrinker))
            return
        }
        pendingTarget = PowerUp.SHRINKER
        gameView.applyTarget(cell, GameView.Target.ONE_DOT)
    }

    override fun onTargetUsed(result: MoveResult) {
        val p = pendingTarget ?: return
        pendingTarget = null
        powerUpHint.visibility = View.INVISIBLE
        use(p)
        // Points count, but it is not a move and earns no dots for the account.
        score += result.removed.size
        if (mode == GameMode.ENDLESS) Prefs.submit(this, mode, difficulty, score)
        updateHud()
        save()
    }

    private fun use(p: PowerUp) {
        if (wallet.use(p)) Prefs.saveWallet(this, difficulty, wallet)
    }

    /** The game waits meanwhile, as after the Home button: it goes on with the next touch. */
    private fun openShop() {
        startActivity(Intent(this, ShopActivity::class.java).putExtra(Difficulty.EXTRA, difficulty.name))
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

    /** The time stop is over: the clock runs again. */
    private fun endTimeStop() {
        timeStopActive = false
        timeStopBar.hide()
        gameView.frost = false
        Sound.playThaw()
        gameView.haptics.thaw()
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
            timeStopActive = true
            timeStopBar.run(frozenLeftMs, timeStopTotalMs.coerceAtLeast(frozenLeftMs))
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
        if (timeStopActive) {
            timeStopActive = false
            timeStopBar.hold(frozenLeftMs)
        }
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
                PowerUp.TIME_STOP ->
                    if (mode == GameMode.MOVES) getString(R.string.powerup_extra_moves, PowerUp.EXTRA_MOVES_COUNT)
                    else getString(R.string.powerup_time_stop)
                PowerUp.EXPANDER -> getString(R.string.powerup_expander)
            }
            val count = wallet.count(p)
            setIfChanged(button, if (count > 0) getString(R.string.powerup_label, name, count) else getString(R.string.powerup_label_buy, name))
            val alpha = if (gameOver) 0.4f else 1f
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
        timeStopActive = false
        timeStopBar.hide()
        gameView.frost = false
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

    override fun onResume() {
        super.onResume()
        // Back from the shop (or another app): the account and the power-ups may have changed.
        wallet = Prefs.wallet(this, difficulty)
        updateHud()
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

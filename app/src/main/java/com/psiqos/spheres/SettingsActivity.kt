package com.psiqos.spheres

import android.app.AlertDialog
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import com.psiqos.spheres.game.Haptics

class SettingsActivity : ThemedActivity() {

    private val options = mapOf(
        Difficulty.EASY to R.id.difficulty_easy,
        Difficulty.NORMAL to R.id.difficulty_normal,
        Difficulty.HARD to R.id.difficulty_hard,
    )

    private val themes = mapOf(
        ThemeMode.SYSTEM to R.id.theme_system,
        ThemeMode.LIGHT to R.id.theme_light,
        ThemeMode.DARK to R.id.theme_dark,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<android.view.View>(R.id.settings_back).setOnClickListener { finish() }

        for ((difficulty, id) in options) {
            findViewById<RadioButton>(id).text = optionText(difficulty)
        }
        val group = findViewById<RadioGroup>(R.id.difficulty_group)
        group.check(options.getValue(Prefs.difficulty(this)))
        group.setOnCheckedChangeListener { _, checkedId ->
            options.entries.firstOrNull { it.value == checkedId }?.let { Prefs.setDifficulty(this, it.key) }
        }

        val themeGroup = findViewById<RadioGroup>(R.id.theme_group)
        themeGroup.check(themes.getValue(Prefs.themeMode(this)))
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            themes.entries.firstOrNull { it.value == checkedId }?.let {
                Prefs.setThemeMode(this, it.key)
                recreate() // show the new design right away
            }
        }

        findViewById<Switch>(R.id.sound_switch).apply {
            isChecked = Prefs.soundEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setSoundEnabled(this@SettingsActivity, on) }
        }
        findViewById<Switch>(R.id.vibration_switch).apply {
            isChecked = Prefs.vibrationEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setVibrationEnabled(this@SettingsActivity, on)
                // Let the player feel what they switched on.
                if (on) Haptics(this@SettingsActivity).square()
            }
        }

        findViewById<android.view.View>(R.id.reset_records).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.reset_records_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    Prefs.resetBest(this)
                    Toast.makeText(this, R.string.reset_records_done, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** "Normal" with its rules in a smaller, grey second line. */
    private fun optionText(d: Difficulty): CharSequence {
        val name = getString(d.label)
        val details = getString(R.string.difficulty_details, d.colors, d.size, d.seconds, d.moves)
        return SpannableString("$name\n$details").apply {
            val start = name.length + 1
            setSpan(RelativeSizeSpan(0.82f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(getColor(R.color.text_secondary)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

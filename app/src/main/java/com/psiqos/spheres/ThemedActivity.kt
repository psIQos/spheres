package com.psiqos.spheres

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.psiqos.spheres.game.Palette

/**
 * Base of all screens: applies the design chosen in the settings (light, dark or the
 * device's) and rebuilds the screen when that choice no longer matches what it shows.
 */
open class ThemedActivity : Activity() {

    /** Whether the views were built dark. */
    protected var builtNight = false
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val mode = Prefs.themeMode(base)
        if (mode != ThemeMode.SYSTEM) {
            val night = if (mode.night(false)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            applyOverrideConfiguration(Configuration().apply {
                uiMode = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        builtNight = isNight(resources.configuration)
        Palette.night = builtNight
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // The design was changed in the settings while this screen waited below them.
        if (wantsNight(applicationContext.resources.configuration) != builtNight) recreate()
        else Palette.night = builtNight
    }

    /** Whether the screen should be dark with the device in [systemConfig]. */
    protected fun wantsNight(systemConfig: Configuration): Boolean =
        Prefs.themeMode(this).night(isNight(systemConfig))

    private fun isNight(config: Configuration): Boolean =
        config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

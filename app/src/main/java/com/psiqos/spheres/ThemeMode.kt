package com.psiqos.spheres

/** Light or dark design: follows the device's dark theme unless fixed in the settings. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    /** Whether the app is dark, given whether the device currently uses its dark theme. */
    fun night(systemNight: Boolean): Boolean = when (this) {
        SYSTEM -> systemNight
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

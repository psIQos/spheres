package com.psiqos.spheres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun systemFollowsTheDevice() {
        assertTrue(ThemeMode.SYSTEM.night(systemNight = true))
        assertFalse(ThemeMode.SYSTEM.night(systemNight = false))
    }

    @Test
    fun fixedModesIgnoreTheDevice() {
        for (systemNight in listOf(true, false)) {
            assertFalse(ThemeMode.LIGHT.night(systemNight))
            assertTrue(ThemeMode.DARK.night(systemNight))
        }
    }

    @Test
    fun unknownOrMissingSettingFollowsTheDevice() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("SEPIA"))
        for (m in ThemeMode.entries) assertEquals(m, ThemeMode.parse(m.name))
    }
}

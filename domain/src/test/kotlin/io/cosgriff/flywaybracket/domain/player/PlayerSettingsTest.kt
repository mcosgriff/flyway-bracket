package io.cosgriff.flywaybracket.domain.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The name and county code are made up.
class PlayerSettingsTest {
    @Test
    fun `settings are complete with a name and a home county`() {
        assertTrue(PlayerSettings("Test Player", "US-ZZ-001").isComplete)
    }

    @Test
    fun `settings missing either are not complete`() {
        assertFalse(PlayerSettings().isComplete)
        assertFalse(PlayerSettings(displayName = "Test Player").isComplete)
        assertFalse(PlayerSettings(homeCountyCode = "US-ZZ-001").isComplete)
    }
}

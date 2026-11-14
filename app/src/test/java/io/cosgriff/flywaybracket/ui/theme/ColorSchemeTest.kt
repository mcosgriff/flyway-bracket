package io.cosgriff.flywaybracket.ui.theme

import androidx.compose.material3.contentColorFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Material picks a `Surface`'s text color with `contentColorFor`, which matches the background against
 * the roles in a fixed order. A role checked before `surface` that has the same value steals its text
 * color (#64).
 */
class ColorSchemeTest {
    private val light = LightFlywayColors.toColorScheme(dark = false)
    private val dark = DarkFlywayColors.toColorScheme(dark = true)

    @Test
    fun `text on surface is onSurface in light`() {
        assertEquals(light.onSurface, light.contentColorFor(light.surface))
    }

    @Test
    fun `text on surface is onSurface in dark`() {
        assertEquals(dark.onSurface, dark.contentColorFor(dark.surface))
    }

    @Test
    fun `text on errorContainer is onErrorContainer in both themes`() {
        assertEquals(light.onErrorContainer, light.contentColorFor(light.errorContainer))
        assertEquals(dark.onErrorContainer, dark.contentColorFor(dark.errorContainer))
    }
}

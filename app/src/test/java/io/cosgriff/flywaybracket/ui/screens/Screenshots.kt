@file:OptIn(ExperimentalRoborazziApi::class)

package io.cosgriff.flywaybracket.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.checks.TextContrastCheck
import io.cosgriff.flywaybracket.ui.theme.DarkFlywayColors
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import io.cosgriff.flywaybracket.ui.theme.LightFlywayColors
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

/**
 * Exact: any pixel that differs fails. Roborazzi's default allows each pixel a small colour distance,
 * which would let a theme token moved by one step pass unseen.
 */
private val exact = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(imageComparator = SimpleImageComparator(maxDistance = 0f), changeThreshold = 0f),
)

/**
 * Every check in the Accessibility Test Framework's latest set: touch targets under 48dp, text contrast
 * under 4.5:1, missing labels, duplicates. A warning fails the test as an error does. A deliberate finding
 * is suppressed for the one kind of element it is about (the checker's `suppressions`), with a comment
 * saying why.
 */
private val accessibility = RoborazziATFAccessibilityCheckOptions(
    checker = RoborazziATFAccessibilityChecker(preset = AccessibilityCheckPreset.LATEST, suppressions = FieldBorderReadAsText),
    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Warning,
)

/**
 * Suppressed: a contrast finding whose "text" colour is exactly a text field's border (`borderStrong`).
 * The check has no text colour for a Compose element, so it takes the second most common colour in the
 * element's picture. In an outlined field holding a short word that is the border, not the word (#23).
 * The border passes the 3:1 a line needs; the text in the field is the ink colour and far above 4.5:1.
 */
private object FieldBorderReadAsText : TypeSafeMatcher<AccessibilityViewCheckResult>() {
    private const val RGB = 0xFFFFFF
    private val borders = listOf(LightFlywayColors, DarkFlywayColors).map { it.borderStrong.toArgb() and RGB }

    override fun matchesSafely(result: AccessibilityViewCheckResult): Boolean {
        val measured = result.metadata ?: return false
        return result.sourceCheckClass == TextContrastCheck::class.java &&
            measured.containsKey(TextContrastCheck.KEY_FOREGROUND_COLOR) &&
            (measured.getInt(TextContrastCheck.KEY_FOREGROUND_COLOR) and RGB) in borders
    }

    override fun describeTo(description: Description) {
        description.appendText("a text field's border measured as its text")
    }
}

/** Light and dark, which every screen is drawn in. */
enum class Shade(val dark: Boolean) { LIGHT(false), DARK(true) }

/**
 * The phone every screen is drawn on: a common width, tall enough that most screens fit without
 * scrolling, at twice the density to keep the files small.
 */
const val PHONE = "w411dp-h1100dp-xhdpi"

/** Where the references live, relative to `app/`. */
private fun reference(name: String, shade: Shade) = "src/test/screenshots/${name}_${shade.name.lowercase()}.png"

/**
 * Draws [content] (a `…Content` composable) as the app shows a screen: in the Flyway theme, on the page
 * colour. Checks it for accessibility, then compares it with `src/test/screenshots/<name>_<shade>.png`.
 */
fun ComposeContentTestRule.shoot(name: String, shade: Shade, content: @Composable () -> Unit) {
    setContent {
        FlywayTheme(darkTheme = shade.dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
    // Accessibility first: a finding says what is wrong, where an image that differs only says that it does.
    onRoot().checkRoboAccessibility(accessibility)
    onRoot().captureRoboImage(reference(name, shade), roborazziOptions = exact)
}

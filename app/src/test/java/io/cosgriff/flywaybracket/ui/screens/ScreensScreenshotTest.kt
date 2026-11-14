package io.cosgriff.flywaybracket.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.NotBuiltYetContent
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.home.HomeContent
import io.cosgriff.flywaybracket.ui.home.HomeState
import io.cosgriff.flywaybracket.ui.home.previewState
import io.cosgriff.flywaybracket.ui.home.previewToday
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens in light and dark, drawn from their `…Content` composables with no ViewModel, checked for
 * accessibility and compared with the references in `src/test/screenshots`.
 * `./gradlew :app:recordRoborazziDebug` writes new references after a change meant to alter how a screen
 * looks; look at every image it changes before committing it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE, sdk = [35])
class ScreensScreenshotTest(private val shade: Shade) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun home() = compose.shoot("home", shade) {
        HomeContent(previewState, previewToday, onNavigate = {}, onStartOuting = {})
    }

    @Test
    fun homeEmpty() = compose.shoot("home_empty", shade) {
        HomeContent(HomeState(), previewToday, onNavigate = {}, onStartOuting = {})
    }

    @Test
    fun notBuiltYet() = compose.shoot("not_built_yet", shade) {
        NotBuiltYetContent(title = R.string.nav_bracket, place = TopLevel.Bracket)
    }

    @Test
    fun notBuiltYetPushed() = compose.shoot("not_built_yet_pushed", shade) {
        NotBuiltYetContent(title = R.string.outing_title, onBack = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shades() = Shade.entries.map { arrayOf<Any>(it) }
    }
}

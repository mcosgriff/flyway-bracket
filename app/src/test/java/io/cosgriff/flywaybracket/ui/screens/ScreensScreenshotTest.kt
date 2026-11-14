package io.cosgriff.flywaybracket.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.NotBuiltYetContent
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.home.HomeContent
import io.cosgriff.flywaybracket.ui.home.HomeState
import io.cosgriff.flywaybracket.ui.home.previewState
import io.cosgriff.flywaybracket.ui.home.previewToday
import io.cosgriff.flywaybracket.ui.onboarding.OnboardingContent
import io.cosgriff.flywaybracket.ui.onboarding.OnboardingState
import io.cosgriff.flywaybracket.ui.onboarding.previewEmpty
import io.cosgriff.flywaybracket.ui.onboarding.previewFilledIn
import io.cosgriff.flywaybracket.ui.onboarding.previewNameTooShort
import io.cosgriff.flywaybracket.ui.outing.AddSightingSheet
import io.cosgriff.flywaybracket.ui.outing.AddSightingState
import io.cosgriff.flywaybracket.ui.outing.OutingContent
import io.cosgriff.flywaybracket.ui.outing.OutingState
import io.cosgriff.flywaybracket.ui.outing.SummaryContent
import io.cosgriff.flywaybracket.ui.outing.previewAddSighting
import io.cosgriff.flywaybracket.ui.outing.previewAddSightingNoMatch
import io.cosgriff.flywaybracket.ui.outing.previewAddSightingSearch
import io.cosgriff.flywaybracket.ui.outing.previewElapsed
import io.cosgriff.flywaybracket.ui.outing.previewOuting
import io.cosgriff.flywaybracket.ui.outing.previewOutingEmpty
import io.cosgriff.flywaybracket.ui.outing.previewSummary
import io.cosgriff.flywaybracket.ui.packs.PacksContent
import io.cosgriff.flywaybracket.ui.packs.PacksState
import io.cosgriff.flywaybracket.ui.packs.previewHomePack
import io.cosgriff.flywaybracket.ui.packs.previewNoPacks
import io.cosgriff.flywaybracket.ui.packs.previewRejected
import io.cosgriff.flywaybracket.ui.packs.previewTwoPacks
import java.time.Duration
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
    fun homeOutingInProgress() = compose.shoot("home_outing_in_progress", shade) {
        HomeContent(previewState.copy(outingInProgress = true), previewToday, onNavigate = {}, onStartOuting = {})
    }

    @Test
    fun homePackMissing() = compose.shootScreen("home_pack_missing", shade) {
        HomeContent(HomeState(region = previewState.region, packMissing = true), previewToday, onNavigate = {}, onStartOuting = {})
    }

    @Test
    fun onboardingEmpty() = onboarding("onboarding_empty", previewEmpty)

    @Test
    fun onboardingNameTooShort() = onboarding("onboarding_name_too_short", previewNameTooShort)

    @Test
    fun onboardingFilledIn() = onboarding("onboarding_filled_in", previewFilledIn)

    private fun onboarding(name: String, state: OnboardingState) = compose.shoot(name, shade) {
        OnboardingContent(state, onName = {}, onState = {}, onCounty = {}, onContinue = {})
    }

    @Test
    fun packsNoPacks() = packs("packs_no_packs", previewNoPacks)

    @Test
    fun packsHomePack() = packs("packs_home_pack", previewHomePack)

    @Test
    fun packsTwoPacks() = packs("packs_two_packs", previewTwoPacks)

    @Test
    fun packsRejected() = packs("packs_rejected", previewRejected)

    private fun packs(name: String, state: PacksState) = compose.shoot(name, shade) {
        PacksContent(state, onNavigate = {}, onAddPack = {}, onRemove = {})
    }

    @Test
    fun outingEmpty() = outing("outing_empty", previewOutingEmpty, Duration.ofSeconds(12))

    @Test
    fun outingWithSightings() = outing("outing_with_sightings", previewOuting, previewElapsed)

    private fun outing(name: String, state: OutingState, elapsed: Duration) = compose.shoot(name, shade) {
        OutingContent(state, elapsed, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun addSightingNoSearch() = addSighting("add_sighting_no_search", previewAddSighting, query = "")

    @Test
    fun addSightingSearch() = addSighting("add_sighting_search", previewAddSightingSearch, query = "warb")

    @Test
    fun addSightingNoMatch() = addSighting("add_sighting_no_match", previewAddSightingNoMatch, query = "pelicun")

    /** The sheet over the outing it adds to. */
    private fun addSighting(name: String, state: AddSightingState, query: String) = compose.shootScreen(name, shade) {
        OutingContent(previewOuting, previewElapsed, {}, {}, {}, {}, {}, {}, {}, {})
        AddSightingSheet(state, query, onQuery = {}, onPick = {}, onDismiss = {})
    }

    @Test
    fun summary() = compose.shoot("summary", shade) {
        SummaryContent(previewSummary, onDone = {})
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

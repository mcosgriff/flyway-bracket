package io.cosgriff.flywaybracket.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.ui.bracket.BracketContent
import io.cosgriff.flywaybracket.ui.bracket.BracketLayout
import io.cosgriff.flywaybracket.ui.bracket.BracketScreenState
import io.cosgriff.flywaybracket.ui.bracket.MatchupContent
import io.cosgriff.flywaybracket.ui.bracket.MatchupState
import io.cosgriff.flywaybracket.ui.bracket.previewBeforeLock
import io.cosgriff.flywaybracket.ui.bracket.previewChangePick
import io.cosgriff.flywaybracket.ui.bracket.previewLocked
import io.cosgriff.flywaybracket.ui.bracket.previewMatchupHigherShare
import io.cosgriff.flywaybracket.ui.bracket.previewMatchupTied
import io.cosgriff.flywaybracket.ui.bracket.previewMatchupTooFew
import io.cosgriff.flywaybracket.ui.bracket.previewPartlyPicked
import io.cosgriff.flywaybracket.ui.components.NotBuiltYetContent
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.consent.ConsentContent
import io.cosgriff.flywaybracket.ui.consent.ConsentState
import io.cosgriff.flywaybracket.ui.consent.previewAsking
import io.cosgriff.flywaybracket.ui.consent.previewNoNetwork
import io.cosgriff.flywaybracket.ui.home.HomeContent
import io.cosgriff.flywaybracket.ui.home.HomeState
import io.cosgriff.flywaybracket.ui.home.LocationExplainContent
import io.cosgriff.flywaybracket.ui.home.previewState
import io.cosgriff.flywaybracket.ui.home.previewToday
import io.cosgriff.flywaybracket.ui.league.CreateLeagueState
import io.cosgriff.flywaybracket.ui.league.LeagueContent
import io.cosgriff.flywaybracket.ui.league.LeagueCreateContent
import io.cosgriff.flywaybracket.ui.league.LeagueTabState
import io.cosgriff.flywaybracket.ui.league.previewClosed
import io.cosgriff.flywaybracket.ui.league.previewCreate
import io.cosgriff.flywaybracket.ui.league.previewCreateInvalid
import io.cosgriff.flywaybracket.ui.league.previewCreated
import io.cosgriff.flywaybracket.ui.league.previewFinished
import io.cosgriff.flywaybracket.ui.league.previewForming
import io.cosgriff.flywaybracket.ui.league.previewJoin
import io.cosgriff.flywaybracket.ui.league.previewLeagueToday
import io.cosgriff.flywaybracket.ui.league.previewLeave
import io.cosgriff.flywaybracket.ui.league.previewRunning
import io.cosgriff.flywaybracket.ui.onboarding.OnboardingContent
import io.cosgriff.flywaybracket.ui.onboarding.OnboardingState
import io.cosgriff.flywaybracket.ui.onboarding.previewEmpty
import io.cosgriff.flywaybracket.ui.onboarding.previewFilledIn
import io.cosgriff.flywaybracket.ui.onboarding.previewNameTooShort
import io.cosgriff.flywaybracket.ui.outing.AddSightingSheet
import io.cosgriff.flywaybracket.ui.outing.AddSightingState
import io.cosgriff.flywaybracket.ui.outing.CountyPickerSheet
import io.cosgriff.flywaybracket.ui.outing.OutingContent
import io.cosgriff.flywaybracket.ui.outing.OutingState
import io.cosgriff.flywaybracket.ui.outing.SummaryContent
import io.cosgriff.flywaybracket.ui.outing.previewAddSighting
import io.cosgriff.flywaybracket.ui.outing.previewAddSightingNoMatch
import io.cosgriff.flywaybracket.ui.outing.previewAddSightingSearch
import io.cosgriff.flywaybracket.ui.outing.previewCountyChoices
import io.cosgriff.flywaybracket.ui.outing.previewElapsed
import io.cosgriff.flywaybracket.ui.outing.previewOuting
import io.cosgriff.flywaybracket.ui.outing.previewOutingEmpty
import io.cosgriff.flywaybracket.ui.outing.previewOutingFallback
import io.cosgriff.flywaybracket.ui.outing.previewSummary
import io.cosgriff.flywaybracket.ui.packs.PacksContent
import io.cosgriff.flywaybracket.ui.packs.PacksState
import io.cosgriff.flywaybracket.ui.packs.previewHomePack
import io.cosgriff.flywaybracket.ui.packs.previewNoPacks
import io.cosgriff.flywaybracket.ui.packs.previewRejected
import io.cosgriff.flywaybracket.ui.packs.previewTwoPacks
import io.cosgriff.flywaybracket.ui.settings.LicencesContent
import io.cosgriff.flywaybracket.ui.settings.SettingsActions
import io.cosgriff.flywaybracket.ui.settings.SettingsContent
import io.cosgriff.flywaybracket.ui.settings.previewLicences
import io.cosgriff.flywaybracket.ui.settings.previewSettingsAccount
import io.cosgriff.flywaybracket.ui.settings.previewSettingsDeleteAsked
import io.cosgriff.flywaybracket.ui.settings.previewSettingsDeleteAskedAgain
import io.cosgriff.flywaybracket.ui.settings.previewSettingsNoAccount
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

    @Test
    fun outingFallback() = outing("outing_fallback", previewOutingFallback, Duration.ofSeconds(12))

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

    /** The county picker over a new outing that fell back to the home county. */
    @Test
    fun countyPicker() = compose.shootScreen("county_picker", shade) {
        OutingContent(previewOutingFallback, Duration.ofSeconds(12), {}, {}, {}, {}, {}, {}, {}, {})
        CountyPickerSheet(previewCountyChoices, currentCode = previewOutingFallback.countyCode, onPick = {}, onDismiss = {})
    }

    @Test
    fun locationExplain() = compose.shoot("location_explain", shade) {
        LocationExplainContent(onContinue = {}, onNotNow = {}, onBack = {})
    }

    @Test
    fun summary() = compose.shoot("summary", shade) {
        SummaryContent(previewSummary, onDone = {})
    }

    @Test
    fun consent() = consent("consent", previewAsking, TopLevel.League)

    @Test
    fun consentNoNetwork() = consent("consent_no_network", previewNoNetwork, TopLevel.Bracket)

    private fun consent(name: String, state: ConsentState, place: TopLevel) = compose.shoot(name, shade) {
        ConsentContent(state, place, onNavigate = {}, onContinue = {}, onNotNow = {})
    }

    @Test
    fun settingsNoAccount() = compose.shoot("settings_no_account", shade) {
        SettingsContent(previewSettingsNoAccount, SettingsActions())
    }

    @Test
    fun settingsAccount() = compose.shoot("settings_account", shade) {
        SettingsContent(previewSettingsAccount, SettingsActions())
    }

    /** The end of the page: privacy, about and licences. */
    @Test
    fun settingsAbout() = compose.shoot("settings_about", shade) {
        SettingsContent(previewSettingsAccount, SettingsActions(), scroll = rememberScrollState(Int.MAX_VALUE))
    }

    @Test
    fun settingsDelete() =compose.shootScreen("settings_delete", shade) {
        SettingsContent(previewSettingsDeleteAsked, SettingsActions())
    }

    @Test
    fun settingsDeleteAgain() = compose.shootScreen("settings_delete_again", shade) {
        SettingsContent(previewSettingsDeleteAskedAgain, SettingsActions())
    }

    @Test
    fun licences() = compose.shoot("licences", shade) {
        LicencesContent(previewLicences, onBack = {}, onOpenLink = {})
    }

    @Test
    fun bracketBeforeLock() = bracket("bracket_before_lock", BracketScreenState.Ready(previewBeforeLock))

    @Test
    fun bracketPartlyPicked() = bracket("bracket_partly_picked", BracketScreenState.Ready(previewPartlyPicked), round = Round.ROUND_OF_32)

    @Test
    fun bracketChangePick() = compose.shootScreen("bracket_change_pick", shade) {
        BracketContent(BracketScreenState.Ready(previewChangePick), BracketLayout.LIST, Round.ROUND_OF_64, Field.quarters.first(), onNavigate = {})
    }

    @Test
    fun bracketLockedLive() = bracket("bracket_locked_live", BracketScreenState.Ready(previewLocked))

    @Test
    fun bracketRoundDone() = bracket("bracket_round_done", BracketScreenState.Ready(previewLocked), round = Round.ROUND_OF_64)

    @Test
    fun bracketTree() = bracket("bracket_tree", BracketScreenState.Ready(previewLocked), layout = BracketLayout.TREE)

    @Test
    fun bracketField() = bracket("bracket_field", BracketScreenState.Ready(previewLocked), layout = BracketLayout.FIELD)

    @Test
    fun bracketNoLeague() = bracket("bracket_no_league", BracketScreenState.NoLeague(forming = false))

    private fun bracket(name: String, state: BracketScreenState, round: Round? = null, layout: BracketLayout = BracketLayout.LIST) =
        compose.shoot(name, shade) {
            BracketContent(state, layout, round, Field.quarters.first(), onNavigate = {})
        }

    @Test
    fun matchupHigherShare() = matchup("matchup_higher_share", previewMatchupHigherShare)

    @Test
    fun matchupTied() = matchup("matchup_tied", previewMatchupTied)

    @Test
    fun matchupTooFew() = matchup("matchup_too_few", previewMatchupTooFew)

    private fun matchup(name: String, state: MatchupState) = compose.shoot(name, shade) {
        MatchupContent(state, onBack = {})
    }

    @Test
    fun leagueJoin() = league("league_join", previewJoin)

    @Test
    fun leagueForming() = league("league_forming", previewForming)

    @Test
    fun leagueRunning() = league("league_running", previewRunning)

    @Test
    fun leagueFinished() = league("league_finished", previewFinished)

    @Test
    fun leagueClosed() = league("league_closed", previewClosed)

    @Test
    fun leagueLeave() = compose.shootScreen("league_leave", shade) {
        LeagueContent(previewLeave, onNavigate = {})
    }

    private fun league(name: String, state: LeagueTabState) = compose.shoot(name, shade) {
        LeagueContent(state, onNavigate = {})
    }

    @Test
    fun leagueCreate() = leagueCreate("league_create", previewCreate)

    @Test
    fun leagueCreateInvalidName() = leagueCreate("league_create_invalid_name", previewCreateInvalid)

    @Test
    fun leagueCreated() = leagueCreate("league_created", previewCreated)

    private fun leagueCreate(name: String, state: CreateLeagueState) = compose.shoot(name, shade) {
        LeagueCreateContent(state, previewLeagueToday, onBack = {})
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

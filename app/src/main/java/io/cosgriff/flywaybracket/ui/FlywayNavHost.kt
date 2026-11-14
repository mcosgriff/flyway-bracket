package io.cosgriff.flywaybracket.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.NotBuiltYetContent
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.consent.ConsentScreen
import io.cosgriff.flywaybracket.ui.home.HomeScreen
import io.cosgriff.flywaybracket.ui.onboarding.OnboardingScreen
import io.cosgriff.flywaybracket.ui.outing.CountyFallback
import io.cosgriff.flywaybracket.ui.outing.OutingScreen
import io.cosgriff.flywaybracket.ui.outing.SummaryScreen
import io.cosgriff.flywaybracket.ui.packs.PacksScreen
import io.cosgriff.flywaybracket.ui.settings.LicencesScreen
import io.cosgriff.flywaybracket.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

/** The first run: asks for a display name and a home county (#23). */
@Serializable data object OnboardingRoute : NavKey

@Serializable data object HomeRoute : NavKey

@Serializable data object BracketRoute : NavKey

@Serializable data object LeagueRoute : NavKey

@Serializable data object PacksRoute : NavKey

/** The consent page, before the first pack download (#39). Once there is an account it goes back to Packs. */
@Serializable data object PacksConsentRoute : NavKey

/**
 * The outing in progress, with the Add sighting sheet (#26).
 *
 * @property fallback why a new outing is in the home county rather than where the phone is (#27)
 */
@Serializable data class OutingRoute(val fallback: CountyFallback? = null) : NavKey

/** A finished outing, by its id (#26). */
@Serializable data class SummaryRoute(val outingId: Long) : NavKey

/** Settings, from the gear on Home (#44). */
@Serializable data object SettingsRoute : NavKey

/** The app's, the font's and the libraries' licences, from Settings (#44). */
@Serializable data object LicencesRoute : NavKey

/**
 * The app's screens. Each entry gets its own ViewModels, cleared when it leaves the back stack. The
 * bottom bar's places each start a fresh back stack.
 *
 * @param needsOnboarding whether the display name or the home county is missing. While it is, the app
 *   starts on the first-run screen instead of Home, alone on the back stack, so Back leaves the app.
 */
@Composable
fun FlywayNavHost(needsOnboarding: Boolean) {
    val backStack = rememberNavBackStack(if (needsOnboarding) OnboardingRoute else HomeRoute)
    // Both saved: Home takes the first-run screen's place, so Back can't return to it.
    LaunchedEffect(needsOnboarding) {
        if (!needsOnboarding && backStack.firstOrNull() == OnboardingRoute) {
            backStack.clear()
            backStack.add(HomeRoute)
        }
    }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val navigate: (TopLevel) -> Unit = { place ->
        val root = when (place) {
            TopLevel.Home -> HomeRoute
            TopLevel.Bracket -> BracketRoute
            TopLevel.League -> LeagueRoute
            TopLevel.Packs -> PacksRoute
        }
        if (backStack.firstOrNull() != root) {
            backStack.clear()
            backStack.add(root)
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<OnboardingRoute> {
                OnboardingScreen()
            }
            entry<HomeRoute> {
                HomeScreen(
                    onNavigate = navigate,
                    onOpenOuting = { fallback -> if (backStack.lastOrNull() !is OutingRoute) backStack.add(OutingRoute(fallback)) },
                    onOpenSettings = { if (backStack.lastOrNull() != SettingsRoute) backStack.add(SettingsRoute) },
                )
            }
            // Both need the server: the consent page comes first until the phone has an account (#38).
            entry<BracketRoute> {
                ConsentScreen(place = TopLevel.Bracket, onNavigate = navigate) {
                    NotBuiltYetContent(title = R.string.nav_bracket, place = TopLevel.Bracket, onNavigate = navigate)
                }
            }
            entry<LeagueRoute> {
                ConsentScreen(place = TopLevel.League, onNavigate = navigate) {
                    NotBuiltYetContent(title = R.string.nav_league, place = TopLevel.League, onNavigate = navigate)
                }
            }
            entry<PacksRoute> {
                PacksScreen(onNavigate = navigate, onGetAccount = { if (backStack.lastOrNull() != PacksConsentRoute) backStack.add(PacksConsentRoute) })
            }
            // Not now, or the account made: back to Packs, which then lists the server's packs.
            entry<PacksConsentRoute> {
                ConsentScreen(place = TopLevel.Packs, onNavigate = navigate, onNotNow = back) {
                    LaunchedEffect(Unit) { back() }
                }
            }
            entry<OutingRoute> { route ->
                OutingScreen(
                    fallback = route.fallback,
                    onBack = back,
                    // The summary takes the outing's place, so Back from it goes Home.
                    onFinished = { id ->
                        backStack.removeAll { it is OutingRoute }
                        backStack.add(SummaryRoute(id))
                    },
                    onClosed = { backStack.removeAll { it is OutingRoute } },
                )
            }
            entry<SummaryRoute> { route ->
                SummaryScreen(outingId = route.outingId, onDone = back)
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = back, onOpenLicences = { if (backStack.lastOrNull() != LicencesRoute) backStack.add(LicencesRoute) })
            }
            entry<LicencesRoute> {
                LicencesScreen(onBack = back)
            }
        },
    )
}

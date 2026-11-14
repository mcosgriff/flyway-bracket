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
import io.cosgriff.flywaybracket.ui.outing.OutingScreen
import io.cosgriff.flywaybracket.ui.outing.SummaryScreen
import io.cosgriff.flywaybracket.ui.packs.PacksScreen
import kotlinx.serialization.Serializable

/** The first run: asks for a display name and a home county (#23). */
@Serializable data object OnboardingRoute : NavKey

@Serializable data object HomeRoute : NavKey

@Serializable data object BracketRoute : NavKey

@Serializable data object LeagueRoute : NavKey

@Serializable data object PacksRoute : NavKey

/** The outing in progress, with the Add sighting sheet (#26). */
@Serializable data object OutingRoute : NavKey

/** A finished outing, by its id (#26). */
@Serializable data class SummaryRoute(val outingId: Long) : NavKey

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
                HomeScreen(onNavigate = navigate, onOpenOuting = { if (backStack.lastOrNull() != OutingRoute) backStack.add(OutingRoute) })
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
                PacksScreen(onNavigate = navigate)
            }
            entry<OutingRoute> {
                OutingScreen(
                    onBack = back,
                    // The summary takes the outing's place, so Back from it goes Home.
                    onFinished = { id ->
                        backStack.remove(OutingRoute)
                        backStack.add(SummaryRoute(id))
                    },
                    onClosed = { backStack.remove(OutingRoute) },
                )
            }
            entry<SummaryRoute> { route ->
                SummaryScreen(outingId = route.outingId, onDone = back)
            }
        },
    )
}

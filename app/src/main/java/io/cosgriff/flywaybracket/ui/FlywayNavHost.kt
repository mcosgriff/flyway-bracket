package io.cosgriff.flywaybracket.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.NotBuiltYetContent
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

@Serializable data object BracketRoute : NavKey

@Serializable data object LeagueRoute : NavKey

@Serializable data object PacksRoute : NavKey

@Serializable data object OutingRoute : NavKey

/**
 * The app's screens. Each entry gets its own ViewModels, cleared when it leaves the back stack. The
 * bottom bar's places each start a fresh back stack.
 */
@Composable
fun FlywayNavHost() {
    val backStack = rememberNavBackStack(HomeRoute)
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
            entry<HomeRoute> {
                HomeScreen(onNavigate = navigate, onStartOuting = { backStack.add(OutingRoute) })
            }
            entry<BracketRoute> {
                NotBuiltYetContent(title = R.string.nav_bracket, place = TopLevel.Bracket, onNavigate = navigate)
            }
            entry<LeagueRoute> {
                NotBuiltYetContent(title = R.string.nav_league, place = TopLevel.League, onNavigate = navigate)
            }
            entry<PacksRoute> {
                NotBuiltYetContent(title = R.string.nav_packs, place = TopLevel.Packs, onNavigate = navigate)
            }
            entry<OutingRoute> {
                NotBuiltYetContent(title = R.string.outing_title, onBack = back)
            }
        },
    )
}

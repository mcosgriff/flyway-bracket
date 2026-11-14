package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/** The bottom bar's places. Each starts a fresh back stack. */
enum class TopLevel(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Home(R.string.nav_home, R.drawable.ic_nav_home),
    Bracket(R.string.nav_bracket, R.drawable.ic_nav_bracket),
    League(R.string.nav_league, R.drawable.ic_nav_league),
    Packs(R.string.nav_packs, R.drawable.ic_nav_packs),
}

@Composable
fun FlywayBottomBar(selected: TopLevel, onNavigate: (TopLevel) -> Unit, modifier: Modifier = Modifier) {
    val colors = FlywayTheme.colors
    NavigationBar(modifier = modifier, containerColor = colors.surface) {
        TopLevel.entries.forEach { place ->
            NavigationBarItem(
                selected = place == selected,
                onClick = { onNavigate(place) },
                // The label names the place, so the icon adds nothing for a screen reader.
                icon = { Icon(painterResource(place.icon), contentDescription = null) },
                label = { Text(stringResource(place.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.pick,
                    selectedTextColor = colors.pick,
                    indicatorColor = colors.pickContainer,
                    unselectedIconColor = colors.inkMuted,
                    unselectedTextColor = colors.inkMuted,
                ),
            )
        }
    }
}

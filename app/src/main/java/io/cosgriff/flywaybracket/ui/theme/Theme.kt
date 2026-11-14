package io.cosgriff.flywaybracket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's theme, from the design canvas's tokens. Dynamic color (Material You) is off on purpose:
 * the wallpaper palette would replace the brand and the meaning of the pick and live colors.
 */
@Composable
fun FlywayTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkFlywayColors else LightFlywayColors
    CompositionLocalProvider(
        LocalFlywayColors provides colors,
        LocalFlywayTextStyles provides DefaultFlywayTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = FlywayTypography,
            shapes = FlywayShapes,
            content = content,
        )
    }
}

/** Tokens Material has no role for: `FlywayTheme.colors.pick`, `FlywayTheme.text.score`. */
object FlywayTheme {
    val colors: FlywayColors
        @Composable @ReadOnlyComposable
        get() = LocalFlywayColors.current

    val text: FlywayTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalFlywayTextStyles.current
}

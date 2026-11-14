package io.cosgriff.flywaybracket.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The design canvas's color tokens, one object per theme. Each name is followed by the canvas's short
 * token (`bg`, `sf`, …) so a value can be checked against the mockups by eye.
 * Design canvas: https://claude.ai/artifact/6YaSetusq6mnkkiE5fds6R
 */
@Immutable
data class FlywayColors(
    val ground: Color, // bg
    val surface: Color, // sf
    val surfaceMuted: Color, // sc
    val ink: Color, // ink
    val inkMuted: Color, // mut
    val border: Color, // line
    val borderStrong: Color, // sline
    val divider: Color, // div
    val primary: Color, // pri
    val onPrimary: Color, // onp
    val onPrimaryMuted: Color, // onpm
    val action: Color, // act
    val onAction: Color, // onact
    val pick: Color, // pick
    val pickContainer: Color, // tint
    val warningContainer: Color, // wbg
    val warningBorder: Color, // wln
    val onWarningContainer: Color, // wtx
    val live: Color, // hot
    val chartMuted: Color, // bar2
    val inverse: Color, // inv
    val onInverse: Color, // oninv
    val danger: Color,
    val dangerContainer: Color,
)

val LightFlywayColors = FlywayColors(
    ground = Color(0xFFF4F6F1),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFDDE3D8),
    ink = Color(0xFF14231C),
    inkMuted = Color(0xFF4A5A51),
    border = Color(0xFFC9D1C6),
    borderStrong = Color(0xFF8A968D),
    divider = Color(0xFFE3E8DF),
    primary = Color(0xFF0B5D4B),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryMuted = Color(0xFFE6F3EE),
    action = Color(0xFFC2410C),
    onAction = Color(0xFFFFFFFF),
    pick = Color(0xFF0B5D4B),
    pickContainer = Color(0xFFE6F3EE),
    warningContainer = Color(0xFFFFF1D6),
    warningBorder = Color(0xFFB77400),
    onWarningContainer = Color(0xFF6B4100),
    live = Color(0xFF9A330A),
    chartMuted = Color(0xFFA9B3AB),
    inverse = Color(0xFF14231C),
    onInverse = Color(0xFFFFFFFF),
    danger = Color(0xFFB3261E),
    dangerContainer = Color(0xFFFCE8E6),
)

val DarkFlywayColors = FlywayColors(
    ground = Color(0xFF0B1612),
    surface = Color(0xFF14231C),
    surfaceMuted = Color(0xFF223329),
    ink = Color(0xFFE8F0EB),
    inkMuted = Color(0xFFA3B3AA),
    border = Color(0xFF2F4238),
    borderStrong = Color(0xFF647A6E),
    divider = Color(0xFF24352C),
    primary = Color(0xFF0F6B56),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryMuted = Color(0xFFD3EDE3),
    action = Color(0xFFFF8A4C),
    onAction = Color(0xFF2B1000),
    pick = Color(0xFF7FD8BC),
    pickContainer = Color(0xFF173A30),
    warningContainer = Color(0xFF3A2A05),
    warningBorder = Color(0xFFD9A036),
    onWarningContainer = Color(0xFFFFD58A),
    live = Color(0xFFFFA273),
    chartMuted = Color(0xFF55685E),
    inverse = Color(0xFFE8F0EB),
    onInverse = Color(0xFF0B1612),
    danger = Color(0xFFF2A19A),
    dangerContainer = Color(0xFF3B1714),
)

val LocalFlywayColors = staticCompositionLocalOf { LightFlywayColors }

/**
 * Material roles from the tokens. Every surface and container role is set: one left to Material's
 * default shows its baseline purple.
 */
fun FlywayColors.toColorScheme(dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = pickContainer,
        onPrimaryContainer = pick,
        inversePrimary = if (dark) LightFlywayColors.pick else DarkFlywayColors.pick,
        secondary = action,
        onSecondary = onAction,
        secondaryContainer = surfaceMuted,
        onSecondaryContainer = ink,
        tertiary = live,
        onTertiary = ground,
        tertiaryContainer = warningContainer,
        onTertiaryContainer = onWarningContainer,
        background = ground,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = inkMuted,
        // No tonal tint on raised surfaces: elevation shows as surface steps, as the tokens lay out.
        surfaceTint = surface,
        inverseSurface = inverse,
        inverseOnSurface = onInverse,
        error = danger,
        onError = surface,
        // Not `surface`: Material's contentColorFor checks errorContainer before surface, so a card on
        // surface would get onErrorContainer (danger red) as its text color (#64).
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        outline = borderStrong,
        outlineVariant = border,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = ground,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceMuted,
        surfaceContainerHighest = surfaceMuted,
        // "Fixed" roles keep one value in both themes, so they come from the light tokens.
        primaryFixed = LightFlywayColors.pickContainer,
        primaryFixedDim = LightFlywayColors.border,
        onPrimaryFixed = LightFlywayColors.ink,
        onPrimaryFixedVariant = LightFlywayColors.pick,
        secondaryFixed = LightFlywayColors.surfaceMuted,
        secondaryFixedDim = LightFlywayColors.border,
        onSecondaryFixed = LightFlywayColors.ink,
        onSecondaryFixedVariant = LightFlywayColors.inkMuted,
        tertiaryFixed = LightFlywayColors.warningContainer,
        tertiaryFixedDim = LightFlywayColors.warningContainer,
        onTertiaryFixed = LightFlywayColors.onWarningContainer,
        onTertiaryFixedVariant = LightFlywayColors.onWarningContainer,
    )

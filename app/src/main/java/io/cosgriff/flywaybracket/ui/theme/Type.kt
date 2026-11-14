package io.cosgriff.flywaybracket.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.cosgriff.flywaybracket.R

// Inter is a bundled variable font (OFL, text in assets/licences): no font is fetched at runtime.
// Each weight the tokens use is one instance of the variable file.
private fun variable(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val Inter = variable(R.font.inter, 400, 500, 600, 700)

/**
 * Titles, seeds and scores. The design canvas draws these in Barlow Condensed; until that font is
 * bundled, they use Inter, and this one name is the place to swap it.
 */
val Display: FontFamily = Inter

private fun style(family: FontFamily, size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = if (tracking == 0.0) TextStyle.Default.letterSpacing else (tracking / size).em,
)

/**
 * Material roles from the design canvas's text sizes:
 * headlineMedium the screen title (uppercase it where it is used), titleLarge a card's main line,
 * titleMedium and labelLarge row names and buttons, bodyLarge and bodyMedium running text,
 * bodySmall captions, labelMedium chips, labelSmall the overline (uppercase it where it is used).
 */
val FlywayTypography = Typography(
    headlineMedium = style(Display, 28, 34, 700, tracking = 0.5),
    titleLarge = style(Inter, 17, 24, 600),
    titleMedium = style(Inter, 15, 20, 600),
    bodyLarge = style(Inter, 15, 22, 400),
    bodyMedium = style(Inter, 14, 20, 400),
    bodySmall = style(Inter, 13, 18, 400),
    labelLarge = style(Inter, 15, 20, 600),
    labelMedium = style(Inter, 13, 18, 600),
    labelSmall = style(Inter, 12, 16, 600, tracking = 1.0),
)

/** Text styles with no Material role: the figures a bracket is read by. */
@Immutable
data class FlywayTextStyles(
    val scoreHero: TextStyle,
    val scoreLarge: TextStyle,
    val score: TextStyle,
    val seed: TextStyle,
)

val DefaultFlywayTextStyles = FlywayTextStyles(
    scoreHero = style(Display, 48, 52, 700),
    scoreLarge = style(Display, 28, 32, 700),
    score = style(Display, 22, 26, 700),
    seed = style(Display, 16, 20, 700),
)

val LocalFlywayTextStyles = staticCompositionLocalOf { DefaultFlywayTextStyles }

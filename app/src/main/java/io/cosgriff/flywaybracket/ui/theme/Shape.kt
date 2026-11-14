package io.cosgriff.flywaybracket.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from the design canvas. */
val FlywayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), // badges, bracket slots
    small = RoundedCornerShape(10.dp), // round chips, inputs
    medium = RoundedCornerShape(14.dp), // rows, result cards
    large = RoundedCornerShape(18.dp), // cards, hero panels
    extraLarge = RoundedCornerShape(28.dp), // full-width buttons, bottom sheets' top corners
)

/** Spacing and size tokens. Touch targets stay at least [tapMin]. */
object FlywayDimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp // screen side gutter
    val space6 = 24.dp

    // Material asks for 48dp targets; the canvas's 44px is its floor, so the larger wins.
    val tapMin = 48.dp
    val fieldHeight = 52.dp
    val buttonHeight = 56.dp
    val rowHeight = 64.dp

    /** A card's or a row's outline, as the canvas's 1px borders. */
    val border = 1.dp

    /** The least width of a row's leading time ("10:48 AM"), so the names beside it line up. */
    val timeColumn = 64.dp

    /** A line that marks a card out, as the canvas's 2px upset border. */
    val emphasisBorder = 2.dp

    /** The least width of a matchup row's seed, so the names beside it line up (#42). */
    val seedColumn = 24.dp

    /** A finished matchup's points, at the end of its card (#42). */
    val resultColumn = 68.dp

    /** The least width of a league table's points, so the figures line up (#40). */
    val pointsColumn = 34.dp

    /** The most a list inside a dialog grows before it scrolls, such as the time zones (#40). */
    val dialogList = 320.dp

    /** A legend's colour sample. */
    val swatch = 14.dp

    // The bracket's tree and whole field (#42): a matchup is two slots, together at least [tapMin] tall.
    val bracketSlot = 24.dp
    val bracketJoin = 16.dp
    val treeFirstColumn = 172.dp
    val treeColumn = 132.dp
    val treeCell = 60.dp
    val fieldFirstColumn = 140.dp
    val fieldColumn = 112.dp
    val fieldCell = 56.dp
}

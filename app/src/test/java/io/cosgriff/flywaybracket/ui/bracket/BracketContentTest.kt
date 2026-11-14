package io.cosgriff.flywaybracket.ui.bracket

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.ui.screens.PHONE
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A matchup is one item for a screen reader, and taps pick or open it. The made-up preview brackets. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE, sdk = [35])
class BracketContentTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<Pair<DrawPosition, String>>()
    private val opened = mutableListOf<DrawPosition>()

    private fun show(state: BracketState, round: Round? = null) = compose.setContent {
        FlywayTheme(darkTheme = false) {
            BracketContent(
                BracketScreenState.Ready(state),
                BracketLayout.LIST,
                round,
                Field.quarters.first(),
                onNavigate = {},
                actions = BracketActions(onPick = { at, bird -> picked += at to bird.commonName }, onOpenMatchup = { opened += it }),
            )
        }
    }

    @Test
    fun `before the lock a tap on a bird picks it, and a screen reader picks with an action on the one matchup`() {
        show(previewBeforeLock)
        val first = DrawPosition(Round.ROUND_OF_64, 0)

        compose.onNodeWithText("Lazuli Bunting", useUnmergedTree = true).performClick()
        assertEquals(listOf(first to "Lazuli Bunting"), picked)

        val matchup = compose.onNode(hasContentDescription("Northern House Wren, seed 1, against Lazuli Bunting, seed 16. No pick."))
        val actions = matchup.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Pick Northern House Wren", "Pick Lazuli Bunting"), actions.map { it.label })
        actions.first().action()
        assertEquals(first to "Northern House Wren", picked.last())
    }

    @Test
    fun `after the lock a matchup reads its birds, shares, pick and result, and opens`() {
        show(previewLocked, round = Round.ROUND_OF_64)

        val description = "Northern House Wren, seed 1: 78% of 9, against Lazuli Bunting, seed 16: 0% of 14. " +
            "Your pick: Northern House Wren. Northern House Wren won. You scored 1 point."
        compose.onNode(hasContentDescription(description))
            .performScrollTo()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(DrawPosition(Round.ROUND_OF_64, 0)), opened)
    }

    @Test
    fun `a pick that clears later picks names them and asks`() {
        show(previewChangePick)

        compose.onNodeWithText("Change this pick?").assertExists()
        compose.onNodeWithText("Round of 32: Western Warbling Vireo").assertExists()
    }
}

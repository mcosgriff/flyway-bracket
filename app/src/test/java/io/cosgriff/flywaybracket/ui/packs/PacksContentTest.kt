package io.cosgriff.flywaybracket.ui.packs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.cosgriff.flywaybracket.ui.screens.PHONE
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Removing a pack: the screen asks first, and the home county's pack cannot be removed. Made-up packs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE, sdk = [35])
class PacksContentTest {
    @get:Rule val compose = createComposeRule()

    private val removed = mutableListOf<String>()

    private fun show(state: PacksState) = compose.setContent {
        FlywayTheme(darkTheme = false) {
            PacksContent(state, onNavigate = {}, onAddPack = {}, onRemove = { removed += it })
        }
    }

    @Test
    fun `removing a pack asks first, says what is lost, and removes it on a yes`() {
        show(previewTwoPacks)

        compose.onNodeWithContentDescription("Remove Birch").performClick()

        compose.onNodeWithText("Remove Birch?").assertExists()
        compose.onNodeWithText("Birds from Birch can no longer be logged until you add its pack again.").assertExists()
        assertEquals(emptyList<String>(), removed)

        // The dialog's own Remove, not the row's.
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("US-ZB"), removed)
        compose.onNodeWithText("Remove Birch?").assertDoesNotExist()
    }

    @Test
    fun `cancelling keeps the pack`() {
        show(previewTwoPacks)

        compose.onNodeWithContentDescription("Remove Birch").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<String>(), removed)
        compose.onNodeWithText("Remove Birch?").assertDoesNotExist()
    }

    @Test
    fun `the home county's pack has its Remove button off, with the reason beside it`() {
        show(previewHomePack)

        compose.onNodeWithContentDescription("Remove Alder").assertIsNotEnabled()
        compose.onNodeWithText("This pack holds your home county, so it cannot be removed.").assertExists()
    }
}

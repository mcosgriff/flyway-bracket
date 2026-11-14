package io.cosgriff.flywaybracket.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LicencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reads the build's list of libraries`() {
        // Made-up libraries, in the shape the build writes.
        val text = """
            [
              {"name": "Sample", "coordinates": "com.example:sample", "version": "1.0.0",
               "licences": [{"name": "Sample Licence", "url": "https://example.com/licence"}]},
              {"name": "Other", "coordinates": "com.example:other", "version": "2.0",
               "licences": [{"name": "No Address", "url": null}], "extra": true}
            ]
        """.trimIndent()
        assertEquals(
            listOf(
                LibraryLicence("Sample", "com.example:sample", "1.0.0", listOf(Licence("Sample Licence", "https://example.com/licence"))),
                LibraryLicence("Other", "com.example:other", "2.0", listOf(Licence("No Address", null))),
            ),
            parseLibraries(text),
        )
    }

    @Test
    fun `a broken list reads as none`() {
        assertNull(parseLibraries("not json"))
        assertNull(parseLibraries("""[{"name": "No coordinates"}]"""))
    }

    /** The asset the build writes is in the app, and names a licence for each library. */
    @Test
    fun `the app holds the font's licence and the libraries' licences`() {
        val state = readLicences(context.assets)
        assertTrue(state.fontLicence.orEmpty().contains("SIL OPEN FONT LICENSE"))
        assertEquals(false, state.notRead)
        val libraries = state.libraries.orEmpty()
        assertTrue(libraries.any { it.coordinates == "org.jetbrains.kotlin:kotlin-stdlib" })
        assertTrue(libraries.all { it.licences.isNotEmpty() })
        // Debug-only tools are not in the release APK, so they are not in its list.
        val debugOnly = listOf("androidx.compose.ui:ui-tooling-android", "androidx.compose.ui:ui-test-manifest")
        assertTrue(libraries.none { it.coordinates in debugOnly })
    }

    @Test
    fun `a link opens in the browser with a plain view intent`() {
        // From a screen, as the app opens one.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.openInBrowser(USER_GUIDE_URL)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(USER_GUIDE_URL, started.dataString)
        assertNull(started.component)
    }
}

package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.domain.player.PlayerSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The name and county code are made up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "settings_test.preferences_pb").apply { delete() }
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val settings = SettingsRepository(store)

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun `nothing stored reads as unset`() = runBlocking {
        assertEquals(PlayerSettings(displayName = null, homeCountyCode = null), settings.settings().first())
    }

    @Test
    fun `every value round-trips`() = runBlocking {
        settings.setDisplayName("Test Player")
        settings.setHomeCountyCode("US-ZZ-001")
        assertEquals(
            PlayerSettings(displayName = "Test Player", homeCountyCode = "US-ZZ-001"),
            settings.settings().first(),
        )
    }

    @Test
    fun `values are stored trimmed`() = runBlocking {
        settings.setDisplayName("  Test Player ")
        settings.setHomeCountyCode(" US-ZZ-001\n")
        assertEquals(PlayerSettings("Test Player", "US-ZZ-001"), settings.settings().first())
    }

    @Test
    fun `null or blank clears a value`() = runBlocking {
        settings.setDisplayName("Test Player")
        settings.setHomeCountyCode("US-ZZ-001")
        settings.setDisplayName(null)
        settings.setHomeCountyCode("   ")
        assertEquals(PlayerSettings(), settings.settings().first())
    }

    @Test
    fun `a value stored blank reads as unset`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("display_name")] = " "
            it[stringPreferencesKey("home_county_code")] = ""
        }
        assertEquals(PlayerSettings(), settings.settings().first())
    }

    @Test
    fun `the location question is unasked until it is asked`() = runBlocking {
        assertEquals(false, settings.locationAsked().first())
        settings.setLocationAsked()
        assertEquals(true, settings.locationAsked().first())
    }
}

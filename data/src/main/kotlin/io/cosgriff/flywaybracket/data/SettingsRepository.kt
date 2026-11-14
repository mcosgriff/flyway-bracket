package io.cosgriff.flywaybracket.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.cosgriff.flywaybracket.domain.player.PlayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The player's settings, in DataStore. A value never set reads as `null`, and so does one stored blank.
 * Made by `DataModule`.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {
    fun settings(): Flow<PlayerSettings> = store.data.map { prefs ->
        PlayerSettings(
            displayName = prefs[DISPLAY_NAME]?.takeIf(String::isNotBlank),
            homeCountyCode = prefs[HOME_COUNTY_CODE]?.takeIf(String::isNotBlank),
        )
    }

    /** Stores the name trimmed; `null` or a blank name clears it. */
    suspend fun setDisplayName(name: String?) = set(DISPLAY_NAME, name)

    /** Stores the eBird region code of the home county (`US-CO-059`) trimmed; `null` or blank clears it. */
    suspend fun setHomeCountyCode(code: String?) = set(HOME_COUNTY_CODE, code)

    private suspend fun set(key: Preferences.Key<String>, value: String?) {
        val trimmed = value?.trim().orEmpty()
        store.edit { if (trimmed.isEmpty()) it.remove(key) else it[key] = trimmed }
    }

    private companion object {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val HOME_COUNTY_CODE = stringPreferencesKey("home_county_code")
    }
}

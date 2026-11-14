package io.cosgriff.flywaybracket.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.data.db.OutingDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): FlywayDatabase = FlywayDatabase.open(context)

    @Provides
    fun outingDao(db: FlywayDatabase): OutingDao = db.outings()

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun settingsRepository(store: DataStore<Preferences>): SettingsRepository = SettingsRepository(store)
}

package io.cosgriff.flywaybracket.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.BuildConfig
import io.cosgriff.flywaybracket.data.LeagueRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.EncryptedAccountStore
import io.cosgriff.flywaybracket.data.account.keystoreKey
import io.cosgriff.flywaybracket.data.db.BracketDao
import io.cosgriff.flywaybracket.data.db.LeagueDao
import io.cosgriff.flywaybracket.data.db.OutingDao
import io.cosgriff.flywaybracket.data.db.SyncDao
import io.cosgriff.flywaybracket.data.server.ServerActivity
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.serverEngine
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.data.sync.SightingSync
import io.cosgriff.flywaybracket.data.sync.SyncScheduler
import io.cosgriff.flywaybracket.data.sync.SyncWorkerFactory
import io.cosgriff.flywaybracket.data.sync.WorkSyncScheduler
import java.io.File
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The league server's client, the account kept on the phone (#38), what is fetched with it (#40, #41), and the
 * background sync of sightings and results (#43).
 */
@Module
@InstallIn(SingletonComponent::class)
object ServerModule {
    /** Making the client makes no call: nothing is sent until the player agrees (ADR 0001). */
    @Provides @Singleton
    fun serverClient(activity: ServerActivity): ServerClient =
        ServerClient(serverHttpClient(serverEngine()), BuildConfig.SERVER_URL, activity = activity)

    /** Whether a call is waiting on the server to wake (#93), for the screens that wait on it. */
    @Provides @Singleton
    fun serverActivity(): ServerActivity = ServerActivity()

    /** In `no_backup`: the account stays on this phone, whatever a backup takes. */
    @Provides @Singleton
    fun accountStore(@ApplicationContext context: Context): AccountStore =
        EncryptedAccountStore(File(context.noBackupFilesDir, "account"), key = { keystoreKey(KEY_ALIAS) })

    @Provides @Singleton
    fun accountRepository(server: ServerClient, store: AccountStore, settings: SettingsRepository): AccountRepository =
        AccountRepository(server, store, settings)

    @Provides @Singleton
    fun bracketRepository(server: ServerClient, accounts: AccountRepository, brackets: BracketDao, outings: OutingDao, clock: Clock): BracketRepository =
        BracketRepository(server, accounts, brackets, outings, clock)

    @Provides @Singleton
    fun leagueRepository(server: ServerClient, accounts: AccountRepository, leagues: LeagueDao, clock: Clock): LeagueRepository =
        LeagueRepository(server, accounts, leagues, clock)

    @Provides @Singleton
    fun sightingSync(server: ServerClient, accounts: AccountRepository, brackets: BracketRepository, sync: SyncDao, clock: Clock): SightingSync =
        SightingSync(server, accounts, brackets, sync, clock)

    /** Enqueuing work makes no call: a run with no account sends nothing (#43). */
    @Provides @Singleton
    fun syncScheduler(@ApplicationContext context: Context): SyncScheduler = WorkSyncScheduler(context)

    @Provides @Singleton
    fun syncWorkerFactory(sync: Provider<SightingSync>, scheduler: Provider<SyncScheduler>): SyncWorkerFactory =
        SyncWorkerFactory(sync, scheduler)

    private const val KEY_ALIAS = "flyway_account"
}

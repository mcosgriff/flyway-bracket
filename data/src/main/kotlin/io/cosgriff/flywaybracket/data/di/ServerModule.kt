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
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.EncryptedAccountStore
import io.cosgriff.flywaybracket.data.account.keystoreKey
import io.cosgriff.flywaybracket.data.db.BracketDao
import io.cosgriff.flywaybracket.data.db.OutingDao
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.serverEngine
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/** The league server's client, the account kept on the phone (#38), and what is fetched with it (#41). */
@Module
@InstallIn(SingletonComponent::class)
object ServerModule {
    /** Making the client makes no call: nothing is sent until the player agrees (ADR 0001). */
    @Provides @Singleton
    fun serverClient(): ServerClient =
        ServerClient(serverHttpClient(serverEngine()), BuildConfig.SERVER_URL)

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

    private const val KEY_ALIAS = "flyway_account"
}

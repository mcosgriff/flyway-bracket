package io.cosgriff.flywaybracket.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.AndroidNetworkMeter
import io.cosgriff.flywaybracket.data.NetworkMeter
import io.cosgriff.flywaybracket.data.PackDownloads
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.server.ServerClient
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object PacksModule {
    /** One for the whole app: it keeps the parsed packs in memory. The files are in `files/packs`. */
    @Provides @Singleton
    fun packRepository(@ApplicationContext context: Context): PackRepository = PackRepository(File(context.filesDir, "packs"))

    /**
     * One for the whole app, so Home and Packs see the same downloads. They run in a scope of their own,
     * which lives as long as the app: leaving a screen does not stop one (#39).
     */
    @Provides @Singleton
    fun packDownloads(server: ServerClient, accounts: AccountRepository, packs: PackRepository): PackDownloads =
        PackDownloads(server, accounts, packs, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides
    fun networkMeter(@ApplicationContext context: Context): NetworkMeter = AndroidNetworkMeter(context)
}

package io.cosgriff.flywaybracket.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.flywaybracket.data.PackRepository
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PacksModule {
    /** One for the whole app: it keeps the parsed packs in memory. The files are in `files/packs`. */
    @Provides @Singleton
    fun packRepository(@ApplicationContext context: Context): PackRepository = PackRepository(File(context.filesDir, "packs"))
}

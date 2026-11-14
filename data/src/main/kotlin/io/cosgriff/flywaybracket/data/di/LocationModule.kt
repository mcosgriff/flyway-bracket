package io.cosgriff.flywaybracket.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.flywaybracket.data.AndroidCountyLocator
import io.cosgriff.flywaybracket.data.CountyLocator
import io.cosgriff.flywaybracket.data.PackRepository

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    fun countyLocator(@ApplicationContext context: Context, packs: PackRepository): CountyLocator = AndroidCountyLocator(context, packs)
}

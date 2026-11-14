package io.cosgriff.flywaybracket

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.cosgriff.flywaybracket.data.sync.SyncWorkerFactory
import javax.inject.Inject

/**
 * The application. It gives WorkManager the factory that makes the sync worker (#43): WorkManager starts on
 * first use with this configuration, in place of its own start-up (removed in the manifest).
 */
@HiltAndroidApp
class FlywayApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: SyncWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

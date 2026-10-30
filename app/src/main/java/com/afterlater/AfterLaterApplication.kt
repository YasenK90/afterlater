package com.afterlater

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.afterlater.data.metadata.SafeDns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AfterLaterApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashReportStore.installAsHandler()
        container.applicationScope.launch {
            try {
                container.reminderScheduler.ensureScheduled(
                    container.settingsRepository.currentSettings(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AFTERLATER", "Reminder schedule check failed", error)
            }
            try {
                container.autoBackupManager.syncSchedule()
            } catch (error: Exception) {
                Log.w("AFTERLATER", "Auto-backup schedule check failed", error)
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .dns(SafeDns)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
        .crossfade(160)
        .build()
}

package com.afterlater

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.afterlater.data.metadata.SafeDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AfterLaterApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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

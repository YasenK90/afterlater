package com.afterlater

import android.content.Context
import com.afterlater.core.database.AfterLaterDatabase
import com.afterlater.data.metadata.MetadataExtractor
import com.afterlater.data.metadata.MetadataScheduler
import com.afterlater.data.metadata.WorkManagerMetadataScheduler
import com.afterlater.data.export.ArchiveExportManager
import com.afterlater.data.repository.RoomSavedItemRepository
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = Clock.systemUTC()

    val database: AfterLaterDatabase by lazy { AfterLaterDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val metadataExtractor: MetadataExtractor by lazy { MetadataExtractor() }

    private val metadataScheduler: MetadataScheduler by lazy {
        WorkManagerMetadataScheduler(appContext)
    }

    val savedItemRepository: SavedItemRepository by lazy {
        RoomSavedItemRepository(database, metadataScheduler, clock)
    }

    val archiveExportManager: ArchiveExportManager by lazy {
        ArchiveExportManager(appContext, savedItemRepository)
    }
}

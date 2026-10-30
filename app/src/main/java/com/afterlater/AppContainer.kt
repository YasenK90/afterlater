package com.afterlater

import android.content.Context
import com.afterlater.auto.AutoDetectStateStore
import com.afterlater.core.database.AfterLaterDatabase
import com.afterlater.data.metadata.MetadataExtractor
import com.afterlater.data.metadata.MetadataScheduler
import com.afterlater.data.metadata.WorkManagerMetadataScheduler
import com.afterlater.core.crash.CrashReportStore
import com.afterlater.data.export.ArchiveBackupManager
import com.afterlater.data.export.AutoBackupManager
import com.afterlater.data.reminder.ReminderNotifier
import com.afterlater.data.reminder.ReminderScheduler
import com.afterlater.data.reminder.WorkManagerReminderScheduler
import com.afterlater.data.repository.RoomSavedItemRepository
import com.afterlater.data.repository.SavedItemRepository
import com.afterlater.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.ZoneId

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = Clock.systemUTC()

    val reminderZone: ZoneId = ZoneId.systemDefault()

    val database: AfterLaterDatabase by lazy { AfterLaterDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val autoDetectStateStore: AutoDetectStateStore by lazy { AutoDetectStateStore(appContext) }

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(appContext) }

    val reminderScheduler: ReminderScheduler by lazy {
        WorkManagerReminderScheduler(appContext, clock, reminderZone, reminderNotifier)
    }

    val metadataExtractor: MetadataExtractor by lazy { MetadataExtractor() }

    private val metadataScheduler: MetadataScheduler by lazy {
        WorkManagerMetadataScheduler(appContext)
    }

    val savedItemRepository: SavedItemRepository by lazy {
        RoomSavedItemRepository(database, metadataScheduler, clock, reminderZone)
    }

    val archiveBackupManager: ArchiveBackupManager by lazy {
        ArchiveBackupManager(appContext, savedItemRepository, clock)
    }

    val autoBackupManager: AutoBackupManager by lazy {
        AutoBackupManager(appContext, settingsRepository, archiveBackupManager, clock)
    }

    val crashReportStore: CrashReportStore by lazy { CrashReportStore(appContext) }
}

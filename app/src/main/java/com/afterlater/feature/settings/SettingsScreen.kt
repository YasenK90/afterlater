package com.afterlater.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.BuildConfig
import com.afterlater.auto.AutoDetectPermissions
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterLaterColors
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.BlackoutPalette
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.InkPalette
import com.afterlater.core.designsystem.LabeledValue
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.Wordmark
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import com.afterlater.core.util.TimeFormat
import com.afterlater.data.export.RestorePreview
import com.afterlater.data.reminder.areReminderNotificationsAllowed
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val inspecting by viewModel.inspecting.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = LocalAfterLaterColors.current
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(areReminderNotificationsAllowed(context))
    }
    var accessibilityEnabled by remember {
        mutableStateOf(AutoDetectPermissions.isAccessibilityServiceEnabled(context))
    }
    var overlayAllowed by remember {
        mutableStateOf(AutoDetectPermissions.canDrawOverlays(context))
    }
    var showReminderTime by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val allowed = areReminderNotificationsAllowed(context)
        notificationsAllowed = allowed
        if (granted && allowed) {
            viewModel.setRemindersEnabled(true)
        } else {
            viewModel.showMessage("Notifications are blocked. Reminders will stay off.")
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = areReminderNotificationsAllowed(context)
        accessibilityEnabled = AutoDetectPermissions.isAccessibilityServiceEnabled(context)
        overlayAllowed = AutoDetectPermissions.canDrawOverlays(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.inspectBackup(uri)
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.chooseAutoBackupFolder(uri)
    }
    val exportName = remember { "afterlater-archive-${LocalDate.now()}.json" }
    val onRemindersToggle: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setRemindersEnabled(false)
        } else {
            notificationsAllowed = areReminderNotificationsAllowed(context)
            val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            when {
                permissionMissing -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                !notificationsAllowed -> viewModel.showMessage(
                    "Notifications are blocked in Android settings.",
                )
                else -> viewModel.setRemindersEnabled(true)
            }
        }
    }

    LaunchedEffect(message) {
        val current = message
        if (current != null) {
            snackbarHostState.showSnackbar(current)
            viewModel.clearMessage()
        }
    }

    ArchiveBackground {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wordmark()
                    Spacer(Modifier.weight(1f))
                    Mono("LOCAL ONLY", color = colors.accent, style = AfterType.MonoSmall)
                }
                Hairline()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "SETTINGS",
                        AfterType.DisplayHero.copy(fontSize = 44.sp, lineHeight = 43.sp),
                        colors.textPrimary,
                    )
                    Spacer(Modifier.height(7.dp))
                    Mono(
                        "TUNE THE RHYTHM. NOT THE ARCHIVE.",
                        color = colors.textDim,
                        style = AfterType.MonoSmall,
                    )

                    Spacer(Modifier.height(28.dp))
                    SectionMark(
                        index = "01",
                        title = "DAILY REVIEW QUANTITY",
                        trailing = "MAX ${AppSettings.MAX_DAILY_LIMIT}",
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            state.settings.dailyReviewLimit.toString(),
                            AfterType.DisplayHero.copy(fontSize = 58.sp, lineHeight = 56.sp),
                            colors.accent,
                        )
                        Spacer(Modifier.width(10.dp))
                        Mono(
                            "ITEMS / DAY",
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AfterButton(
                            label = "-",
                            onClick = { viewModel.setDailyLimit(state.settings.dailyReviewLimit - 1) },
                            modifier = Modifier
                                .width(64.dp)
                                .semantics { contentDescription = "Decrease daily review limit" },
                            style = AfterButtonStyle.OUTLINE,
                            enabled = state.settings.dailyReviewLimit > AppSettings.MIN_DAILY_LIMIT,
                        )
                        AfterButton(
                            label = "+",
                            onClick = { viewModel.setDailyLimit(state.settings.dailyReviewLimit + 1) },
                            modifier = Modifier
                                .width(64.dp)
                                .semantics { contentDescription = "Increase daily review limit" },
                            style = AfterButtonStyle.OUTLINE,
                            enabled = state.settings.dailyReviewLimit < AppSettings.MAX_DAILY_LIMIT,
                        )
                        Spacer(Modifier.weight(1f))
                        Mono("1 TO ${AppSettings.MAX_DAILY_LIMIT}", color = colors.textDim, style = AfterType.MonoSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Today shows at most this many entries. When fewer qualify, the queue is shorter — never padded with filler.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(index = "02", title = "REDISCOVERY RHYTHM")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Each day surfaces up to three due entries. Opening one resets it for seven days; snoozing offers tomorrow or next week.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledValue("First return", "24H AFTER SAVING")
                    LabeledValue("After opening", "7 DAYS")
                    LabeledValue("Snooze", "TOMORROW / NEXT WEEK")

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(index = "03", title = "APPEARANCE")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoice(
                            label = "INK",
                            note = "DEEP GREY / ACID",
                            palette = InkPalette,
                            selected = state.settings.theme == AppTheme.INK,
                            onClick = { viewModel.setTheme(AppTheme.INK) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeChoice(
                            label = "BLACKOUT",
                            note = "TRUE BLACK / OLED",
                            palette = BlackoutPalette,
                            selected = state.settings.theme == AppTheme.BLACKOUT,
                            onClick = { viewModel.setTheme(AppTheme.BLACKOUT) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Both palettes keep the acid accent and editorial type. Blackout reduces power use on OLED panels.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(
                        index = "04",
                        title = "REMINDERS",
                        trailing = if (state.settings.remindersEnabled) "ON" else "OFF",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "One quiet note a day, only when something is actually ready in Today.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.settings.remindersEnabled,
                                role = Role.Switch,
                                onValueChange = onRemindersToggle,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("DAILY REMINDER", color = colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.settings.remindersEnabled) {
                                    "A single reminder at your chosen time. Never on an empty Today."
                                } else {
                                    "Off. AFTERLATER will not send reminders."
                                },
                                AfterType.BodySmall,
                                colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.settings.remindersEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentInk,
                                checkedTrackColor = colors.accent,
                                checkedBorderColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.borderStrong,
                            ),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("TIME", color = colors.textDim)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                reminderTimeLabel(
                                    state.settings.reminderHour,
                                    state.settings.reminderMinute,
                                ),
                                AfterType.DisplaySmall,
                                colors.textPrimary,
                            )
                        }
                        AfterButton(
                            label = "Change time",
                            onClick = { showReminderTime = true },
                            style = AfterButtonStyle.OUTLINE,
                            icon = AfterIcons.Clock,
                        )
                    }
                    if (!notificationsAllowed) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Android notifications are off for AFTERLATER. Reminders cannot be delivered until you allow them.",
                            AfterType.BodySmall,
                            colors.danger,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextAction(
                            label = "Open notification settings",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    },
                                )
                            },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(
                        index = "05",
                        title = "TIKTOK AUTO-DETECT",
                        trailing = if (state.settings.tiktokAutoDetectEnabled) "ON" else "OFF",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Experimental. When TikTok exposes a video link in its share sheet, AFTERLATER offers a local save prompt. It never guesses a link and never saves without your tap.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.settings.tiktokAutoDetectEnabled,
                                role = Role.Switch,
                                onValueChange = { enabled: Boolean ->
                                    viewModel.setTikTokAutoDetectEnabled(enabled)
                                    if (enabled && (!accessibilityEnabled || !overlayAllowed)) {
                                        viewModel.showMessage(
                                            "Enable both permissions below to use TikTok auto-detect.",
                                        )
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("DETECT IN TIKTOK", color = colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.settings.tiktokAutoDetectEnabled) {
                                    "Watching TikTok's share sheet for a video link."
                                } else {
                                    "Off. AFTERLATER does not inspect TikTok."
                                },
                                AfterType.BodySmall,
                                colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.settings.tiktokAutoDetectEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentInk,
                                checkedTrackColor = colors.accent,
                                checkedBorderColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.borderStrong,
                            ),
                        )
                    }
                    if (state.settings.tiktokAutoDetectEnabled) {
                        Spacer(Modifier.height(14.dp))
                        AutoDetectPermissionRow(
                            label = "ACCESSIBILITY SERVICE",
                            status = if (accessibilityEnabled) "ENABLED" else "NOT ENABLED",
                            granted = accessibilityEnabled,
                            actionLabel = if (accessibilityEnabled) "Open settings" else "Enable",
                            onAction = {
                                context.startActivity(AutoDetectPermissions.accessibilitySettingsIntent())
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        AutoDetectPermissionRow(
                            label = "DISPLAY OVER OTHER APPS",
                            status = if (overlayAllowed) "ALLOWED" else "NOT ALLOWED",
                            granted = overlayAllowed,
                            actionLabel = if (overlayAllowed) "Open settings" else "Allow",
                            onAction = {
                                context.startActivity(AutoDetectPermissions.overlaySettingsIntent(context))
                            },
                        )
                        if (!accessibilityEnabled || !overlayAllowed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Both permissions are required. Until they are enabled, no TikTok prompt can appear.",
                                AfterType.BodySmall,
                                colors.danger,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Only a URL actually exposed by TikTok's share sheet is used. If TikTok hides it, no prompt appears.",
                            AfterType.MonoSmall,
                            colors.textDim,
                        )
                        if (BuildConfig.DEBUG) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Debug builds log detected accessibility text under AFTERLATER-DETECT.",
                                AfterType.MonoSmall,
                                colors.textDim,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(
                        index = "06",
                        title = "CLIPBOARD CARD",
                        trailing = if (state.settings.clipboardCaptureEnabled) "ON" else "OFF",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Copy a link in any app and a card slides in from the right. Save it in one tap, then say whether it should surface today and whether it expires.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.settings.clipboardCaptureEnabled,
                                role = Role.Switch,
                                onValueChange = { enabled: Boolean ->
                                    viewModel.setClipboardCaptureEnabled(enabled)
                                    if (enabled && (!accessibilityEnabled || !overlayAllowed)) {
                                        viewModel.showMessage(
                                            "Enable both permissions above to use the clipboard card.",
                                        )
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("WATCH THE CLIPBOARD", color = colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.settings.clipboardCaptureEnabled) {
                                    "A copied link opens the card. The text is read on this device only."
                                } else {
                                    "Off. AFTERLATER does not read the clipboard."
                                },
                                AfterType.BodySmall,
                                colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.settings.clipboardCaptureEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentInk,
                                checkedTrackColor = colors.accent,
                                checkedBorderColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.borderStrong,
                            ),
                        )
                    }
                    if (state.settings.clipboardCaptureEnabled) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Android only lets a background app read the clipboard while it has input focus, so the card takes focus for a few frames before reading. Nothing is saved without your tap and the text never leaves the device.",
                            AfterType.MonoSmall,
                            colors.textDim,
                        )
                        if (!accessibilityEnabled || !overlayAllowed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Both permissions above are required for the clipboard card as well.",
                                AfterType.BodySmall,
                                colors.danger,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(
                        index = "07",
                        title = "YOUR DATA",
                        trailing = TimeFormat.countLabel(state.totalItems, "ENTRY", "ENTRIES"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Everything lives in a local database on this device. Export a readable JSON copy or restore one you saved earlier. Restore only adds entries that are missing.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    AfterButton(
                        label = if (exporting) "Exporting…" else "Export JSON",
                        onClick = { exportLauncher.launch(exportName) },
                        enabled = !exporting && !inspecting && !restoring,
                        icon = AfterIcons.ArrowUpRight,
                    )
                    Spacer(Modifier.height(8.dp))
                    AfterButton(
                        label = if (inspecting) "Reading…" else "Restore JSON",
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                        enabled = !exporting && !inspecting && !restoring,
                        style = AfterButtonStyle.OUTLINE,
                        icon = AfterIcons.ArchiveBox,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Uses the system file picker. You will see exactly what will be added before anything is restored. Existing entries are never overwritten or deleted.",
                        AfterType.MonoSmall,
                        colors.textDim,
                    )

                    Spacer(Modifier.height(20.dp))
                    Hairline()
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.autoBackup.enabled,
                                role = Role.Switch,
                                onValueChange = viewModel::setAutoBackupEnabled,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("AUTOMATIC BACKUP", color = colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.autoBackup.enabled) {
                                    "A copy is written to the chosen folder once a week."
                                } else {
                                    "Off. The archive is only saved when you export it yourself."
                                },
                                AfterType.BodySmall,
                                colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.autoBackup.enabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentInk,
                                checkedTrackColor = colors.accent,
                                checkedBorderColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface,
                                uncheckedBorderColor = colors.borderStrong,
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Mono("FOLDER", color = colors.textDim)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.autoBackup.folderName ?: "NOT CHOSEN",
                                AfterType.MonoSmall,
                                if (state.autoBackup.folderName == null) colors.textDim else colors.textPrimary,
                                maxLines = 1,
                            )
                        }
                        TextAction(
                            label = if (state.autoBackup.folderName == null) "Choose folder" else "Change",
                            onClick = { backupFolderLauncher.launch(null) },
                        )
                    }
                    val lastBackupAt = state.autoBackup.lastBackupAtMillis
                    if (lastBackupAt != null) {
                        Spacer(Modifier.height(8.dp))
                        Mono(
                            "LAST BACKUP / " + TimeFormat.dateTime(Instant.ofEpochMilli(lastBackupAt)),
                            color = colors.accent,
                            style = AfterType.MonoSmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Writes afterlater-archive-auto.json to the chosen folder once a week, overwriting the previous copy. Nothing is uploaded; if the folder is on a synced drive, that is between you and the drive.",
                        AfterType.MonoSmall,
                        colors.textDim,
                    )

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(index = "08", title = "ABOUT")
                    Spacer(Modifier.height(4.dp))
                    LabeledValue("Version", BuildConfig.VERSION_NAME)
                    LabeledValue("Archive", TimeFormat.countLabel(state.totalItems, "ENTRY", "ENTRIES"))
                    LabeledValue("Storage", "ON DEVICE / ROOM + DATASTORE")
                    LabeledValue("Network", "PREVIEWS ONLY / OPTIONAL")
                    LabeledValue("Account", "NONE")

                    Spacer(Modifier.height(26.dp))
                    Text(
                        "AFTERLATER / A PERSONAL INTERNET ARCHIVE",
                        AfterType.MonoSmall,
                        colors.textDim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Save first. Read later. Keep what matters.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) { data -> Snackbar(snackbarData = data) }
        }
    }

    pendingRestore?.let { preview ->
        RestoreBackupDialog(
            preview = preview,
            restoring = restoring,
            onDismiss = viewModel::cancelRestore,
            onConfirm = viewModel::confirmRestore,
        )
    }

    if (showReminderTime) {
        ReminderTimeDialog(
            initialHour = state.settings.reminderHour,
            initialMinute = state.settings.reminderMinute,
            onDismiss = { showReminderTime = false },
            onConfirm = { hour, minute ->
                showReminderTime = false
                viewModel.setReminderTime(hour, minute)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val picker = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 0.dp,
        title = {
            Text("REMINDER TIME", AfterType.DisplaySmall, colors.textPrimary)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = picker)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Only one reminder is sent each day, and only when Today has something to review.",
                    AfterType.BodySmall,
                    colors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextAction(
                label = "Set time",
                onClick = { onConfirm(picker.hour, picker.minute) },
            )
        },
        dismissButton = {
            TextAction(
                label = "Cancel",
                onClick = onDismiss,
                color = colors.textSecondary,
            )
        },
    )
}

private fun reminderTimeLabel(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

@Composable
private fun AutoDetectPermissionRow(
    label: String,
    status: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Mono(label, color = colors.textDim)
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                AfterType.MonoSmall,
                if (granted) colors.accent else colors.danger,
            )
        }
        TextAction(label = actionLabel, onClick = onAction)
    }
}

@Composable
private fun RestoreBackupDialog(
    preview: RestorePreview,
    restoring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    AlertDialog(
        onDismissRequest = { if (!restoring) onDismiss() },
        containerColor = colors.surface,
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 0.dp,
        title = {
            Text("RESTORE FROM BACKUP?", AfterType.DisplaySmall, colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Backup from ${TimeFormat.dateTime(preview.exportedAt)} contains ${entryLabel(preview.backupItemCount)}.",
                    AfterType.Body,
                    colors.textSecondary,
                )
                Text(
                    if (preview.newItemCount == 0) {
                        "Nothing new will be added. Your archive will stay exactly as it is."
                    } else {
                        "${entryLabel(preview.newItemCount)} will be added with their notes, statuses, and review dates."
                    },
                    AfterType.Body,
                    colors.textPrimary,
                )
                if (preview.duplicateCount > 0) {
                    Text(
                        "${entryLabel(preview.duplicateCount)} already exist or repeat in this backup. They will be left unchanged.",
                        AfterType.Body,
                        colors.textSecondary,
                    )
                }
                Text(
                    "No existing entry will be overwritten or deleted.",
                    AfterType.MonoSmall,
                    colors.accent,
                )
            }
        },
        confirmButton = {
            TextAction(
                label = if (restoring) {
                    "Restoring…"
                } else if (preview.newItemCount == 0) {
                    "Nothing to restore"
                } else {
                    "Restore ${preview.newItemCount}"
                },
                onClick = onConfirm,
                enabled = !restoring && preview.newItemCount > 0,
            )
        },
        dismissButton = {
            TextAction(
                label = "Cancel",
                onClick = onDismiss,
                color = colors.textSecondary,
                enabled = !restoring,
            )
        },
    )
}

private fun entryLabel(count: Int): String =
    if (count == 1) "1 entry" else "$count entries"

@Composable
private fun ThemeChoice(
    label: String,
    note: String,
    palette: AfterLaterColors,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(if (selected) colors.surface else colors.background)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(1.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(18.dp).background(palette.background).border(1.dp, palette.border))
            Box(Modifier.size(18.dp).background(palette.surface))
            Box(Modifier.size(18.dp).background(palette.accent))
        }
        Spacer(Modifier.height(12.dp))
        Mono(label, color = if (selected) colors.accent else colors.textPrimary, style = AfterType.Mono)
        Spacer(Modifier.height(3.dp))
        Text(note, AfterType.MonoSmall, colors.textDim, maxLines = 1)
    }
}

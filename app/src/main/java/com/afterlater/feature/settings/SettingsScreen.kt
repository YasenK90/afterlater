package com.afterlater.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.BuildConfig
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
import com.afterlater.core.designsystem.Wordmark
import com.afterlater.core.model.AppSettings
import com.afterlater.core.model.AppTheme
import com.afterlater.core.util.TimeFormat
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = LocalAfterLaterColors.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val exportName = remember { "afterlater-archive-${LocalDate.now()}.json" }

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
                            modifier = Modifier.width(64.dp),
                            style = AfterButtonStyle.OUTLINE,
                            enabled = state.settings.dailyReviewLimit > AppSettings.MIN_DAILY_LIMIT,
                        )
                        AfterButton(
                            label = "+",
                            onClick = { viewModel.setDailyLimit(state.settings.dailyReviewLimit + 1) },
                            modifier = Modifier.width(64.dp),
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
                        title = "YOUR DATA",
                        trailing = TimeFormat.countLabel(state.totalItems, "ENTRY", "ENTRIES"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Everything lives in a local database on this device. Export a readable JSON copy whenever you want. Import is intentionally not implemented.",
                        AfterType.BodySmall,
                        colors.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    AfterButton(
                        label = if (exporting) "Exporting…" else "Export JSON",
                        onClick = { exportLauncher.launch(exportName) },
                        enabled = !exporting,
                        icon = AfterIcons.ArrowUpRight,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Uses the system file picker. AFTERLATER never requests storage access.",
                        AfterType.MonoSmall,
                        colors.textDim,
                    )

                    Spacer(Modifier.height(24.dp))
                    Hairline()
                    Spacer(Modifier.height(18.dp))
                    SectionMark(index = "05", title = "ABOUT")
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
}

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

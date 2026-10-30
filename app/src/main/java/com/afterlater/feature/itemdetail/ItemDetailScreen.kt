package com.afterlater.feature.itemdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterTextField
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.BottomScrim
import com.afterlater.core.designsystem.DeadlineDialog
import com.afterlater.core.designsystem.DomainLine
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.IconAction
import com.afterlater.core.designsystem.ItemArtwork
import com.afterlater.core.designsystem.LabeledValue
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.SnoozeDialog
import com.afterlater.core.designsystem.StatusTag
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.rememberHaptics
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.SavedItem
import com.afterlater.core.util.LinkOpener
import com.afterlater.domain.review.ExpiryChoice
import com.afterlater.core.util.TimeFormat
import java.time.Instant

@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val colors = LocalAfterLaterColors.current
    val now = remember { Instant.now() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSnoozeDialog by remember { mutableStateOf(false) }
    var showDeadlineDialog by remember { mutableStateOf(false) }

    val leaf: () -> Unit = {
        if (state.noteDirty) {
            viewModel.saveNote(onSaved = onBack)
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true, onBack = leaf)

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val notice = state.message
    LaunchedEffect(notice) {
        if (notice != null) {
            val result = snackbarHostState.showSnackbar(
                message = notice.text,
                actionLabel = notice.undo?.let { "UNDO" },
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            viewModel.clearMessage()
            if (result == SnackbarResult.ActionPerformed) {
                notice.undo?.let(viewModel::undo)
            }
        }
    }

    val item = state.item

    ArchiveBackground {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(status = item?.status, onBack = leaf)
                when {
                    state.loading -> DetailLoading(Modifier.weight(1f))
                    item == null -> MissingEntry(onBack = onBack, modifier = Modifier.weight(1f))
                    else -> DetailBody(
                        item = item,
                        state = state,
                        now = now,
                        modifier = Modifier.weight(1f),
                        onOpenLink = {
                            if (LinkOpener.open(context, item.originalUrl)) {
                                viewModel.recordOpen()
                            } else {
                                viewModel.showMessage("No app can open this link.")
                            }
                        },
                        onUseful = {
                            haptics.confirm()
                            viewModel.markUseful()
                        },
                        onSnooze = {
                            haptics.tick()
                            showSnoozeDialog = true
                        },
                        onArchive = {
                            haptics.confirm()
                            viewModel.archive()
                        },
                        onRestore = {
                            haptics.confirm()
                            viewModel.restore()
                        },
                        onRetryMetadata = viewModel::retryMetadata,
                        onOpenDeadline = { showDeadlineDialog = true },
                        onDeadline = viewModel::setDeadline,
                        onNoteChange = viewModel::onNoteChange,
                        onSaveNote = { viewModel.saveNote() },
                        onDelete = { showDeleteDialog = true },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.surfaceRaised,
                    contentColor = colors.textPrimary,
                    shape = RoundedCornerShape(1.dp),
                    modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(1.dp)),
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteEntryDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                haptics.reject()
                viewModel.delete()
            },
        )
    }

    if (showSnoozeDialog) {
        SnoozeDialog(
            onDismiss = { showSnoozeDialog = false },
            onSelect = { window ->
                showSnoozeDialog = false
                viewModel.snooze(window)
            },
        )
    }

    if (showDeadlineDialog) {
        DeadlineDialog(
            onDismiss = { showDeadlineDialog = false },
            onSelect = { choice ->
                showDeadlineDialog = false
                viewModel.setDeadline(choice)
            },
        )
    }
}

@Composable
private fun DetailTopBar(status: ItemStatus?, onBack: () -> Unit) {
    val colors = LocalAfterLaterColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(8.dp))
            Mono("ENTRY", color = colors.textDim, style = AfterType.MonoSmall)
            Spacer(Modifier.weight(1f))
            if (status != null) {
                StatusTag(status)
                Spacer(Modifier.width(10.dp))
            }
        }
        Hairline()
    }
}

@Composable
private fun DetailBody(
    item: SavedItem,
    state: ItemDetailUiState,
    now: Instant,
    modifier: Modifier = Modifier,
    onOpenLink: () -> Unit,
    onUseful: () -> Unit,
    onSnooze: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onRetryMetadata: () -> Unit,
    onOpenDeadline: () -> Unit,
    onDeadline: (ExpiryChoice) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val description = item.description?.trim()?.takeIf { it.isNotEmpty() }
    val actionsEnabled = !state.busy
    val expired = item.isExpired(now)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            ItemArtwork(
                thumbnailUrl = item.thumbnailUrl,
                domain = item.domain,
                modifier = Modifier.fillMaxSize(),
                contentDescription = item.displayTitle,
            )
            BottomScrim(Modifier.align(Alignment.BottomCenter), height = 96.dp)
            Box(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                DomainLine(item.domain, color = colors.textPrimary)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = item.displayTitle,
                style = AfterType.DisplayMedium.copy(fontSize = 30.sp, lineHeight = 31.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(
                    TimeFormat.date(item.savedAt),
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(14.dp).height(1.dp).background(colors.borderStrong))
                Spacer(Modifier.width(10.dp))
                Mono(
                    statusLabel(item.status),
                    color = if (item.status == ItemStatus.USEFUL) colors.accent else colors.textSecondary,
                    style = AfterType.MonoSmall,
                )
            }

            if (description != null) {
                Spacer(Modifier.height(14.dp))
                Text(description, AfterType.BodyLarge, colors.textSecondary, maxLines = 7)
            }

            Spacer(Modifier.height(20.dp))
            AfterButton(
                label = "Open link",
                onClick = onOpenLink,
                modifier = Modifier.fillMaxWidth(),
                icon = AfterIcons.ArrowUpRight,
                enabled = actionsEnabled,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AfterButton(
                    label = "Useful",
                    onClick = onUseful,
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                    enabled = actionsEnabled && item.status != ItemStatus.USEFUL,
                )
                AfterButton(
                    label = "Snooze",
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                    enabled = actionsEnabled && item.status != ItemStatus.ARCHIVED && item.status != ItemStatus.USEFUL,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AfterButton(
                    label = "Archive",
                    onClick = onArchive,
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.ArchiveBox,
                    enabled = actionsEnabled && item.status != ItemStatus.ARCHIVED,
                )
                AfterButton(
                    label = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.DANGER,
                    enabled = actionsEnabled,
                )
            }
            if (item.status == ItemStatus.USEFUL || item.status == ItemStatus.ARCHIVED) {
                Spacer(Modifier.height(8.dp))
                AfterButton(
                    label = "Return to today",
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                    enabled = actionsEnabled,
                )
            }

            Spacer(Modifier.height(24.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            SectionMark(index = "01", title = "SCHEDULE")
            Spacer(Modifier.height(6.dp))
            LabeledValue("Status", statusLabel(item.status))
            LabeledValue("Saved", TimeFormat.dateTime(item.savedAt))
            LabeledValue(
                "Last opened",
                item.lastReviewedAt?.let { TimeFormat.dateTime(it) } ?: "NEVER",
            )
            LabeledValue(
                "Next review",
                if (item.isReviewable && !expired) {
                    TimeFormat.dueLabel(item.nextReviewAt, now)
                } else {
                    "NOT IN ROTATION"
                },
            )
            if (item.snoozedUntil != null) {
                LabeledValue("Snoozed until", TimeFormat.dateTime(item.snoozedUntil))
            }
            LabeledValue(
                label = "Deadline",
                value = item.expiresAt?.let { TimeFormat.expiryLabel(it, now) } ?: "NONE",
                valueColor = when {
                    item.expiresAt == null -> colors.textDim
                    expired -> colors.danger
                    else -> colors.accent
                },
            )
            if (item.expiresAt != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction(
                        label = "Change deadline",
                        onClick = onOpenDeadline,
                        enabled = actionsEnabled,
                        color = colors.accent,
                    )
                    Spacer(Modifier.weight(1f))
                    TextAction(
                        label = "Clear",
                        onClick = { onDeadline(ExpiryChoice.NONE) },
                        enabled = actionsEnabled,
                        color = colors.textSecondary,
                    )
                }
            } else {
                Spacer(Modifier.height(2.dp))
                TextAction(
                    label = "Set a deadline",
                    onClick = onOpenDeadline,
                    enabled = actionsEnabled,
                    color = colors.accent,
                )
            }
            LabeledValue(
                label = "Preview",
                value = metadataLabel(item.metadataStatus),
                valueColor = when (item.metadataStatus) {
                    MetadataStatus.FAILED -> colors.danger
                    MetadataStatus.PENDING -> colors.accent
                    MetadataStatus.SUCCESS -> colors.textPrimary
                },
            )
            if (item.metadataStatus != MetadataStatus.SUCCESS) {
                Spacer(Modifier.height(2.dp))
                TextAction(
                    label = "Retry preview",
                    onClick = onRetryMetadata,
                    enabled = actionsEnabled,
                    color = if (item.metadataStatus == MetadataStatus.FAILED) colors.danger else colors.accent,
                )
            }

            Spacer(Modifier.height(18.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            SectionMark(index = "02", title = "YOUR NOTE")
            Spacer(Modifier.height(10.dp))
            AfterTextField(
                value = state.noteDraft,
                onValueChange = onNoteChange,
                placeholder = "WHAT MADE THIS WORTH KEEPING?",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 118.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction(
                    label = if (state.savingNote) "Saving…" else "Save note",
                    onClick = onSaveNote,
                    enabled = state.noteDirty && !state.savingNote,
                    color = if (state.noteDirty) colors.accent else colors.textDim,
                )
                Spacer(Modifier.weight(1f))
                Mono(
                    "${state.noteDraft.length} / ${ItemDetailViewModel.MAX_NOTE_LENGTH}",
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
            }

            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            SectionMark(index = "03", title = "ORIGINAL")
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.originalUrl,
                style = AfterType.Mono.copy(letterSpacing = 0.sp, lineHeight = 17.sp),
                color = colors.textSecondary,
                maxLines = 4,
            )
            if (item.canonicalUrl != item.originalUrl) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "CANONICAL / ${item.canonicalUrl}",
                    style = AfterType.MonoSmall,
                    color = colors.textDim,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun DetailLoading(modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Mono("LOADING ENTRY…", color = colors.textDim, style = AfterType.MonoSmall)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(colors.surface)
                .border(1.dp, colors.border),
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(24.dp).background(colors.surface))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).background(colors.surface))
    }
}

@Composable
private fun MissingEntry(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Mono("ENTRY MISSING", color = colors.accent, style = AfterType.MonoSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "THIS ENTRY\nIS GONE.",
            AfterType.DisplayHero.copy(fontSize = 40.sp, lineHeight = 39.sp),
            colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "It may have been deleted from another screen.",
            AfterType.Body,
            colors.textSecondary,
        )
        Spacer(Modifier.height(22.dp))
        AfterButton(
            label = "Back to archive",
            onClick = onBack,
            style = AfterButtonStyle.OUTLINE,
        )
    }
}

@Composable
private fun DeleteEntryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = LocalAfterLaterColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 0.dp,
        title = {
            Text("DELETE THIS ENTRY?", AfterType.DisplaySmall, colors.textPrimary)
        },
        text = {
            Text(
                "The link, your note, and every review event will be removed permanently. This cannot be undone.",
                AfterType.Body,
                colors.textSecondary,
            )
        },
        confirmButton = {
            TextAction(
                label = "Delete forever",
                onClick = onConfirm,
                color = colors.danger,
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

private fun statusLabel(status: ItemStatus): String = when (status) {
    ItemStatus.NEW -> "NEW"
    ItemStatus.READY -> "READY"
    ItemStatus.SNOOZED -> "SNOOZED"
    ItemStatus.USEFUL -> "USEFUL"
    ItemStatus.ARCHIVED -> "ARCHIVED"
}

private fun metadataLabel(status: MetadataStatus): String = when (status) {
    MetadataStatus.PENDING -> "FETCHING"
    MetadataStatus.SUCCESS -> "CLEARED"
    MetadataStatus.FAILED -> "FAILED"
}

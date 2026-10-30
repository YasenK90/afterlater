package com.afterlater.feature.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterTextField
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.BottomScrim
import com.afterlater.core.designsystem.DomainLine
import com.afterlater.core.designsystem.FilterPill
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.IconAction
import com.afterlater.core.designsystem.ItemArtwork
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.Selector
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.StatusTag
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.Wordmark
import com.afterlater.core.model.ArchiveSort
import com.afterlater.core.model.ItemStatus
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.model.SavedItem
import com.afterlater.core.util.TimeFormat
import java.time.Instant

private val sortOptions = listOf(
    ArchiveSort.NEWEST.name to "NEWEST",
    ArchiveSort.OLDEST.name to "OLDEST",
    ArchiveSort.RECENTLY_REVIEWED.name to "LAST REVIEWED",
)

private fun ArchiveSort.label(): String = when (this) {
    ArchiveSort.NEWEST -> "NEWEST"
    ArchiveSort.OLDEST -> "OLDEST"
    ArchiveSort.RECENTLY_REVIEWED -> "LAST REVIEWED"
}

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onOpenItem: (String) -> Unit,
    onSaveNew: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val domainFilter by viewModel.domainFilter.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val entries by viewModel.items.collectAsStateWithLifecycle()
    val domains by viewModel.domains.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val colors = LocalAfterLaterColors.current
    val focusManager = LocalFocusManager.current
    val now = remember { Instant.now() }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
    val hasFilters = query.isNotBlank() || statusFilter != ArchiveStatusFilter.ALL || domainFilter != null
    val domainValue = domainFilter ?: "ALL"

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
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
                Mono(
                    text = if (totalCount >= 0) "${totalCount.toString().padStart(2, '0')} SAVED" else "— SAVED",
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
            }
            Hairline()

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "THE ARCHIVE",
                        style = AfterType.DisplayLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .clickable(role = Role.Button, onClick = viewModel::toggleLayout)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (layout == ArchiveLayout.VISUAL) "COMPACT" else "VISUAL",
                            style = AfterType.MonoSmall,
                            color = colors.accent,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AfterTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "SEARCH TITLE, URL, DOMAIN, NOTE",
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                        onDone = { focusManager.clearFocus() },
                        leading = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textDim,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        IconAction(
                            icon = Icons.Default.Close,
                            contentDescription = "Clear search",
                            onClick = { viewModel.setQuery("") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArchiveStatusFilter.entries.forEach { option ->
                    FilterPill(
                        label = option.label,
                        selected = statusFilter == option,
                        onClick = { viewModel.setStatus(option) },
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Selector(
                    caption = "SOURCE",
                    value = domainValue,
                    options = listOf("ALL" to "ALL SOURCES") + domains.map { it to it },
                    onSelect = { viewModel.setDomain(if (it == "ALL") null else it) },
                    modifier = Modifier.weight(1f),
                    selectedId = domainValue,
                )
                Selector(
                    caption = "ORDER",
                    value = sort.label(),
                    options = sortOptions,
                    onSelect = { viewModel.setSort(ArchiveSort.valueOf(it)) },
                    modifier = Modifier.weight(1f),
                    selectedId = sort.name,
                )
            }
            Hairline()

            when {
                totalCount < 0 -> ArchiveLoading(Modifier.weight(1f))
                entries.isEmpty() -> ArchiveEmpty(
                    hasFilters = hasFilters,
                    onClearFilters = viewModel::clearFilters,
                    onSaveNew = onSaveNew,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.id }) { item ->
                        if (layout == ArchiveLayout.VISUAL) {
                            ArchiveVisualCard(
                                item = item,
                                now = now,
                                busy = busy,
                                onOpen = { onOpenItem(item.id) },
                                onRestore = { viewModel.restore(item.id) },
                                onDelete = { pendingDelete = item },
                            )
                        } else {
                            ArchiveCompactRow(
                                item = item,
                                now = now,
                                busy = busy,
                                onOpen = { onOpenItem(item.id) },
                                onRestore = { viewModel.restore(item.id) },
                                onDelete = { pendingDelete = item },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        SectionMark(
                            index = "END",
                            title = "INDEX",
                            trailing = TimeFormat.countLabel(entries.size, "ENTRY", "ENTRIES"),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
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

    pendingDelete?.let { item ->
        DeleteArchiveDialog(
            title = item.displayTitle,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                viewModel.delete(item.id)
            },
        )
    }
}

@Composable
private fun ArchiveVisualCard(
    item: SavedItem,
    now: Instant,
    busy: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val note = item.personalNote?.trim()?.takeIf { it.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, colors.border, RoundedCornerShape(2.dp))
            .background(colors.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(role = Role.Button, onClick = onOpen),
        ) {
            ItemArtwork(
                thumbnailUrl = item.thumbnailUrl,
                domain = item.domain,
                modifier = Modifier.fillMaxSize(),
                contentDescription = item.displayTitle,
            )
            BottomScrim(Modifier.align(Alignment.BottomCenter), height = 92.dp)
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                DomainLine(item.domain, color = colors.textPrimary)
            }
            StatusTag(
                status = item.status,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(colors.background.copy(alpha = 0.82f)),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(
                    TimeFormat.relativePast(item.savedAt, now),
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
                item.expiresAt?.let { deadline ->
                    Spacer(Modifier.width(8.dp))
                    Mono(
                        TimeFormat.expiryLabel(deadline, now),
                        color = if (item.isExpired(now)) colors.danger else colors.textSecondary,
                        style = AfterType.MonoSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.metadataStatus == MetadataStatus.FAILED) {
                    Mono("PREVIEW FAILED", color = colors.danger, style = AfterType.MonoSmall)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.displayTitle,
                style = AfterType.DisplaySmall.copy(fontSize = 22.sp, lineHeight = 24.sp),
                color = colors.textPrimary,
                maxLines = 2,
            )
            if (note != null) {
                Spacer(Modifier.height(9.dp))
                Row {
                    Box(Modifier.width(2.dp).height(30.dp).background(colors.accent))
                    Spacer(Modifier.width(9.dp))
                    Text(note, AfterType.BodySmall, colors.textSecondary, maxLines = 2)
                }
            }
        }
        ArchiveActionRow(
            showRestore = item.status == ItemStatus.ARCHIVED,
            busy = busy,
            onRestore = onRestore,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ArchiveCompactRow(
    item: SavedItem,
    now: Instant,
    busy: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val note = item.personalNote?.trim()?.takeIf { it.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemArtwork(
                thumbnailUrl = item.thumbnailUrl,
                domain = item.domain,
                modifier = Modifier.size(58.dp),
                contentDescription = item.displayTitle,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    style = AfterType.BodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                DomainLine(item.domain, color = colors.textDim)
                item.expiresAt?.let { deadline ->
                    Spacer(Modifier.height(4.dp))
                    Mono(
                        TimeFormat.expiryLabel(deadline, now),
                        color = if (item.isExpired(now)) colors.danger else colors.textSecondary,
                        style = AfterType.MonoSmall,
                    )
                }
                if (note != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(note, AfterType.BodySmall, colors.textSecondary, maxLines = 1)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusTag(item.status)
                Spacer(Modifier.height(7.dp))
                Mono(
                    TimeFormat.relativePast(item.savedAt, now),
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
            }
        }
        ArchiveActionRow(
            showRestore = item.status == ItemStatus.ARCHIVED,
            busy = busy,
            onRestore = onRestore,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ArchiveActionRow(
    showRestore: Boolean,
    busy: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    Hairline()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRestore) {
            TextAction(label = "Restore", onClick = onRestore, enabled = !busy)
        }
        Spacer(Modifier.weight(1f))
        TextAction(
            label = "Delete",
            onClick = onDelete,
            color = colors.danger,
            enabled = !busy,
        )
    }
}

@Composable
private fun DeleteArchiveDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
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
                "“$title” and its note, link, and review history will be removed permanently. This cannot be undone.",
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

@Composable
private fun ArchiveLoading(modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mono("READING INDEX…", color = colors.textDim, style = AfterType.MonoSmall)
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(colors.surface)
                    .border(1.dp, colors.border),
            )
        }
    }
}

@Composable
private fun ArchiveEmpty(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onSaveNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAfterLaterColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 34.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Mono(
                text = if (hasFilters) "NO MATCHES" else "NO ENTRIES",
                color = colors.accent,
                style = AfterType.MonoSmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hasFilters) "THE INDEX\nHAS NO\nMATCHES." else "THE ARCHIVE\nIS EMPTY.",
                style = AfterType.DisplayHero.copy(fontSize = 40.sp, lineHeight = 39.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (hasFilters) {
                    "Try a broader phrase, or drop the status and source filters."
                } else {
                    "Everything you save lands here: searchable, offline, and yours."
                },
                style = AfterType.Body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(22.dp))
            if (hasFilters) {
                AfterButton(
                    label = "Clear filters",
                    onClick = onClearFilters,
                    style = AfterButtonStyle.OUTLINE,
                )
            } else {
                AfterButton(
                    label = "Save a link",
                    onClick = onSaveNew,
                )
            }
        }
    }
}

package com.afterlater.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.BottomScrim
import com.afterlater.core.designsystem.DomainLine
import com.afterlater.core.designsystem.DottedRule
import com.afterlater.core.designsystem.IconAction
import com.afterlater.core.designsystem.ItemArtwork
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.SnoozeDialog
import com.afterlater.core.designsystem.StatusTag
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.Wordmark
import com.afterlater.core.designsystem.rememberHaptics
import com.afterlater.core.model.SavedItem
import com.afterlater.core.util.LinkOpener
import com.afterlater.core.util.TimeFormat
import java.time.Instant
import java.util.Locale

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenQuickAdd: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    val colors = LocalAfterLaterColors.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val compact = LocalConfiguration.current.screenWidthDp < 380
    var snoozeTarget by remember { mutableStateOf<SavedItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { notice ->
            val result = snackbarHostState.showSnackbar(
                message = notice.text,
                actionLabel = notice.undo?.let { "UNDO" },
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                notice.undo?.let(viewModel::undo)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ArchiveBackground {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 132.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Wordmark()
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(5.dp).background(colors.accent))
                                Spacer(Modifier.width(6.dp))
                                Mono("LOCAL / OFFLINE", color = colors.textSecondary, style = AfterType.MonoSmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Mono("DEVICE ARCHIVE", color = colors.textDim, style = AfterType.MonoSmall)
                        }
                    }
                }

                item { Spacer(Modifier.height(34.dp)) }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(TimeFormat.date(state.now), color = colors.textSecondary, style = AfterType.MonoSmall)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(colors.border))
                        Spacer(Modifier.width(12.dp))
                        Mono(
                            text = "${state.dueCount.toString().padStart(2, '0')} DUE",
                            color = if (state.dueCount > 0) colors.accent else colors.textDim,
                            style = AfterType.MonoSmall,
                        )
                    }
                }

                item { Spacer(Modifier.height(22.dp)) }

                item {
                    Text(
                        text = "THE THINGS\nYOU MEANT TO\nRETURN TO",
                        style = if (compact) AfterType.DisplayLarge else AfterType.DisplayHero,
                        color = colors.textPrimary,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .width(72.dp)
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }

                item { Spacer(Modifier.height(20.dp)) }

                item {
                    Text(
                        text = "A private shelf for the internet. Save a link now; decide what it meant later.",
                        style = AfterType.Body,
                        color = colors.textSecondary,
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
                            .background(colors.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.dueCount.toString().padStart(3, '0'),
                            style = AfterType.DisplaySmall,
                            color = colors.accent,
                        )
                        Spacer(Modifier.width(10.dp))
                        Mono("AWAITING REDISCOVERY", color = colors.textSecondary, style = AfterType.MonoSmall)
                        Spacer(Modifier.weight(1f))
                        Mono("${state.totalSaved.toString().padStart(3, '0')} SAVED", color = colors.textDim, style = AfterType.MonoSmall)
                    }
                }

                item { Spacer(Modifier.height(30.dp)) }

                if (state.loading) {
                    item { LoadingToday() }
                } else if (state.featured == null) {
                    item {
                        EmptyToday(
                            hasArchive = state.totalSaved > 0,
                            onOpenQuickAdd = onOpenQuickAdd,
                            onOpenArchive = onOpenArchive,
                        )
                    }
                } else {
                    item {
                        SectionMark(
                            index = "01",
                            title = "FEATURED",
                            trailing = "1 OF ${state.dailyLimit}",
                        )
                    }
                    item { Spacer(Modifier.height(14.dp)) }
                    item {
                        FeaturedCard(
                            item = state.featured!!,
                            now = state.now,
                            compact = compact,
                            onOpenLink = {
                                haptics.tick()
                                viewModel.open(it) { url -> LinkOpener.open(context, url) }
                            },
                            onOpenDetail = { featured -> onOpenItem(featured.id) },
                            onSnooze = {
                                haptics.tick()
                                snoozeTarget = it
                            },
                            onUseful = {
                                haptics.confirm()
                                viewModel.markUseful(it)
                            },
                            onArchive = {
                                haptics.confirm()
                                viewModel.archive(it)
                            },
                        )
                    }

                    if (state.secondary.isNotEmpty()) {
                        item { Spacer(Modifier.height(28.dp)) }
                        item {
                            SectionMark(
                                index = "02",
                                title = "ALSO DUE",
                                trailing = "${state.secondary.size} WAITING",
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                        items(
                            count = state.secondary.size,
                            key = { index -> state.secondary[index].id },
                        ) { index ->
                            val item = state.secondary[index]
                            SecondaryCard(
                                item = item,
                                index = index + 2,
                                now = state.now,
                                onOpenDetail = { onOpenItem(item.id) },
                                onOpenLink = {
                                    haptics.tick()
                                    viewModel.open(item) { url -> LinkOpener.open(context, url) }
                                },
                                onSnooze = {
                                    haptics.tick()
                                    snoozeTarget = item
                                },
                                onUseful = {
                                    haptics.confirm()
                                    viewModel.markUseful(item)
                                },
                                onArchive = {
                                    haptics.confirm()
                                    viewModel.archive(item)
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                item { Spacer(Modifier.height(28.dp)) }
                item {
                    DottedRule()
                    Spacer(Modifier.height(14.dp))
                    TextAction(
                        label = "ENTER THE FULL ARCHIVE — ${state.totalSaved} SAVED",
                        onClick = onOpenArchive,
                        icon = AfterIcons.ArrowUpRight,
                    )
                    Spacer(Modifier.height(12.dp))
                    Mono("EVERYTHING STAYS ON THIS DEVICE.", color = colors.textDim, style = AfterType.MonoSmall)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.surfaceRaised,
                contentColor = colors.textPrimary,
                shape = RoundedCornerShape(1.dp),
                modifier = Modifier.border(1.dp, colors.border, RoundedCornerShape(1.dp)),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .size(58.dp)
                .background(colors.accent, RoundedCornerShape(1.dp))
                .clickable(role = Role.Button, onClick = onOpenQuickAdd)
                .semantics {
                    role = Role.Button
                    contentDescription = "Save a link"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.accentInk,
                modifier = Modifier.size(26.dp),
            )
        }

        snoozeTarget?.let { target ->
            SnoozeDialog(
                onDismiss = { snoozeTarget = null },
                onSelect = { window ->
                    snoozeTarget = null
                    viewModel.snooze(target, window)
                },
            )
        }
    }
}

@Composable
private fun LoadingToday() {
    val colors = LocalAfterLaterColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
            .padding(20.dp),
    ) {
        Mono("READING THE LOCAL ARCHIVE", color = colors.textDim)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.border)) {
            Box(Modifier.fillMaxWidth(0.35f).height(2.dp).background(colors.accent))
        }
    }
}

@Composable
private fun EmptyToday(
    hasArchive: Boolean,
    onOpenQuickAdd: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
            .background(colors.surface)
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(colors.background),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val gap = 18.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = colors.border,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = 1f,
                    )
                    x += gap
                }
            }
            Text(
                text = "00",
                style = AfterType.DisplayHero.copy(fontSize = 84.sp, lineHeight = 84.sp),
                color = colors.textPrimary.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                maxLines = 1,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(9.dp)
                    .background(colors.accent),
            )
            Mono(
                "QUEUE CLEAR / SHELF INTACT",
                color = colors.textSecondary,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = if (hasArchive) "NOTHING IS DUE\nRIGHT NOW." else "THE ARCHIVE IS\nWAITING.",
            style = AfterType.DisplayMedium,
            color = colors.textPrimary,
            maxLines = 2,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasArchive) {
                "The next link will surface when its review date arrives. You can always go looking yourself."
            } else {
                "Share a link from any app, or paste one here. The first thing you save becomes the start of the archive."
            },
            style = AfterType.Body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(22.dp))
        AfterButton("SAVE A LINK", onOpenQuickAdd, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        AfterButton(
            label = "BROWSE THE ARCHIVE",
            onClick = onOpenArchive,
            modifier = Modifier.fillMaxWidth(),
            style = AfterButtonStyle.OUTLINE,
        )
    }
}

@Composable
private fun FeaturedCard(
    item: SavedItem,
    now: Instant,
    compact: Boolean,
    onOpenLink: (SavedItem) -> Unit,
    onOpenDetail: (SavedItem) -> Unit,
    onSnooze: (SavedItem) -> Unit,
    onUseful: (SavedItem) -> Unit,
    onArchive: (SavedItem) -> Unit,
) {
    val colors = LocalAfterLaterColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
            .background(colors.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clickable(role = Role.Button) { onOpenDetail(item) },
        ) {
            ItemArtwork(
                thumbnailUrl = item.thumbnailUrl,
                domain = item.domain,
                modifier = Modifier.fillMaxSize(),
                contentDescription = item.displayTitle,
            )
            BottomScrim(
                modifier = Modifier.align(Alignment.BottomCenter),
                height = 96.dp,
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(colors.background.copy(alpha = 0.86f))
                    .border(1.dp, colors.border, RoundedCornerShape(1.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Mono("01", color = colors.accent, style = AfterType.MonoMedium)
            }
            StatusTag(
                status = item.status,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(colors.background.copy(alpha = 0.86f)),
            )
            DomainLine(
                domain = item.domain,
                color = colors.textPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            )
        }

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(
                    "SAVED ${TimeFormat.relativePast(item.savedAt, now)}",
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
                Spacer(Modifier.weight(1f))
                Mono(
                    TimeFormat.dueLabel(item.nextReviewAt, now),
                    color = colors.accent,
                    style = AfterType.MonoSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.displayTitle,
                style = if (compact) AfterType.DisplaySmall.copy(fontSize = 24.sp) else AfterType.DisplayMedium,
                color = colors.textPrimary,
                modifier = Modifier.clickable(role = Role.Button) { onOpenDetail(item) },
                maxLines = 3,
            )
            if (!item.description.isNullOrBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = item.description.orEmpty(),
                    style = AfterType.Body,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
            if (!item.personalNote.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(2.dp).height(30.dp).background(colors.accent))
                    Spacer(Modifier.width(9.dp))
                    Text(item.personalNote.orEmpty(), AfterType.BodySmall, colors.textSecondary, maxLines = 2)
                }
            }
            Spacer(Modifier.height(18.dp))
            AfterButton(
                label = "OPEN ORIGINAL",
                onClick = { onOpenLink(item) },
                modifier = Modifier.fillMaxWidth(),
                icon = AfterIcons.ArrowUpRight,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AfterButton(
                    label = "SNOOZE",
                    onClick = { onSnooze(item) },
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                )
                AfterButton(
                    label = "USEFUL",
                    onClick = { onUseful(item) },
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                )
                AfterButton(
                    label = "ARCHIVE",
                    onClick = { onArchive(item) },
                    modifier = Modifier.weight(1f),
                    style = AfterButtonStyle.OUTLINE,
                )
            }
        }
    }
}

@Composable
private fun SecondaryCard(
    item: SavedItem,
    index: Int,
    now: Instant,
    onOpenDetail: () -> Unit,
    onOpenLink: () -> Unit,
    onSnooze: () -> Unit,
    onUseful: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(1.dp))
            .background(colors.surface),
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.size(84.dp)) {
                ItemArtwork(
                    thumbnailUrl = item.thumbnailUrl,
                    domain = item.domain,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = item.displayTitle,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onOpenDetail),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono(
                        "${index.toString().padStart(2, '0')} / ${item.domain.removePrefix("www.")}",
                        color = colors.textDim,
                        style = AfterType.MonoSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    StatusTag(item.status)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.displayTitle,
                    style = AfterType.DisplaySmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                )
                Spacer(Modifier.height(7.dp))
                Mono(
                    "SAVED ${TimeFormat.relativePast(item.savedAt, now)} · ${TimeFormat.dueLabel(item.nextReviewAt, now)}",
                    color = colors.textDim,
                    style = AfterType.MonoSmall,
                )
                if (!item.personalNote.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(2.dp).height(30.dp).background(colors.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.personalNote.orEmpty(),
                            style = AfterType.BodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        HairlineLight()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryAction("OPEN", onOpenLink, Modifier.weight(1f), accent = true)
            SecondaryAction("SNOOZE", onSnooze, Modifier.weight(1f))
            SecondaryAction("USEFUL", onUseful, Modifier.weight(1f))
            SecondaryAction("ARCHIVE", onArchive, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val colors = LocalAfterLaterColors.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = AfterType.MonoSmall,
            color = if (accent) colors.accent else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun HairlineLight() {
    val colors = LocalAfterLaterColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.7f)))
}

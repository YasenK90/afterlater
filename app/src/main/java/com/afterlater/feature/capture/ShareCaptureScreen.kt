package com.afterlater.feature.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.DomainLine
import com.afterlater.core.designsystem.DottedRule
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.IconAction
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.LocalReducedMotion
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.motionMillis
import com.afterlater.core.designsystem.rememberHaptics
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.util.TimeFormat
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun ShareCaptureScreen(
    viewModel: ShareViewModel,
    onDone: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenQuickAdd: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAfterLaterColors.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 380

    LaunchedEffect(state) {
        when (state) {
            is ShareCaptureUiState.Saved -> {
                haptics.confirm()
                delay(3_000)
                onDone()
            }

            is ShareCaptureUiState.Duplicate -> haptics.reject()
            is ShareCaptureUiState.Error -> haptics.reject()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.82f))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(bottom = 10.dp)
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colors.accent),
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("SHARE / CAPTURE", color = colors.accent, style = AfterType.MonoSmall)
                    Spacer(Modifier.weight(1f))
                    IconAction(
                        icon = Icons.Default.Close,
                        contentDescription = "Close capture",
                        onClick = onDone,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(22.dp))

                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(tween(motionMillis(180, reducedMotion))) togetherWith
                            fadeOut(tween(motionMillis(120, reducedMotion)))
                    },
                    label = "capture-state",
                ) { current ->
                    when (current) {
                        is ShareCaptureUiState.Waiting, is ShareCaptureUiState.Saving -> {
                            SavingContent(compact = compact)
                        }

                        is ShareCaptureUiState.Saved -> {
                            SavedContent(
                                heading = "SAVED TO AFTERLATER",
                                item = current.item,
                                compact = compact,
                                metadataPending = current.item.metadataStatus == MetadataStatus.PENDING,
                                primaryLabel = "DONE",
                                onPrimary = onDone,
                                secondaryLabel = "VIEW ITEM",
                                onSecondary = { onOpenItem(current.item.id) },
                            )
                        }

                        is ShareCaptureUiState.Duplicate -> {
                            SavedContent(
                                heading = "ALREADY IN YOUR ARCHIVE",
                                item = current.item,
                                compact = compact,
                                metadataPending = false,
                                primaryLabel = "OPEN EXISTING",
                                onPrimary = { onOpenItem(current.item.id) },
                                secondaryLabel = "DONE",
                                onSecondary = onDone,
                                accent = colors.textSecondary,
                            )
                        }

                        is ShareCaptureUiState.Error -> {
                            Column {
                                Text("NOT FILED.", AfterType.DisplayLarge, colors.textPrimary, maxLines = 2)
                                Spacer(Modifier.height(8.dp))
                                Mono(current.heading, color = colors.danger)
                                Spacer(Modifier.height(14.dp))
                                Text(current.message, AfterType.Body, colors.textSecondary)
                                Spacer(Modifier.height(22.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AfterButton("CLOSE", onDone, Modifier.weight(1f))
                                    AfterButton(
                                        label = "ADD MANUALLY",
                                        onClick = onOpenQuickAdd,
                                        modifier = Modifier.weight(1f),
                                        style = AfterButtonStyle.OUTLINE,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                DottedRule()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mono("LOCAL ONLY · NO ACCOUNT", color = colors.textDim, style = AfterType.MonoSmall)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(5.dp).background(colors.accent))
                }
            }
        }
    }
}

@Composable
private fun SavingContent(compact: Boolean) {
    val colors = LocalAfterLaterColors.current
    Column {
        Text(
            text = "FILING IT / AWAY.",
            style = if (compact) AfterType.DisplayMedium else AfterType.DisplayLarge,
            color = colors.textPrimary,
            maxLines = 2,
        )
        Spacer(Modifier.height(14.dp))
        Text("Writing the link to this device first. Previews come later.", AfterType.Body, colors.textSecondary)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        Mono("LOCAL WRITE IN PROGRESS", color = colors.textDim)
    }
}

@Composable
private fun SavedContent(
    heading: String,
    item: com.afterlater.core.model.SavedItem,
    compact: Boolean,
    metadataPending: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    accent: androidx.compose.ui.graphics.Color = LocalAfterLaterColors.current.accent,
) {
    val colors = LocalAfterLaterColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.accentInk,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Mono("LOCAL ARCHIVE / OK", color = accent)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = heading,
            style = if (compact) AfterType.DisplayMedium else AfterType.DisplayLarge,
            color = colors.textPrimary,
            maxLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        DomainLine(item.domain, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.displayTitle,
            style = AfterType.DisplaySmall,
            color = colors.textPrimary,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        Mono(
            text = "${TimeFormat.date(item.savedAt)} · ${TimeFormat.clock(item.savedAt)}",
            color = colors.textDim,
            style = AfterType.MonoSmall,
        )
        if (metadataPending && heading.startsWith("SAVED")) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Preview will be fetched in the background. You can close this now.",
                AfterType.BodySmall,
                colors.textDim,
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AfterButton(primaryLabel, onPrimary, Modifier.weight(1f))
            AfterButton(
                label = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
                style = AfterButtonStyle.OUTLINE,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextAction(
            label = if (heading.startsWith("SAVED")) "RETURNING AUTOMATICALLY" else "OPEN THE ARCHIVE",
            onClick = onPrimary,
            color = colors.textDim,
            icon = AfterIcons.ArrowUpRight,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.originalUrl,
            AfterType.MonoSmall,
            colors.textDim,
            maxLines = 1,
        )
    }
}

package com.afterlater.feature.capture

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterButtonStyle
import com.afterlater.core.designsystem.AfterIcons
import com.afterlater.core.designsystem.AfterTextField
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.DomainLine
import com.afterlater.core.designsystem.DottedRule
import com.afterlater.core.designsystem.Hairline
import com.afterlater.core.designsystem.IconAction
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.rememberHaptics
import com.afterlater.core.model.CaptureResult
import com.afterlater.core.model.MetadataStatus
import com.afterlater.core.util.TimeFormat
import com.afterlater.domain.url.LinkParser

@Composable
fun QuickAddScreen(
    viewModel: QuickAddViewModel,
    onBack: () -> Unit,
    onViewItem: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAfterLaterColors.current
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val compact = LocalConfiguration.current.screenWidthDp < 380
    val result = state.result

    ArchiveBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.width(8.dp))
                Mono("QUICK ADD / MANUAL CAPTURE", color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Mono("AFTERLATER", color = colors.textDim, style = AfterType.MonoSmall)
            }

            Spacer(Modifier.height(34.dp))

            when (result) {
                null -> {
                    Text(
                        text = "SOMETHING\nWORTH\nKEEPING?",
                        style = if (compact) AfterType.DisplayLarge else AfterType.DisplayHero,
                        color = colors.textPrimary,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Paste a link. It is written to this device before any network request happens.",
                        style = AfterType.BodyLarge,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(30.dp))

                    SectionMark("01", "THE LINK")
                    Spacer(Modifier.height(14.dp))

                    AfterTextField(
                        value = state.input,
                        onValueChange = viewModel::onInputChange,
                        placeholder = "https://…",
                        isError = state.error != null,
                        onDone = viewModel::save,
                        trailing = {
                            TextAction(
                                label = "PASTE",
                                onClick = {
                                    val text = clipboard.getText()?.text.orEmpty()
                                    val candidate = LinkParser.extractFirstUrl(text) ?: text.trim()
                                    if (candidate.isNotEmpty()) {
                                        viewModel.paste(candidate)
                                        haptics.tick()
                                    }
                                },
                                color = colors.accent,
                            )
                        },
                    )

                    if (state.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.error.orEmpty(),
                            style = AfterType.BodySmall,
                            color = colors.danger,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    AfterButton(
                        label = if (state.saving) "FILING…" else "SAVE TO AFTERLATER",
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.saving,
                        icon = AfterIcons.ArrowUpRight,
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Optional notes and categories stay out of the way. You can add them later.",
                        AfterType.BodySmall,
                        colors.textDim,
                    )

                    Spacer(Modifier.height(30.dp))
                    DottedRule()
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(colors.accent))
                        Spacer(Modifier.width(8.dp))
                        Mono("HTTP / HTTPS ONLY · NO ACCOUNT", color = colors.textDim, style = AfterType.MonoSmall)
                    }
                }

                is CaptureResult.Saved -> {
                    val item = result.item
                    SavedPanel(
                        eyebrow = "FILED / LOCAL",
                        heading = "SAVED.",
                        title = item.displayTitle,
                        domain = item.domain,
                        meta = "${TimeFormat.date(item.savedAt)} · ${TimeFormat.clock(item.savedAt)}",
                        pending = item.metadataStatus == MetadataStatus.PENDING,
                        primaryLabel = "VIEW ITEM",
                        onPrimary = { onViewItem(item.id) },
                        secondaryLabel = "SAVE ANOTHER",
                        onSecondary = viewModel::reset,
                        onDone = onBack,
                    )
                }

                is CaptureResult.Duplicate -> {
                    val item = result.existing
                    SavedPanel(
                        eyebrow = "ALREADY FILED",
                        heading = "THIS ONE\nIS ALREADY\nHERE.",
                        title = item.displayTitle,
                        domain = item.domain,
                        meta = "SAVED ${TimeFormat.relativePast(item.savedAt, java.time.Instant.now())}",
                        pending = false,
                        primaryLabel = "OPEN EXISTING",
                        onPrimary = { onViewItem(item.id) },
                        secondaryLabel = "SAVE ANOTHER",
                        onSecondary = viewModel::reset,
                        onDone = onBack,
                    )
                }

                is CaptureResult.Invalid, is CaptureResult.Failed -> {
                    val message = when (result) {
                        is CaptureResult.Invalid -> result.reason
                        is CaptureResult.Failed -> result.reason
                        else -> ""
                    }
                    ErrorCard(message = message, onDismiss = viewModel::dismissResult, expanded = true)
                    Spacer(Modifier.height(16.dp))
                    AfterButton("BACK TO THE LINK", viewModel::dismissResult, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SavedPanel(
    eyebrow: String,
    heading: String,
    title: String,
    domain: String,
    meta: String,
    pending: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    Column {
        Box(
            Modifier
                .size(42.dp)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, null, tint = colors.accentInk, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(18.dp))
        Mono(eyebrow, color = colors.accent)
        Spacer(Modifier.height(12.dp))
        Text(heading, AfterType.DisplayHero, colors.textPrimary, maxLines = 3)
        Spacer(Modifier.height(26.dp))
        Hairline()
        Spacer(Modifier.height(18.dp))
        DomainLine(domain)
        Spacer(Modifier.height(8.dp))
        Text(title, AfterType.DisplaySmall, colors.textPrimary, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        Mono(meta, color = colors.textDim, style = AfterType.MonoSmall)
        if (pending) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Preview will resolve in the background. Your saved link is already safe.",
                AfterType.BodySmall,
                colors.textDim,
            )
        }
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AfterButton(primaryLabel, onPrimary, Modifier.weight(1f))
            AfterButton(
                label = secondaryLabel,
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
                style = AfterButtonStyle.OUTLINE,
            )
        }
        Spacer(Modifier.height(10.dp))
        TextAction("DONE", onDone, color = colors.textDim, icon = AfterIcons.ArrowUpRight)
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, expanded: Boolean = false) {
    val colors = LocalAfterLaterColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.danger.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Mono("NOT FILED", color = colors.danger)
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text("THAT LINK COULD NOT BE SAVED.", AfterType.DisplaySmall, colors.textPrimary, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        Text(message, AfterType.BodySmall, colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        TextAction("DISMISS", onDismiss, color = colors.textDim)
    }
}

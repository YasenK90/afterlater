package com.afterlater.feature.welcome

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.afterlater.auto.AutoDetectPermissions
import com.afterlater.core.designsystem.AfterButton
import com.afterlater.core.designsystem.AfterType
import com.afterlater.core.designsystem.ArchiveBackground
import com.afterlater.core.designsystem.LocalAfterLaterColors
import com.afterlater.core.designsystem.Mono
import com.afterlater.core.designsystem.SectionMark
import com.afterlater.core.designsystem.Text
import com.afterlater.core.designsystem.TextAction
import com.afterlater.core.designsystem.Wordmark

/**
 * One-time first-run screen: the concept in three lines, plus the only
 * genuinely fiddly setup (copy detection needs an accessibility service and
 * the overlay permission). Everything here is optional — Get Started works
 * with all of it off.
 */
@Composable
fun WelcomeScreen(
    clipboardCaptureEnabled: Boolean,
    onSetClipboardCapture: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val context = LocalContext.current
    var accessibilityEnabled by remember {
        mutableStateOf(AutoDetectPermissions.isAccessibilityServiceEnabled(context))
    }
    var overlayAllowed by remember {
        mutableStateOf(AutoDetectPermissions.canDrawOverlays(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessibilityEnabled = AutoDetectPermissions.isAccessibilityServiceEnabled(context)
        overlayAllowed = AutoDetectPermissions.canDrawOverlays(context)
    }

    ArchiveBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                Mono("LOCAL ONLY", color = colors.accent, style = AfterType.MonoSmall)
            }

            Spacer(Modifier.height(38.dp))
            Text(
                text = "SAVE FIRST.\nREAD LATER.",
                style = AfterType.DisplayHero,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(72.dp).height(3.dp).background(colors.accent))
            Spacer(Modifier.height(20.dp))
            Text(
                text = "A private shelf for the internet. Everything stays on this device — no account, no cloud, no sync.",
                style = AfterType.Body,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(30.dp))
            SectionMark(index = "01", title = "SAVE")
            Spacer(Modifier.height(8.dp))
            Text(
                "Copy a link in any app and a card offers to keep it, or share a link to AFTERLATER, or paste one by hand.",
                AfterType.BodySmall,
                colors.textSecondary,
            )

            Spacer(Modifier.height(18.dp))
            SectionMark(index = "02", title = "REDISCOVER")
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved links settle for a day. Each morning, a small batch of what is due — open it, snooze it, keep it, or let it go.",
                AfterType.BodySmall,
                colors.textSecondary,
            )

            Spacer(Modifier.height(18.dp))
            SectionMark(index = "03", title = "OPTIONAL EXTRAS")
            Spacer(Modifier.height(8.dp))
            Text(
                "Copy detection and daily reminders are off by default. Copy detection needs the two permissions below; everything else lives in Settings.",
                AfterType.BodySmall,
                colors.textSecondary,
            )

            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(1.dp))
                    .background(colors.surface)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = clipboardCaptureEnabled,
                            role = Role.Switch,
                            onValueChange = onSetClipboardCapture,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Mono("COPY DETECTION", color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (clipboardCaptureEnabled) {
                                "A save card slides in when you copy a link."
                            } else {
                                "Off. Copying links does nothing until you turn this on."
                            },
                            AfterType.BodySmall,
                            colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = clipboardCaptureEnabled,
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
                if (clipboardCaptureEnabled) {
                    Spacer(Modifier.height(14.dp))
                    WelcomePermissionRow(
                        label = "ACCESSIBILITY SERVICE",
                        granted = accessibilityEnabled,
                        actionLabel = if (accessibilityEnabled) "Open settings" else "Enable",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    WelcomePermissionRow(
                        label = "DISPLAY OVER OTHER APPS",
                        granted = overlayAllowed,
                        actionLabel = if (overlayAllowed) "Open settings" else "Allow",
                        onAction = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                    if (!accessibilityEnabled || !overlayAllowed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Both permissions are needed before the card can appear. AFTERLATER reads nothing until they are on.",
                            AfterType.BodySmall,
                            colors.danger,
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
            AfterButton(
                label = "GET STARTED",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Mono(
                "EVERY CHOICE HERE CAN BE CHANGED IN SETTINGS LATER.",
                color = colors.textDim,
                style = AfterType.MonoSmall,
            )
        }
    }
}

@Composable
private fun WelcomePermissionRow(
    label: String,
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
                if (granted) "ENABLED" else "NOT ENABLED",
                AfterType.MonoSmall,
                if (granted) colors.accent else colors.danger,
            )
        }
        TextAction(label = actionLabel, onClick = onAction)
    }
}

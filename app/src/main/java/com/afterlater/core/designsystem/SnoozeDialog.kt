package com.afterlater.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afterlater.domain.review.SnoozeWindow

@Composable
fun SnoozeDialog(
    onDismiss: () -> Unit,
    onSelect: (SnoozeWindow) -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 0.dp,
        title = {
            Text("SNOOZE UNTIL", AfterType.DisplaySmall, colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "IT LEAVES TODAY AND RETURNS ON SCHEDULE.",
                    AfterType.Body,
                    colors.textSecondary,
                )
                Spacer(Modifier.height(2.dp))
                AfterButton(
                    label = "Tomorrow",
                    onClick = { onSelect(SnoozeWindow.TOMORROW) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                )
                AfterButton(
                    label = "Next week",
                    onClick = { onSelect(SnoozeWindow.NEXT_WEEK) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextAction(
                label = "Cancel",
                onClick = onDismiss,
                color = colors.textSecondary,
            )
        },
    )
}

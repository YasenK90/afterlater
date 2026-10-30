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
import com.afterlater.domain.review.ExpiryChoice

@Composable
fun DeadlineDialog(
    onDismiss: () -> Unit,
    onSelect: (ExpiryChoice) -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(2.dp),
        tonalElevation = 0.dp,
        title = {
            Text("DEADLINE", AfterType.DisplaySmall, colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "IT MOVES TO THE NEXT BATCH AND STOPS BEING OFFERED ONCE THE DEADLINE PASSES.",
                    AfterType.Body,
                    colors.textSecondary,
                )
                Spacer(Modifier.height(2.dp))
                AfterButton(
                    label = "Tonight",
                    onClick = { onSelect(ExpiryChoice.TODAY) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                )
                AfterButton(
                    label = "In 7 days",
                    onClick = { onSelect(ExpiryChoice.THIS_WEEK) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
                    icon = AfterIcons.Clock,
                )
                AfterButton(
                    label = "No deadline",
                    onClick = { onSelect(ExpiryChoice.NONE) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AfterButtonStyle.OUTLINE,
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

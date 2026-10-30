package com.afterlater.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afterlater.domain.review.ReviewScheduler
import com.afterlater.domain.review.SnoozeWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeDialog(
    onDismiss: () -> Unit,
    onSelect: (SnoozeWindow) -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    var showDatePicker by remember { mutableStateOf(false) }

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
                AfterButton(
                    label = "Pick a date",
                    onClick = { showDatePicker = true },
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

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now(zone).plusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            containerColor = colors.surface,
            shape = RoundedCornerShape(2.dp),
            tonalElevation = 0.dp,
            title = {
                Text("RETURN ON…", AfterType.DisplaySmall, colors.textPrimary)
            },
            text = {
                DatePicker(state = picker)
            },
            confirmButton = {
                TextAction(
                    label = "Set date",
                    onClick = {
                        val millis = picker.selectedDateMillis
                        if (millis != null) {
                            val target = ReviewScheduler.snoozeUntilDate(
                                now = Instant.now(),
                                zone = zone,
                                targetDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(),
                            )
                            showDatePicker = false
                            onSelect(SnoozeWindow(Duration.between(Instant.now(), target)))
                        }
                    },
                )
            },
            dismissButton = {
                TextAction(
                    label = "Cancel",
                    onClick = { showDatePicker = false },
                    color = colors.textSecondary,
                )
            },
        )
    }
}

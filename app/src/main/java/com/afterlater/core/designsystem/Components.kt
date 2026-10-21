package com.afterlater.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.afterlater.core.model.ItemStatus
import java.util.Locale

@Composable
fun ArchiveBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalAfterLaterColors.current
    val density = LocalDensity.current
    val step = with(density) { 34.dp.toPx() }
    val line = colors.border.copy(alpha = 0.22f)
    val mark = colors.accent.copy(alpha = 0.5f)
    val cross = with(density) { 10.dp.toPx() }
    val margin = with(density) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                var x = step
                while (x < size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                drawLine(mark, Offset(margin, margin), Offset(margin + cross, margin), strokeWidth = 1.5f)
                drawLine(mark, Offset(margin, margin), Offset(margin, margin + cross), strokeWidth = 1.5f)
            },
        content = content,
    )
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(5.dp)
                .height(31.dp)
                .background(colors.accent),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Row {
                Text(
                    text = "AFTER",
                    style = AfterType.Wordmark,
                    color = colors.textPrimary,
                )
                Text(
                    text = "LATER",
                    style = AfterType.Wordmark,
                    color = colors.accent,
                )
            }
            Text(
                text = "PERSONAL INTERNET ARCHIVE",
                style = AfterType.MonoSmall,
                color = colors.textDim,
                letterSpacing = 1.35.sp,
            )
        }
    }
}

@Composable
fun Text(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = LocalAfterLaterColors.current.textPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: androidx.compose.ui.unit.TextUnit = style.letterSpacing,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(letterSpacing = letterSpacing),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAfterLaterColors.current.textSecondary,
    style: androidx.compose.ui.text.TextStyle = AfterType.Mono,
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(Locale.US),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = LocalAfterLaterColors.current.border) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
fun DottedRule(modifier: Modifier = Modifier) {
    val color = LocalAfterLaterColors.current.border
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f),
        )
    }
}

@Composable
fun SectionMark(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = LocalAfterLaterColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index, AfterType.MonoMedium, colors.accent)
        Spacer(Modifier.width(10.dp))
        Text(title.uppercase(Locale.US), AfterType.MonoSmall, colors.textSecondary, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border),
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing.uppercase(Locale.US), AfterType.MonoSmall, colors.textDim, maxLines = 1)
        }
    }
}

enum class AfterButtonStyle { PRIMARY, OUTLINE, QUIET, DANGER }

@Composable
fun AfterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AfterButtonStyle = AfterButtonStyle.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = LocalAfterLaterColors.current
    val background = when (style) {
        AfterButtonStyle.PRIMARY -> colors.accent
        AfterButtonStyle.OUTLINE, AfterButtonStyle.QUIET -> Color.Transparent
        AfterButtonStyle.DANGER -> Color.Transparent
    }
    val contentColor = when (style) {
        AfterButtonStyle.PRIMARY -> colors.accentInk
        AfterButtonStyle.OUTLINE -> colors.textPrimary
        AfterButtonStyle.QUIET -> colors.textSecondary
        AfterButtonStyle.DANGER -> colors.danger
    }
    val borderColor = when (style) {
        AfterButtonStyle.PRIMARY -> colors.accent
        AfterButtonStyle.OUTLINE -> colors.borderStrong
        AfterButtonStyle.QUIET -> Color.Transparent
        AfterButtonStyle.DANGER -> colors.danger.copy(alpha = 0.7f)
    }
    val alpha = if (enabled) 1f else 0.35f
    val shape = RoundedCornerShape(2.dp)

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background.copy(alpha = background.alpha * alpha))
            .border(1.dp, borderColor.copy(alpha = borderColor.alpha * alpha), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (style == AfterButtonStyle.QUIET) 4.dp else 14.dp, vertical = 13.dp)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = AfterType.Button,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1,
        )
        if (icon != null) {
            Spacer(Modifier.width(9.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAfterLaterColors.current.accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(Locale.US), AfterType.Button, color.copy(alpha = if (enabled) 1f else 0.35f), maxLines = 1)
        if (icon != null) {
            Spacer(Modifier.width(7.dp))
            Icon(icon, null, tint = color.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalAfterLaterColors.current.textSecondary,
    bordered: Boolean = false,
) {
    val shape = RoundedCornerShape(1.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .then(
                if (bordered) {
                    Modifier
                        .border(1.dp, LocalAfterLaterColors.current.border, shape)
                        .background(LocalAfterLaterColors.current.surface)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button; this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}


@Composable
fun StatusTag(status: ItemStatus, modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    val (label, tint) = when (status) {
        ItemStatus.NEW -> "NEW" to colors.accent
        ItemStatus.READY -> "READY" to colors.textPrimary
        ItemStatus.SNOOZED -> "SNOOZED" to colors.textSecondary
        ItemStatus.USEFUL -> "USEFUL" to colors.accent
        ItemStatus.ARCHIVED -> "ARCHIVED" to colors.textDim
    }
    Row(
        modifier = modifier
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(label, AfterType.MonoSmall, tint, maxLines = 1)
    }
}

@Composable
fun DomainLine(domain: String, modifier: Modifier = Modifier, color: Color = LocalAfterLaterColors.current.textSecondary) {
    val cleaned = domain.removePrefix("www.").uppercase(Locale.US)
    Text(
        text = cleaned,
        style = AfterType.MonoSmall,
        color = color,
        modifier = modifier,
        letterSpacing = 1.2.sp,
        maxLines = 1,
    )
}

@Composable
fun ItemArtwork(
    thumbnailUrl: String?,
    domain: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    Box(modifier = modifier.background(LocalAfterLaterColors.current.surface)) {
        if (thumbnailUrl.isNullOrBlank()) {
            ArtworkFallback(domain = domain, modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(180)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ArtworkFallback(domain = domain, modifier = Modifier.fillMaxSize()) },
                error = { ArtworkFallback(domain = domain, modifier = Modifier.fillMaxSize()) },
                success = { SubcomposeAsyncImageContent() },
            )
        }
    }
}

@Composable
fun ArtworkFallback(domain: String, modifier: Modifier = Modifier) {
    val colors = LocalAfterLaterColors.current
    val initial = domain.removePrefix("www.").firstOrNull()?.uppercase(Locale.US) ?: "?"
    val dense = MonoFamily
    Box(modifier.background(colors.surface)) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 21.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = colors.border.copy(alpha = 0.6f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = 1f,
                )
                x += gap
            }
            drawLine(
                color = colors.accent.copy(alpha = 0.4f),
                start = Offset(0f, size.height * 0.78f),
                end = Offset(size.width * 0.32f, size.height * 0.78f),
                strokeWidth = 2.dp.toPx(),
            )
        }
        androidx.compose.material3.Text(
            text = initial,
            style = AfterType.DisplayHero.copy(fontFamily = dense, fontWeight = FontWeight.Black),
            color = colors.textPrimary.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 2.dp),
        )
        Text(
            text = domain.removePrefix("www.").uppercase(Locale.US),
            style = AfterType.MonoSmall,
            color = colors.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            maxLines = 1,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(8.dp)
                .background(colors.accent),
        )
    }
}

@Composable
fun BottomScrim(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val colors = LocalAfterLaterColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, colors.background.copy(alpha = 0.96f)),
                ),
            ),
    )
}

@Composable
fun AfterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    isError: Boolean = false,
    singleLine: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAfterLaterColors.current
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> colors.danger
        focused -> colors.accent
        else -> colors.border
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        textStyle = AfterType.BodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, AfterType.BodyLarge, colors.textDim, maxLines = 1)
                    }
                    innerTextField()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
        },
    )
}

@Composable
fun ChoiceCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAfterLaterColors.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) colors.accent else Color.Transparent)
            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(1.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(Locale.US),
            AfterType.MonoSmall,
            if (selected) colors.accentInk else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAfterLaterColors.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(if (selected) colors.textPrimary else Color.Transparent)
            .border(1.dp, if (selected) colors.textPrimary else colors.border, RoundedCornerShape(1.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label.uppercase(Locale.US),
            AfterType.MonoSmall,
            if (selected) colors.background else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAfterLaterColors.current.textPrimary,
    valueStyle: androidx.compose.ui.text.TextStyle = AfterType.Body,
) {
    val colors = LocalAfterLaterColors.current
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(
            text = label.uppercase(Locale.US),
            style = AfterType.MonoSmall,
            color = colors.textDim,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = valueStyle,
            color = valueColor,
            modifier = Modifier.weight(1f),
            maxLines = 4,
        )
    }
}

@Composable
fun Selector(
    caption: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String = value,
) {
    val colors = LocalAfterLaterColors.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .border(1.dp, colors.border, RoundedCornerShape(1.dp))
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(caption.uppercase(Locale.US), AfterType.MonoSmall, colors.textDim, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(value.uppercase(Locale.US), AfterType.Mono, colors.textPrimary, maxLines = 1)
            }
            Icon(
                imageVector = AfterIcons.ChevronDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.surface)
                .border(1.dp, colors.border),
        ) {
            options.forEach { (id, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            label.uppercase(Locale.US),
                            AfterType.MonoSmall,
                            if (id == selectedId) colors.accent else colors.textSecondary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

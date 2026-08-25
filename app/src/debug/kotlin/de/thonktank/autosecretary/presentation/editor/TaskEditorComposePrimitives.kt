package de.thonktank.autosecretary.presentation.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R

internal val EditorSerif = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)
internal val EditorSans = FontFamily(
    Font(R.font.alegreya_sans, FontWeight.Normal),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

@Immutable
internal data class EditorLayout(
    val compact: Boolean,
    val pageStart: Dp,
    val pageEnd: Dp,
    val leafHorizontal: Dp,
    val leafTop: Dp,
    val leafBottom: Dp,
    val footerHeight: Dp,
    val weekdayColumns: Int,
) {
    companion object {
        fun from(widthDp: Int, fontScale: Float): EditorLayout {
            val compact = widthDp < 360 || fontScale >= 1.3f
            return EditorLayout(
                compact = compact,
                pageStart = if (compact) 18.dp else 60.dp,
                pageEnd = if (compact) 18.dp else 22.dp,
                leafHorizontal = if (compact) 18.dp else 26.dp,
                leafTop = if (compact) 20.dp else 26.dp,
                leafBottom = if (compact) 22.dp else 30.dp,
                footerHeight = if (compact) 112.dp else 80.dp,
                weekdayColumns = if (compact) 4 else 7,
            )
        }
    }
}

internal fun Color.Companion.argb(value: Int): Color = Color(value)

internal fun leafShape(topStart: Int, topEnd: Int, bottomEnd: Int, bottomStart: Int): Shape =
    RoundedCornerShape(
        topStart = topStart.dp,
        topEnd = topEnd.dp,
        bottomEnd = bottomEnd.dp,
        bottomStart = bottomStart.dp,
    )

@Composable
internal fun EditorText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    serif: Boolean = true,
    italic: Boolean = false,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (serif) EditorSerif else EditorSans,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (size >= 30) (-0.6).sp else 0.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Question(@StringRes text: Int, palette: DayPalette, modifier: Modifier = Modifier) {
    EditorText(stringResource(text), Color.argb(palette.ink), 30, modifier)
}

@Composable
internal fun Label(@StringRes text: Int, palette: DayPalette, modifier: Modifier = Modifier) {
    EditorText(
        stringResource(text),
        Color.argb(palette.muted),
        17,
        modifier,
        italic = true,
    )
}

@Composable
internal fun EditorButton(
    text: String,
    palette: DayPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    primary: Boolean = false,
    contentDescription: String = text,
) {
    val background = if (primary) {
        if (destructive) Color.argb(palette.bad) else Color.argb(palette.accent)
    } else Color.Transparent
    val foreground = if (primary) Color.argb(palette.accentText) else {
        if (destructive) Color.argb(palette.bad) else Color.argb(palette.ink2)
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (primary) Modifier.shadow(5.dp, shape) else Modifier)
            .clip(shape)
            .background(background)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .padding(horizontal = if (primary) 28.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        EditorText(
            text,
            foreground.copy(alpha = if (enabled) 1f else .48f),
            17,
            serif = false,
            bold = primary,
        )
    }
}

@Composable
internal fun EditorChip(
    @StringRes label: Int,
    selected: Boolean,
    palette: DayPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val text = stringResource(label)
    val accessibilityLabel = stringResource(
        if (selected) R.string.a11y_editor_chip_selected else R.string.a11y_editor_chip,
        text,
    )
    val shape = RoundedCornerShape(24.dp)
    val background = if (selected) Color.argb(palette.accent) else Color.Transparent
    val foreground = if (selected) Color.argb(palette.accentText) else Color.argb(palette.ink2)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) Color.argb(palette.accent) else Color.argb(palette.dot), shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = accessibilityLabel
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        EditorText(text, foreground, 15, serif = false, bold = selected)
    }
}

@Composable
internal fun ChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
internal fun EditorInput(
    value: String,
    onValueChange: (String) -> Unit,
    palette: DayPalette,
    modifier: Modifier = Modifier,
    hint: String = "",
    multiline: Boolean = false,
    error: Boolean = false,
    number: Boolean = false,
    textSize: Int = if (number) 23 else 17,
    serif: Boolean = number,
    focusRequester: FocusRequester? = null,
    tag: String? = null,
) {
    val requestModifier = if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)
    val underline = if (error) Color.argb(palette.bad) else Color.argb(palette.accent)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(requestModifier)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .defaultMinSize(minHeight = if (multiline) 56.dp else 48.dp)
            .drawBehind {
                drawLine(
                    color = underline,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = if (error) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            .padding(top = 2.dp, bottom = 6.dp),
        textStyle = TextStyle(
            color = Color.argb(palette.ink),
            fontSize = textSize.sp,
            fontFamily = if (serif) EditorSerif else EditorSans,
        ),
        singleLine = !multiline,
        keyboardOptions = if (number) KeyboardOptions.Default.copy(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ) else KeyboardOptions.Default,
        cursorBrush = SolidColor(Color.argb(palette.accent)),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    EditorText(hint, Color.argb(palette.hint), textSize, serif = serif)
                }
                field()
            }
        },
    )
}

@Composable
internal fun NumberInput(
    value: Int?,
    @StringRes unit: Int,
    palette: DayPalette,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    focusRequester: FocusRequester? = null,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        EditorInput(
            value = value?.toString().orEmpty(),
            onValueChange = { onValueChange(it.filter(Char::isDigit).toIntOrNull()) },
            palette = palette,
            modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp),
            number = true,
            tag = tag,
            focusRequester = focusRequester,
        )
        EditorText(
            stringResource(unit),
            Color.argb(palette.hint),
            14,
            serif = false,
        )
    }
}

@Composable
internal fun ErrorText(
    @StringRes text: Int,
    palette: DayPalette,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    EditorText(
        stringResource(text),
        Color.argb(palette.bad),
        14,
        modifier
            .padding(top = 7.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.argb(palette.bad).copy(alpha = .10f))
            .border(1.dp, Color.argb(palette.bad).copy(alpha = .34f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        italic = true,
    )
}

@Composable
internal fun LeafSurface(
    palette: DayPalette,
    modifier: Modifier = Modifier,
    level: Int = 1,
    rotation: Float = 0f,
    topStart: Int = 10,
    topEnd: Int = 64,
    bottomEnd: Int = 10,
    bottomStart: Int = 64,
    clickableLabel: String? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    fillOverride: Color? = null,
    edgeOverride: Color? = null,
    strokeWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = when (level) {
        1 -> palette.leaf1
        2 -> palette.leaf2
        else -> palette.leaf3
    }
    val edge = when (level) {
        1 -> palette.leaf1Edge
        2 -> palette.leaf2Edge
        else -> palette.leaf3Edge
    }
    val shape = leafShape(topStart, topEnd, bottomEnd, bottomStart)
    val clickable = if (onClick == null) Modifier else Modifier
        .clickable(role = Role.Button, onClick = onClick)
        .semantics {
            role = Role.Button
            if (clickableLabel != null) contentDescription = clickableLabel
        }
    Box(
        modifier = modifier
            .rotate(rotation)
            .shadow(if (level == 1) 14.dp else 7.dp, shape, clip = false)
            .clip(shape)
            .background(fillOverride ?: Color.argb(fill))
            .border(BorderStroke(strokeWidth, edgeOverride ?: Color.argb(edge)), shape)
            .then(clickable)
            .padding(padding),
        content = content,
    )
}

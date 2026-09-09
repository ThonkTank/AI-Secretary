package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.presentation.mobile.MobileActionButton
import de.thonktank.autosecretary.presentation.mobile.MobileActionStyle
import de.thonktank.autosecretary.presentation.mobile.MobileSans
import de.thonktank.autosecretary.presentation.mobile.MobileText
import de.thonktank.autosecretary.presentation.mobile.mobileColor
import de.thonktank.autosecretary.presentation.mobile.mobileLeaf
import de.thonktank.autosecretary.presentation.mobile.mobileLeafShape

internal fun color(value: Int): Color = mobileColor(value)

internal fun leafShape(
    topStart: Dp = 42.dp,
    topEnd: Dp = 8.dp,
    bottomEnd: Dp = 42.dp,
    bottomStart: Dp = 8.dp,
): Shape = mobileLeafShape(topStart, topEnd, bottomEnd, bottomStart)

@Composable
internal fun AllTasksText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
    italic: Boolean = false,
    bold: Boolean = false,
    underline: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    MobileText(
        text = text,
        color = color,
        size = size,
        modifier = modifier,
        serif = serif,
        italic = italic,
        bold = bold,
        underline = underline,
        maxLines = maxLines,
        serifHeadlineFrom = 30,
        serifHeadlineLetterSpacing = -.6f,
    )
}

@Composable
internal fun AllTasksActionText(
    text: String,
    palette: DayPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    underline: Boolean = false,
    minHeight: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(minHeight / 2)
    val background = if (selected) color(palette.accent) else Color.Transparent
    val foreground = if (selected) color(palette.accentText) else color(palette.ink2)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minHeight, minHeight = minHeight)
            .background(background, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AllTasksText(
            text,
            foreground,
            14,
            bold = selected,
            underline = underline,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AllTasksChoiceChip(
    label: String,
    palette: DayPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MobileActionButton(
        label = label,
        palette = palette,
        onClick = onClick,
        modifier = modifier,
        style = if (selected) MobileActionStyle.PRIMARY else MobileActionStyle.SECONDARY,
        horizontalPadding = 15.dp,
        bold = selected,
    )
}

@Composable
internal fun AllTasksControlButton(
    label: String,
    palette: DayPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    MobileActionButton(
        label = label,
        palette = palette,
        onClick = onClick,
        modifier = modifier,
        style = if (prominent) MobileActionStyle.PRIMARY else MobileActionStyle.SECONDARY,
        minHeight = 48.dp,
        cornerRadius = 16.dp,
        horizontalPadding = 12.dp,
        fontSize = 16,
        bold = prominent,
    )
}

@Composable
internal fun AllTasksSearch(
    query: String,
    palette: DayPalette,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(25.dp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .background(color(palette.leaf1).copy(alpha = .86f), shape)
            .border(1.dp, color(palette.leaf1Edge), shape)
            .padding(horizontal = 18.dp),
        singleLine = true,
        textStyle = TextStyle(
            color = color(palette.ink),
            fontSize = 17.sp,
            fontFamily = MobileSans,
        ),
        cursorBrush = SolidColor(color(palette.accent)),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    AllTasksText(
                        stringResource(R.string.all_search_hint),
                        color(palette.hint),
                        17,
                    )
                }
                field()
            }
        },
    )
}

internal fun Modifier.leaf(
    palette: DayPalette,
    shape: Shape = leafShape(),
    level: Int = 2,
): Modifier {
    return mobileLeaf(palette, shape, level)
}

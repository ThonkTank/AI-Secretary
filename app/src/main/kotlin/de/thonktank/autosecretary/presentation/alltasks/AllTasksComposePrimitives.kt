package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R

internal val AllTasksSerif = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)
internal val AllTasksSans = FontFamily(
    Font(R.font.alegreya_sans, FontWeight.Normal),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

internal fun color(value: Int): Color = Color(value)

internal fun leafShape(
    topStart: Dp = 42.dp,
    topEnd: Dp = 8.dp,
    bottomEnd: Dp = 42.dp,
    bottomStart: Dp = 8.dp,
): Shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

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
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (serif) AllTasksSerif else AllTasksSans,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
            letterSpacing = if (serif && size >= 30) (-.6).sp else 0.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
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
internal fun AllTasksChip(
    label: String,
    palette: DayPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val background = if (selected) color(palette.accent) else Color.Transparent
    val edge = if (selected) color(palette.accent) else color(palette.dot)
    val foreground = if (selected) color(palette.accentText) else color(palette.ink2)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(background, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AllTasksText("$label ⌄", foreground, 14, bold = selected, maxLines = 1)
    }
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
            fontFamily = AllTasksSans,
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
    val fill = if (level == 1) palette.leaf1 else if (level == 2) palette.leaf2 else palette.leaf3
    val edge = if (level == 1) palette.leaf1Edge else if (level == 2) palette.leaf2Edge
    else palette.leaf3Edge
    return background(color(fill), shape)
        .border(1.dp, color(edge), shape)
}

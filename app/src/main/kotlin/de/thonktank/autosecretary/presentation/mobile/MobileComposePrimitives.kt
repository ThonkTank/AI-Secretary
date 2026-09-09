package de.thonktank.autosecretary.presentation.mobile

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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

internal val MobileSerif = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)

internal val MobileSans = FontFamily(
    Font(R.font.alegreya_sans, FontWeight.Normal),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

internal fun mobileColor(value: Int): Color = Color(value)

internal fun mobileLeafShape(
    topStart: Dp = 42.dp,
    topEnd: Dp = 8.dp,
    bottomEnd: Dp = 42.dp,
    bottomStart: Dp = 8.dp,
): Shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

@Composable
internal fun MobileText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
    italic: Boolean = false,
    bold: Boolean = false,
    underline: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    serifHeadlineFrom: Int = 28,
    serifHeadlineLetterSpacing: Float = -.5f,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (serif) MobileSerif else MobileSans,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
            letterSpacing = if (serif && size >= serifHeadlineFrom) {
                serifHeadlineLetterSpacing.sp
            } else {
                0.sp
            },
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

internal enum class MobileActionStyle {
    SECONDARY,
    PRIMARY,
    DESTRUCTIVE,
}

@Composable
internal fun MobileActionButton(
    label: String,
    palette: DayPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MobileActionStyle = MobileActionStyle.SECONDARY,
    enabled: Boolean = true,
    minHeight: Dp = 44.dp,
    cornerRadius: Dp = 22.dp,
    horizontalPadding: Dp = 14.dp,
    fontSize: Int = 15,
    bold: Boolean = style == MobileActionStyle.PRIMARY,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fill = if (style == MobileActionStyle.PRIMARY) {
        mobileColor(palette.accent)
    } else {
        mobileColor(palette.leaf1)
    }
    val edge = when (style) {
        MobileActionStyle.DESTRUCTIVE -> mobileColor(palette.bad)
        MobileActionStyle.PRIMARY -> mobileColor(palette.accent)
        MobileActionStyle.SECONDARY -> mobileColor(palette.leaf1Edge)
    }
    val foreground = when (style) {
        MobileActionStyle.DESTRUCTIVE -> mobileColor(palette.bad)
        MobileActionStyle.PRIMARY -> mobileColor(palette.accentText)
        MobileActionStyle.SECONDARY -> mobileColor(palette.ink2)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .background(fill, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .then(if (enabled) Modifier else Modifier.alpha(.5f))
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        MobileText(label, foreground, fontSize, bold = bold, maxLines = 1)
    }
}

internal fun Modifier.mobileLeaf(
    palette: DayPalette,
    shape: Shape = mobileLeafShape(),
    level: Int = 2,
    edgeColor: Int? = null,
): Modifier {
    val fill = when (level) {
        1 -> palette.leaf1
        2 -> palette.leaf2
        else -> palette.leaf3
    }
    val edge = edgeColor ?: when (level) {
        1 -> palette.leaf1Edge
        2 -> palette.leaf2Edge
        else -> palette.leaf3Edge
    }
    return background(mobileColor(fill), shape)
        .border(1.dp, mobileColor(edge), shape)
}

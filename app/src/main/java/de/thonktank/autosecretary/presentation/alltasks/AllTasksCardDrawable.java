package de.thonktank.autosecretary.presentation.alltasks;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.UiStyle;

/** Draws one continuous leaf edge across independently virtualized card rows. */
final class AllTasksCardDrawable extends Drawable {
    enum Segment { TOP, MIDDLE, BOTTOM }

    private final UiStyle style;
    private final Segment segment;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    AllTasksCardDrawable(UiStyle style, DayPalette palette, Segment segment) {
        this.style = style;
        this.segment = segment;
        fill.setColor(palette.leaf2);
        fill.setStyle(Paint.Style.FILL);
        edge.setColor(palette.leaf2Edge);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, style.dp(1)));
    }

    @Override public void draw(@NonNull Canvas canvas) {
        float half = edge.getStrokeWidth() / 2f;
        RectF bounds = new RectF(getBounds().left + half, getBounds().top,
                getBounds().right - half, getBounds().bottom);
        Path shape = new Path();
        float topLeft = segment == Segment.TOP ? style.dp(42) : 0;
        float topRight = segment == Segment.TOP ? style.dp(8) : 0;
        float bottomRight = segment == Segment.BOTTOM ? style.dp(42) : 0;
        float bottomLeft = segment == Segment.BOTTOM ? style.dp(8) : 0;
        shape.addRoundRect(bounds, new float[]{topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft}, Path.Direction.CW);
        canvas.drawPath(shape, fill);

        Path border = new Path();
        if (segment == Segment.TOP) {
            border.moveTo(bounds.left, bounds.bottom);
            border.lineTo(bounds.left, bounds.top + topLeft);
            border.quadTo(bounds.left, bounds.top, bounds.left + topLeft, bounds.top + half);
            border.lineTo(bounds.right - topRight, bounds.top + half);
            border.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + topRight);
            border.lineTo(bounds.right, bounds.bottom);
        } else if (segment == Segment.MIDDLE) {
            border.moveTo(bounds.left, bounds.top);
            border.lineTo(bounds.left, bounds.bottom);
            border.moveTo(bounds.right, bounds.top);
            border.lineTo(bounds.right, bounds.bottom);
        } else {
            border.moveTo(bounds.left, bounds.top);
            border.lineTo(bounds.left, bounds.bottom - bottomLeft);
            border.quadTo(bounds.left, bounds.bottom,
                    bounds.left + bottomLeft, bounds.bottom - half);
            border.lineTo(bounds.right - bottomRight, bounds.bottom - half);
            border.quadTo(bounds.right, bounds.bottom,
                    bounds.right, bounds.bottom - bottomRight);
            border.lineTo(bounds.right, bounds.top);
        }
        canvas.drawPath(border, edge);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); edge.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        edge.setColorFilter(filter);
    }
    @Override @SuppressWarnings("deprecation")
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

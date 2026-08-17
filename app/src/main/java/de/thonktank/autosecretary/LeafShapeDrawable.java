package de.thonktank.autosecretary;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class LeafShapeDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] radii;
    private final Path path = new Path();

    LeafShapeDrawable(int fillColor, int strokeColor, float strokeWidth,
                      float topLeft, float topRight, float bottomRight, float bottomLeft) {
        fill.setStyle(Paint.Style.FILL); fill.setColor(fillColor);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(strokeWidth);
        stroke.setColor(strokeColor);
        radii = new float[]{topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft};
    }

    @Override public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        float half = stroke.getStrokeWidth() / 2f;
        bounds.inset(half, half);
        path.reset(); path.addRoundRect(bounds, radii, Path.Direction.CW);
        canvas.drawPath(path, fill);
        if (stroke.getStrokeWidth() > 0) canvas.drawPath(path, stroke);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter); stroke.setColorFilter(colorFilter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

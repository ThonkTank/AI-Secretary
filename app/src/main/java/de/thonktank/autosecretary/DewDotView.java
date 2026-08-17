package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.PathInterpolator;

final class DewDotView extends View {
    static final float START_SCALE = .2f;
    static final float OVERSHOOT_SCALE = 1.14f;
    static final float OVERSHOOT_FRACTION = .58f;
    static final float CURVE_X1 = .34f;
    static final float CURVE_Y1 = 1.56f;
    static final float CURVE_X2 = .64f;
    static final float CURVE_Y2 = 1f;
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dropPath = new Path();
    private boolean on;
    private boolean dim;
    private DayPalette palette;
    private ValueAnimator dropAnimator;
    private float dropScale = 1f;
    private float dropAlpha = 1f;

    DewDotView(Context context) {
        super(context);
        style = new UiStyle(context);
        setClickable(true);
        setMinimumWidth(style.dp(48));
        setMinimumHeight(style.dp(48));
    }

    void bind(boolean on, boolean dim, DayPalette palette) {
        boolean animateOn = !this.on && on && isAttachedToWindow();
        this.on = on;
        this.dim = dim;
        this.palette = palette;
        invalidate();
        if (animateOn) {
            if (dropAnimator != null) dropAnimator.cancel();
            dropAnimator = ValueAnimator.ofFloat(0f, 1f);
            dropAnimator.setDuration(palette.motion.dewDurationMs);
            dropAnimator.setInterpolator(new PathInterpolator(
                    CURVE_X1, CURVE_Y1, CURVE_X2, CURVE_Y2));
            dropAnimator.addUpdateListener(animation -> {
                float progress = Math.max(0f, Math.min(1f,
                        (float) animation.getAnimatedValue()));
                if (progress < OVERSHOOT_FRACTION) {
                    float section = progress / OVERSHOOT_FRACTION;
                    dropScale = START_SCALE + (OVERSHOOT_SCALE - START_SCALE) * section;
                    dropAlpha = section;
                } else {
                    float section = (progress - OVERSHOOT_FRACTION)
                            / (1f - OVERSHOOT_FRACTION);
                    dropScale = OVERSHOOT_SCALE - (OVERSHOOT_SCALE - 1f) * section;
                    dropAlpha = 1f;
                }
                invalidate();
            });
            dropAnimator.start();
        } else {
            dropScale = 1f;
            dropAlpha = 1f;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = style.dp(13);
        paint.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(style.dp(1.5f));
        paint.setColor(on ? (dim ? UiStyle.alpha(palette.dot, .2f) : palette.accent) : palette.dot);
        canvas.drawCircle(cx, cy, radius, paint);
        if (!on) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dim ? palette.done : palette.accentText);
        paint.setAlpha(Math.round(255 * dropAlpha));
        Path path = dropPath;
        path.reset();
        float size = style.dp(dim ? 9 : 10);
        float dropRadius = size / 2f;
        path.addRoundRect(new RectF(cx - dropRadius, cy - dropRadius,
                        cx + dropRadius, cy + dropRadius),
                new float[]{dropRadius, dropRadius, dropRadius, dropRadius,
                        dropRadius, dropRadius, 0, 0},
                Path.Direction.CW);
        int save = canvas.save();
        canvas.rotate(-45f, cx, cy);
        canvas.scale(dropScale, dropScale, cx, cy);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(save);
        paint.setAlpha(255);
    }

    @Override protected void onDetachedFromWindow() {
        if (dropAnimator != null) dropAnimator.cancel();
        dropAnimator = null;
        super.onDetachedFromWindow();
    }
}

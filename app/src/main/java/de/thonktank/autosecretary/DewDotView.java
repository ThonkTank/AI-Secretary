package de.thonktank.autosecretary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.PathInterpolator;

final class DewDotView extends View {
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dropPath = new Path();
    private boolean on;
    private boolean dim;
    private DayPalette palette;

    DewDotView(Context context) {
        super(context);
        style = new UiStyle(context);
        setClickable(true);
        setMinimumWidth(style.dp(48));
        setMinimumHeight(style.dp(48));
    }

    void bind(boolean on, boolean dim, DayPalette palette) {
        boolean animateOn = !this.on && on && isAttachedToWindow()
                && android.animation.ValueAnimator.areAnimatorsEnabled();
        this.on = on;
        this.dim = dim;
        this.palette = palette;
        invalidate();
        if (animateOn) {
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator x = ObjectAnimator.ofFloat(this, SCALE_X, 1f, 1.14f, 1f);
            ObjectAnimator y = ObjectAnimator.ofFloat(this, SCALE_Y, 1f, 1.14f, 1f);
            set.playTogether(x, y);
            set.setDuration(palette.motion.dewDurationMs);
            set.setInterpolator(new PathInterpolator(.34f, 1f, .64f, 1f));
            set.start();
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
        Path path = dropPath;
        path.reset();
        float size = style.dp(dim ? 8 : 10);
        path.moveTo(cx, cy - size * .55f);
        path.cubicTo(cx + size * .7f, cy - size * .1f, cx + size * .6f,
                cy + size * .55f, cx, cy + size * .65f);
        path.cubicTo(cx - size * .6f, cy + size * .55f, cx - size * .7f,
                cy - size * .1f, cx, cy - size * .55f);
        path.close();
        canvas.drawPath(path, paint);
    }
}

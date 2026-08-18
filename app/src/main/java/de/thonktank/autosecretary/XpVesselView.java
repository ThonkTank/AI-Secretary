package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class XpVesselView extends View {
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DayPalette palette;
    private int collected;
    private float fill;
    private boolean ready;
    private ValueAnimator pulse;
    private float pulseAlpha;

    public XpVesselView(Context context) {
        super(context); style = new UiStyle(context);
        setMinimumWidth(style.dp(52)); setMinimumHeight(style.dp(52)); setClickable(true);
    }

    public void bind(int collected, int done, int total, boolean ready, int comboStage,
                     DayPalette palette) {
        this.collected = Math.max(0, collected);
        this.fill = total == 0 || collected == 0 ? 0f : done / (float) total;
        this.ready = ready; this.palette = palette;
        setEnabled(ready);
        if (ready && android.animation.ValueAnimator.areAnimatorsEnabled()) startPulse();
        else stopPulse();
        setContentDescription(ready ? getContext().getString(R.string.vessel_ready, collected)
                : getContext().getString(R.string.vessel_progress, done, total, collected));
        invalidate();
    }

    private void startPulse() {
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f, 0f); pulse.setDuration(2400); pulse.setRepeatCount(-1);
        pulse.addUpdateListener(value -> { pulseAlpha = (float) value.getAnimatedValue(); invalidate(); });
        pulse.start();
    }
    private void stopPulse() { if (pulse != null) pulse.cancel(); pulse = null; pulseAlpha = 0f; }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = style.dp(26);
        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.leaf1); canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(UiStyle.alpha(ready ? palette.light : palette.accent, .34f));
        float inner = r - style.dp(2.5f);
        int save = canvas.save(); canvas.clipRect(cx - inner, cy + inner - 2 * inner * fill,
                cx + inner, cy + inner); canvas.drawCircle(cx, cy, inner, paint); canvas.restoreToCount(save);
        if (ready && pulseAlpha > 0f) {
            paint.setColor(UiStyle.alpha(palette.light, .12f * pulseAlpha));
            canvas.drawCircle(cx, cy, r - style.dp(5), paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(style.dp(1.5f));
        paint.setColor(UiStyle.alpha(palette.dot, .55f)); canvas.drawCircle(cx, cy, r - style.dp(.75f), paint);
        if (collected > 0) {
            paint.setStyle(Paint.Style.FILL); paint.setTypeface(style.sans); paint.setTextSize(style.dp(14));
            paint.setTextAlign(Paint.Align.CENTER); paint.setColor(ready ? palette.light
                    : fill >= .55f ? palette.ink : palette.accent);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(String.valueOf(collected), cx, cy - (fm.ascent + fm.descent) / 2f, paint);
        }
    }

    @Override protected void onDetachedFromWindow() { stopPulse(); super.onDetachedFromWindow(); }
}

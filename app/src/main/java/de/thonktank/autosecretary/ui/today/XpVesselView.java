package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.PathInterpolator;

import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;

public final class XpVesselView extends View {
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path innerClip = new Path();
    private DayPalette palette;
    private int result;
    private int base;
    private String multiplierLabel = "1";
    private String breakdownLabel = "0 × 1";
    private float fill;
    private float displayedFill;
    private boolean ready;
    private boolean bound;
    private ValueAnimator pulse;
    private ValueAnimator fillAnimator;
    private float pulseAlpha;

    public XpVesselView(Context context) {
        super(context); style = new UiStyle(context);
        setMinimumWidth(style.dp(68)); setMinimumHeight(style.dp(68)); setClickable(true);
        AccessibilityRoles.button(this);
    }

    public void setPalette(DayPalette palette) {
        if (palette == null) throw new IllegalArgumentException("Vessel palette is required");
        this.palette = palette;
    }

    public void bind(XpVesselUiModel model) {
        if (model == null || palette == null)
            throw new IllegalStateException("Vessel model and palette are required");
        this.result = model.reward.resultXp;
        this.base = model.reward.baseXp;
        multiplierLabel = model.multiplierLabel;
        breakdownLabel = model.breakdownLabel;
        float nextFill = model.plannedXp == 0 ? 0f
                : Math.min(1f, model.earnedXp / (float) model.plannedXp);
        this.fill = nextFill;
        this.ready = model.ready;
        setActivated(model.ready);
        animateFill(nextFill);
        setEnabled(model.ready);
        if (model.ready && android.animation.ValueAnimator.areAnimatorsEnabled()) startPulse();
        else stopPulse();
        setContentDescription(model.ready
                ? getContext().getString(R.string.vessel_ready_breakdown,
                        this.result, this.base, multiplierLabel)
                : getContext().getString(R.string.vessel_progress_breakdown,
                        model.done, model.total, this.result, this.base, multiplierLabel));
        invalidate();
        bound = true;
    }

    private void animateFill(float target) {
        if (fillAnimator != null) fillAnimator.cancel();
        if (!bound || !ValueAnimator.areAnimatorsEnabled()
                || Math.abs(displayedFill - target) < .001f) {
            displayedFill = target;
            return;
        }
        fillAnimator = ValueAnimator.ofFloat(displayedFill, target);
        fillAnimator.setDuration(palette.motion.vesselFillDurationMs);
        fillAnimator.setInterpolator(new PathInterpolator(.2f, .7f, .3f, 1f));
        fillAnimator.addUpdateListener(value -> {
            displayedFill = (float) value.getAnimatedValue();
            invalidate();
        });
        fillAnimator.start();
    }

    private void startPulse() {
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0f, 1f, 0f);
        pulse.setDuration(palette.motion.vesselPulseDurationMs); pulse.setRepeatCount(-1);
        pulse.addUpdateListener(value -> { pulseAlpha = (float) value.getAnimatedValue(); invalidate(); });
        pulse.start();
    }
    private void stopPulse() { if (pulse != null) pulse.cancel(); pulse = null; pulseAlpha = 0f; }

    public boolean isPulsing() { return pulse != null; }
    public float fillFraction() { return fill; }
    boolean isFillAnimating() { return fillAnimator != null && fillAnimator.isRunning(); }
    public int renderedResult() { return result; }
    public String renderedBreakdown() { return breakdownLabel; }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.leaf1);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(UiStyle.alpha(ready ? palette.light : palette.accent, .34f));
        float inner = r - style.dp(2.5f);
        float fraction = Math.max(0f, Math.min(1f, displayedFill));
        FillGeometry fillGeometry = fillGeometry(cx, cy, inner, fraction);
        innerClip.reset();
        innerClip.addCircle(cx, cy, inner, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(innerClip);
        canvas.drawRect(cx - inner, fillGeometry.surfaceY, cx + inner, cy + inner, paint);
        if (fillGeometry.drawSurface) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(style.dp(1.5f));
            paint.setColor(UiStyle.alpha(ready ? palette.lightText : palette.accentText, .35f));
            canvas.drawLine(fillGeometry.chordLeft, fillGeometry.surfaceY,
                    fillGeometry.chordRight, fillGeometry.surfaceY, paint);
        }
        canvas.restoreToCount(save);
        if (ready && pulseAlpha > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(UiStyle.alpha(palette.light, .12f * pulseAlpha));
            canvas.drawCircle(cx, cy, r - style.dp(5), paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(style.dp(1.5f));
        paint.setColor(UiStyle.alpha(palette.dot, .55f)); canvas.drawCircle(cx, cy, r - style.dp(.75f), paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(style.sansBold);
        paint.setTextAlign(Paint.Align.CENTER); paint.setColor(ready ? palette.light
                : displayedFill >= .55f ? palette.ink : palette.accent);
        paint.setTextSize(style.dp(result >= 100 ? 13 : 15));
        canvas.drawText(String.valueOf(result), cx, cy - style.dp(3), paint);
        paint.setTypeface(style.sans); paint.setTextSize(style.dp(9));
        canvas.drawText(breakdownLabel, cx,
                cy + style.dp(12), paint);
    }

    static FillGeometry fillGeometry(float centerX, float centerY, float radius,
                                     float fraction) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        float surfaceY = centerY + radius - 2f * radius * clamped;
        float fromCenter = surfaceY - centerY;
        float halfChord = (float) Math.sqrt(Math.max(0f,
                radius * radius - fromCenter * fromCenter));
        return new FillGeometry(surfaceY, centerX - halfChord, centerX + halfChord,
                clamped > 0f && clamped < 1f);
    }

    static final class FillGeometry {
        final float surfaceY;
        final float chordLeft;
        final float chordRight;
        final boolean drawSurface;

        FillGeometry(float surfaceY, float chordLeft, float chordRight,
                     boolean drawSurface) {
            this.surfaceY = surfaceY;
            this.chordLeft = chordLeft;
            this.chordRight = chordRight;
            this.drawSurface = drawSurface;
        }
    }

    @Override protected void onDetachedFromWindow() {
        stopPulse();
        if (fillAnimator != null) fillAnimator.cancel();
        fillAnimator = null;
        super.onDetachedFromWindow();
    }
}

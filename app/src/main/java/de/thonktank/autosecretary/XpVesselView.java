package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;

public final class XpVesselView extends View {
    private final UiStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private DayPalette palette;
    private int result;
    private int base;
    private String multiplierLabel = "1";
    private String breakdownLabel = "0 × 1";
    private float fill;
    private float displayedFill;
    private boolean ready;
    private boolean bound;
    private int comboStage;
    private ValueAnimator pulse;
    private ValueAnimator fillAnimator;
    private float pulseAlpha;

    public XpVesselView(Context context) {
        super(context); style = new UiStyle(context);
        setMinimumWidth(style.dp(68)); setMinimumHeight(style.dp(68)); setClickable(true);
        AccessibilityRoles.button(this);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
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
        float nextFill = model.total == 0 || base == 0 ? 0f
                : model.done / (float) model.total;
        this.fill = nextFill;
        this.ready = model.ready;
        this.comboStage = model.reward.comboStage;
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

    boolean isPulsing() { return pulse != null; }
    float fillFraction() { return fill; }
    boolean isFillAnimating() { return fillAnimator != null && fillAnimator.isRunning(); }
    int renderedResult() { return result; }
    String renderedBreakdown() { return breakdownLabel; }

    @Override protected void onDraw(Canvas canvas) {
        if (palette == null) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(palette.leaf1);
        if (comboStage >= 5) {
            float alpha = Math.min(.46f, .16f + .04f * comboStage);
            paint.setShadowLayer(style.dp(10 + 2 * comboStage), 0f, 0f,
                    UiStyle.alpha(palette.light, alpha));
        }
        canvas.drawCircle(cx, cy, r, paint);
        paint.clearShadowLayer();
        paint.setColor(UiStyle.alpha(ready ? palette.light : palette.accent, .34f));
        float inner = r - style.dp(2.5f);
        float surface = cy + inner - 2 * inner * displayedFill;
        int save = canvas.save(); canvas.clipRect(cx - inner, surface,
                cx + inner, cy + inner); canvas.drawCircle(cx, cy, inner, paint); canvas.restoreToCount(save);
        if (displayedFill > 0f) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(style.dp(1.5f));
            paint.setColor(UiStyle.alpha(ready ? palette.lightText : palette.accentText, .35f));
            canvas.drawLine(cx - inner * .72f, surface + style.dp(1),
                    cx + inner * .72f, surface + style.dp(1), paint);
        }
        if (ready && pulseAlpha > 0f) {
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

    @Override protected void onDetachedFromWindow() {
        stopPulse();
        if (fillAnimator != null) fillAnimator.cancel();
        fillAnimator = null;
        super.onDetachedFromWindow();
    }
}

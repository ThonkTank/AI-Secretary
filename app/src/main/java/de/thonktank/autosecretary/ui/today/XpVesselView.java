package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import de.thonktank.autosecretary.presentation.today.XpVesselUiModel;

public final class XpVesselView extends View {
    private final UiStyle style;
    private final java.util.function.LongSupplier currentTimeMillis;
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
    private boolean compact;
    private de.thonktank.autosecretary.presentation.today.FlowChainUiModel chain;
    private final Runnable clockFrame = () -> { invalidate(); scheduleClock(); };
    private final Runnable animationVisibility = this::refreshAnimationVisibility;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutVisibility = this::refreshAnimationVisibility;
    private final ViewTreeObserver.OnScrollChangedListener scrollVisibility = this::refreshAnimationVisibility;
    private ViewTreeObserver animationObserver;

    public void setCompact(boolean value) {
        compact = value;
        setMinimumWidth(style.dp(value ? 36 : 68));
        setMinimumHeight(style.dp(value ? 36 : 68));
    }

    public void bindChain(de.thonktank.autosecretary.presentation.today.FlowChainUiModel value) {
        chain = value;
        bind(value.vessel);
        // The parent group owns the full-size touch and accessibility target.
        setEnabled(true); setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        scheduleClock();
    }

    void refreshAnimationVisibility() {
        if (ready && isAnimationVisible() && ValueAnimator.areAnimatorsEnabled()) startPulse();
        else stopPulse();
        scheduleClock();
    }

    private boolean isAnimationVisible() {
        return isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown()
                && getGlobalVisibleRect(new android.graphics.Rect());
    }

    private void scheduleClock() {
        removeCallbacks(clockFrame);
        if (chain != null && chain.mode == de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.COUNTDOWN
                && isAnimationVisible()
                && chain.readyAt > currentTimeMillis.getAsLong())
            postDelayed(clockFrame, ValueAnimator.areAnimatorsEnabled() ? 50L : 1000L);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animationObserver = getViewTreeObserver();
        animationObserver.addOnGlobalLayoutListener(layoutVisibility);
        animationObserver.addOnScrollChangedListener(scrollVisibility);
        post(animationVisibility);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (clockFrame != null) refreshAnimationVisibility();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (clockFrame != null) refreshAnimationVisibility();
    }

    private ValueAnimator pulse;
    private ValueAnimator fillAnimator;
    private float pulseAlpha;

    public XpVesselView(Context context) { this(context, System::currentTimeMillis); }

    public XpVesselView(Context context, java.util.function.LongSupplier currentTimeMillis) {
        super(context); style = new UiStyle(context);
        this.currentTimeMillis = currentTimeMillis;
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
        float nextFill = compact && model.ready ? 1f : model.plannedXp == 0 ? 0f
                : Math.min(1f, model.earnedXp / (float) model.plannedXp);
        this.fill = nextFill;
        this.ready = model.ready;
        setActivated(model.ready);
        animateFill(nextFill);
        setEnabled(model.ready);
        refreshAnimationVisibility();
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
        if (chain != null && (chain.mode == de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.COUNTDOWN
                || chain.mode == de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.RESOURCE)) {
            drawWaiting(canvas, cx, cy, r);
            return;
        }
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
        paint.setTextSize(textSize(result >= 100 ? 13 : 15));
        canvas.drawText(String.valueOf(result), cx, cy - style.dp(3), paint);
        paint.setTypeface(style.sans); paint.setTextSize(textSize(9));
        canvas.drawText(breakdownLabel, cx,
                cy + style.dp(12), paint);
    }

    private float textSize(float sp) {
        return compact ? sp * getResources().getDisplayMetrics().scaledDensity : style.dp(sp);
    }

    private void drawWaiting(Canvas canvas, float cx, float cy, float radius) {
        boolean timed = chain.mode == de.thonktank.autosecretary.presentation.today.FlowChainUiModel.Mode.COUNTDOWN;
        de.thonktank.autosecretary.presentation.today.FlowCountdown clock = timed
                ? new de.thonktank.autosecretary.presentation.today.FlowCountdown(
                        chain.waitStartedAt, chain.readyAt, currentTimeMillis.getAsLong()) : null;
        float inner = radius - style.dp(2.5f);
        if (clock != null) {
            paint.setColor(UiStyle.alpha(palette.accent, .34f));
            canvas.drawArc(cx - inner, cy - inner, cx + inner, cy + inner,
                    -90f + 360f * (1f - clock.fraction), 360f * clock.fraction, true, paint);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(style.dp(1.5f));
        paint.setColor(UiStyle.alpha(palette.dot, .55f));
        canvas.drawCircle(cx, cy, radius - style.dp(.75f), paint);
        if (clock == null) {
            paint.setColor(palette.hint); paint.setStrokeWidth(style.dp(2));
            canvas.drawLine(cx - style.dp(3), cy - style.dp(5), cx - style.dp(3), cy + style.dp(5), paint);
            canvas.drawLine(cx + style.dp(3), cy - style.dp(5), cx + style.dp(3), cy + style.dp(5), paint);
            return;
        }
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(palette.ink); paint.setTypeface(style.sansBold);
        paint.setTextSize(textSize(clock.value >= 100 ? 13 : 15));
        canvas.drawText(String.valueOf(clock.value), cx, cy - style.dp(3), paint);
        paint.setTypeface(style.sans); paint.setTextSize(textSize(9));
        canvas.drawText(countdownUnit(getContext(), clock), cx, cy + style.dp(11), paint);
    }

    static String countdownUnit(Context context, de.thonktank.autosecretary.presentation.today.FlowCountdown clock) {
        switch (clock.unit) {
            case DAYS: return context.getResources().getQuantityString(R.plurals.flow_clock_days, (int) clock.value);
            case HOURS: return context.getString(R.string.flow_clock_hours);
            case MINUTES: return context.getString(R.string.flow_clock_minutes);
            default: return context.getString(R.string.flow_clock_seconds);
        }
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
        removeCallbacks(animationVisibility);
        removeCallbacks(clockFrame);
        if (animationObserver != null && animationObserver.isAlive()) {
            animationObserver.removeOnGlobalLayoutListener(layoutVisibility);
            animationObserver.removeOnScrollChangedListener(scrollVisibility);
        }
        animationObserver = null;
        stopPulse();
        if (fillAnimator != null) fillAnimator.cancel();
        fillAnimator = null;
        super.onDetachedFromWindow();
    }
}

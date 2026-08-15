package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Cached three-depth forest derived from the approved handoff silhouettes. */
final class ForestBackdropView extends View {
    private final ForestArtworkRenderer artwork;
    private DayPalette palette;
    private ValueAnimator breathing;

    ForestBackdropView(Context context) {
        super(context);
        artwork = new ForestArtworkRenderer(getResources().getDisplayMetrics().density);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPalette(DayPalette palette) {
        this.palette = palette;
        artwork.setPalette(palette);
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateBreathing();
    }

    @Override protected void onDetachedFromWindow() {
        stopBreathing();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isAttachedToWindow()) updateBreathing();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        artwork.setSize(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        artwork.draw(canvas);
    }

    private void updateBreathing() {
        stopBreathing();
        if (!isShown() || !ValueAnimator.areAnimatorsEnabled()) {
            setAlpha(1f);
            return;
        }
        MotionTokens motion = palette == null ? MotionTokens.standard() : palette.motion;
        breathing = ValueAnimator.ofFloat(0f, 1f);
        breathing.setDuration(motion.forestBreathDurationMs);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.setRepeatMode(ValueAnimator.REVERSE);
        breathing.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setAlpha(1f - motion.forestBreathAlpha + progress * motion.forestBreathAlpha);
        });
        breathing.start();
    }

    private void stopBreathing() {
        if (breathing != null) {
            breathing.cancel();
            breathing = null;
        }
        setAlpha(1f);
    }

    boolean isBreathing() {
        return breathing != null && breathing.isStarted();
    }
}

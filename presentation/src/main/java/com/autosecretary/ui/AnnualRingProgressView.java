package com.autosecretary.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.autosecretary.presentation.R;

/** Three annual rings used as the transient local-model import/inference progress mark. */
public final class AnnualRingProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress;

    public AnnualRingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setRunning(boolean running) {
        if (!running) {
            if (animator != null) animator.cancel();
            animator = null;
            progress = 0f;
            invalidate();
            return;
        }
        if (animator != null || !ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(getContext().getColor(R.color.amber));
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float[] radii = {dp(16), dp(29), dp(42)};
        for (int index = 0; index < radii.length; index++) {
            float local = Math.max(0f, Math.min(1f, progress * 1.45f - index * .22f));
            canvas.drawArc(cx - radii[index], cy - radii[index],
                    cx + radii[index], cy + radii[index], -90, 360 * local, false, paint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

package com.autosecretary.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** One quiet completion gesture: a leaf sheen followed by a growing annual ring. */
public final class CelebrationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 1f;

    public CelebrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
    }

    public void burst() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        setVisibility(VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(520);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                setVisibility(GONE);
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(60);
        float right = getWidth() - dp(22);
        float top = dp(100);
        float bottom = Math.min(getHeight() * .62f, top + dp(430));
        float sheenX = left - dp(100) + (right - left + dp(200)) * progress;
        int amber = getContext().getColor(
                com.autosecretary.presentation.R.color.amber);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                sheenX - dp(45), top,
                sheenX + dp(45), bottom,
                new int[] {Color.TRANSPARENT, withAlpha(amber, 92), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);

        float ringProgress = Math.max(0f, (progress - .28f) / .72f);
        float centerX = right - dp(38);
        float centerY = bottom - dp(35);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(withAlpha(amber, Math.round(190 * (1f - progress * .35f))));
        for (int index = 0; index < 3; index++) {
            float radius = dp(10 + index * 8) * ringProgress;
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

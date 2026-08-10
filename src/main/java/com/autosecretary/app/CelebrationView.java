package com.autosecretary.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Brief local confetti burst; no gamification screen or persistent reward subsystem. */
public final class CelebrationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);
    private final float[] x = new float[32];
    private final float[] y = new float[32];
    private final float[] velocity = new float[32];
    private float progress = 1f;

    public CelebrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
    }

    public void burst() {
        for (int index = 0; index < x.length; index++) {
            x[index] = random.nextFloat();
            y[index] = random.nextFloat() * .25f;
            velocity[index] = .6f + random.nextFloat() * .8f;
        }
        setVisibility(VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(850);
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
        int[] colors = {0xFFFFC857, 0xFF41B883, 0xFFFF6B6B, 0xFF8B7CF6};
        for (int index = 0; index < x.length; index++) {
            paint.setColor(colors[index % colors.length]);
            paint.setAlpha((int) (255 * (1f - progress)));
            float px = x[index] * getWidth();
            float py = (y[index] + velocity[index] * progress) * getHeight();
            canvas.save();
            canvas.rotate(progress * 240 + index * 11, px, py);
            canvas.drawRect(px - 7, py - 3, px + 7, py + 3, paint);
            canvas.restore();
        }
    }
}

package de.thonktank.autosecretary;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.PathInterpolator;

/** Motion contract shared by editor pages, dependent controls and prompts. */
final class TaskEditorMotion {
    static final float EASE_X1 = .2f;
    static final float EASE_Y1 = .7f;
    static final float EASE_X2 = .3f;
    static final float EASE_Y2 = 1f;

    private TaskEditorMotion() { }

    static boolean enabled() { return ValueAnimator.areAnimatorsEnabled(); }

    static long duration(DayPalette palette) {
        return palette.motion.stateChangeDurationMs;
    }

    static PathInterpolator interpolator() {
        return new PathInterpolator(EASE_X1, EASE_Y1, EASE_X2, EASE_Y2);
    }

    static void enter(View view, DayPalette palette, float translationDp, UiStyle style) {
        enter(view, palette, translationDp, style, enabled());
    }

    static void enter(View view, DayPalette palette, float translationDp, UiStyle style,
                      boolean animationsEnabled) {
        cancel(view);
        if (!animationsEnabled) {
            settle(view);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(style.dp(translationDp));
        view.animate().alpha(1f).translationY(0f).setDuration(duration(palette))
                .setInterpolator(interpolator()).start();
    }

    static void fadeIn(View view, DayPalette palette) {
        fadeIn(view, palette, enabled());
    }

    static void fadeIn(View view, DayPalette palette, boolean animationsEnabled) {
        cancel(view);
        if (!animationsEnabled) {
            settle(view);
            return;
        }
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(duration(palette))
                .setInterpolator(interpolator()).start();
    }

    static void fadeOut(View view, DayPalette palette, Runnable finished) {
        fadeOut(view, palette, finished, enabled());
    }

    static void fadeOut(View view, DayPalette palette, Runnable finished,
                        boolean animationsEnabled) {
        cancel(view);
        if (!animationsEnabled) {
            view.setAlpha(0f);
            finished.run();
            return;
        }
        view.animate().alpha(0f).setDuration(duration(palette))
                .setInterpolator(interpolator()).withEndAction(finished).start();
    }

    static void cancel(View view) {
        ViewPropertyAnimator animator = view.animate();
        animator.setListener(null);
        animator.withEndAction(null);
        animator.cancel();
    }

    static void settle(View view) {
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
    }
}

package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.ui.leaf.LeafSurface;

/** Isolates focus-card transition effects from content binding and measurement. */
final class FocusCardAnimationController {
    private final UiStyle style;
    private final FrameLayout root;
    private final LeafSurface card;
    private final View glint;
    private final View afterglow;

    FocusCardAnimationController(Context context, FrameLayout root, LeafSurface card) {
        style = new UiStyle(context);
        this.root = root;
        this.card = card;
        glint = new View(context);
        glint.setVisibility(View.INVISIBLE);
        glint.setRotation(-14f);
        root.addView(glint, new FrameLayout.LayoutParams(style.dp(64), 0));
        afterglow = new View(context);
        afterglow.setVisibility(View.INVISIBLE);
        root.addView(afterglow, new FrameLayout.LayoutParams(-1, 0));
    }

    void focusChanged(DayPalette palette, boolean deferred) {
        card.animate().cancel();
        card.setTranslationY(style.dp(palette.motion.focusEnterDistanceDp));
        card.setAlpha(.86f);
        card.animate().translationY(0f).alpha(1f)
                .setDuration(palette.motion.stateChangeDurationMs)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                .withEndAction(() -> {
                    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
                    if (deferred) playAfterglow(palette);
                    else playGlint(Color.WHITE, palette.motion.glintDurationMs, .16f);
                });
    }

    void cancel() {
        card.animate().cancel();
        glint.animate().cancel();
        afterglow.animate().cancel();
        card.setTranslationY(0f);
        card.setAlpha(1f);
        glint.setVisibility(View.INVISIBLE);
        afterglow.setVisibility(View.INVISIBLE);
    }

    private void playGlint(int color, long duration, float alpha) {
        glint.animate().cancel();
        glint.layout(0, card.getTop(), style.dp(64), card.getBottom());
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, UiStyle.alpha(color, alpha), Color.TRANSPARENT});
        glint.setBackground(sheen);
        glint.setTranslationX(-style.dp(64));
        glint.setVisibility(View.VISIBLE);
        glint.animate().translationX(Math.max(root.getWidth(), style.dp(320)))
                .setDuration(duration)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> glint.setVisibility(View.INVISIBLE));
    }

    private void playAfterglow(DayPalette palette) {
        afterglow.animate().cancel();
        afterglow.layout(0, card.getTop(), root.getWidth(), card.getBottom());
        afterglow.setRotation(card.getRotation());
        GradientDrawable outline = card.shape().drawable(card.getContext(),
                Color.TRANSPARENT, palette.light, 2);
        outline.setStroke(style.dp(2), palette.light);
        afterglow.setBackground(outline);
        afterglow.setAlpha(1f);
        afterglow.setVisibility(View.VISIBLE);
        afterglow.animate().alpha(0f).setDuration(palette.motion.afterglowDurationMs)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> afterglow.setVisibility(View.INVISIBLE));
    }
}

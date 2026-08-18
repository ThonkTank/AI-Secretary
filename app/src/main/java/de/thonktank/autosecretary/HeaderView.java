package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.animation.ValueAnimator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.time.LocalTime;
import java.util.Arrays;

import de.thonktank.autosecretary.domain.model.XpProgress;

@SuppressLint("ViewConstructor")
public final class HeaderView extends FrameLayout {
    private final UiStyle style;
    private final FrameLayout leaf;
    private final WoodGrainView grain;
    private final TextView greeting;
    private final TextView level;
    private final TextView progress;
    private final TextView add;
    private final View glint;
    private final View afterglow;

    public HeaderView(Context context, Runnable onAdd) {
        super(context); style = new UiStyle(context);
        setPadding(style.dp(60), style.dp(12), style.dp(22), 0);
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        leaf = new FrameLayout(context); leaf.setRotation(1.1f); leaf.setClipChildren(true);
        leaf.setId(R.id.header_reward_anchor);
        grain = new WoodGrainView(context); leaf.addView(grain, new LayoutParams(-1, -1));
        LinearLayout status = new LinearLayout(context); status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(style.dp(22), style.dp(10), style.dp(24), style.dp(13));
        greeting = style.serif("", 16, 0, true, 300);
        status.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout values = new LinearLayout(context); values.setOrientation(LinearLayout.VERTICAL);
        values.setGravity(Gravity.END);
        level = style.serif("", 20, 0, false, 400); progress = style.sans("", 14, 0, false);
        values.addView(level); values.addView(progress); status.addView(values);
        leaf.addView(status, new LayoutParams(-1, -1));
        glint = new View(context); glint.setVisibility(INVISIBLE);
        leaf.addView(glint, new LayoutParams(style.dp(64), -1));
        afterglow = new View(context); afterglow.setVisibility(INVISIBLE);
        leaf.addView(afterglow, new LayoutParams(-1, -1));
        row.addView(leaf, new LinearLayout.LayoutParams(0, style.dp(69), 1));

        FrameLayout addTarget = new FrameLayout(context);
        addTarget.setId(R.id.header_add_task);
        addTarget.setContentDescription(context.getString(R.string.content_add_task));
        addTarget.setOnClickListener(view -> onAdd.run());
        add = style.sans("＋", 23, 0, false); add.setGravity(Gravity.CENTER);
        add.setId(R.id.header_add_task_visual);
        addTarget.addView(add, new LayoutParams(style.dp(40), style.dp(40), Gravity.CENTER));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(style.dp(48), style.dp(48));
        addParams.leftMargin = style.dp(10); row.addView(addTarget, addParams);
        addView(row, new LayoutParams(-1, -1));
    }

    public void bind(LocalTime time, DayPalette palette, XpProgress value) {
        greeting.setText(DayPalette.greetingRes(time)); greeting.setTextColor(palette.ink2);
        level.setText(getContext().getString(R.string.xp_level, value.level)); level.setTextColor(palette.ink2);
        progress.setText(getContext().getString(R.string.xp_progress, value.inLevel, value.required));
        progress.setTextColor(palette.muted);
        WoodGrainView.applyTextHalo(greeting, palette.leaf2);
        WoodGrainView.applyTextHalo(level, palette.leaf2);
        WoodGrainView.applyTextHalo(progress, palette.leaf2);
        leaf.setBackground(style.leaf(palette.leaf2, palette.leaf2Edge, 8, 56, 8, 56));
        style.shadow(leaf, palette, 7, .7f);
        add.setTextColor(palette.lightText); add.setBackground(style.pill(palette.light, 24));
        grain.bindCorner(palette, value.ratio, Arrays.asList(greeting, level, progress));
    }

    public void playRewardGlint(DayPalette palette) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        GradientDrawable light = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, UiStyle.alpha(palette.light, .38f), Color.TRANSPARENT});
        glint.setBackground(light); glint.setTranslationX(-style.dp(64)); glint.setVisibility(VISIBLE);
        glint.animate().translationX(Math.max(leaf.getWidth(), style.dp(260)))
                .setDuration(palette.motion.glintDurationMs)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                .withEndAction(() -> glint.setVisibility(INVISIBLE));
        GradientDrawable edge = style.leaf(Color.TRANSPARENT, palette.light,
                8, 56, 8, 56);
        edge.setStroke(style.dp(2), UiStyle.alpha(palette.light, .58f));
        afterglow.animate().cancel();
        afterglow.setBackground(edge); afterglow.setAlpha(1f); afterglow.setVisibility(VISIBLE);
        afterglow.animate().alpha(0f).setDuration(palette.motion.headerAfterglowDurationMs)
                .withEndAction(() -> afterglow.setVisibility(INVISIBLE));
    }

    boolean isGlintVisible() { return glint.getVisibility() == VISIBLE; }
    View rewardAnchor() { return leaf; }
}

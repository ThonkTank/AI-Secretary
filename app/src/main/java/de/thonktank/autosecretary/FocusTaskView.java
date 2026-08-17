package de.thonktank.autosecretary;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FocusTaskView extends FrameLayout {
    public interface Actions {
        void onComplete(TaskSnapshot task);
        void onDefer(TaskSnapshot task);
        void onToggleStep(TaskStepSnapshot step);
    }

    private final UiStyle style;
    private final View back;
    private final View middle;
    private final LinearLayout card;
    private final LayoutParams cardParams;
    private final LinearLayout titleBlock;
    private final TextView marker;
    private final TextView title;
    private final TextView softTime;
    private final YearRingView ring;
    private final LinearLayout steps;
    private final List<StepRow> stepRows = new ArrayList<>();
    private final LinearLayout actions;
    private final LinearLayout.LayoutParams actionParams;
    private final TextView primary;
    private final TextLinkView later;
    private final View glint;
    private final View afterglow;
    private String boundTaskId;
    private boolean deferPending;

    public FocusTaskView(Context context) {
        super(context);
        style = new UiStyle(context);
        setClipChildren(false);
        back = new View(context);
        LayoutParams backParams = new LayoutParams(-1, style.dp(82));
        backParams.setMargins(style.dp(18), style.dp(34), style.dp(4), 0);
        addView(back, backParams);
        middle = new View(context);
        LayoutParams middleParams = new LayoutParams(-1, style.dp(88));
        middleParams.setMargins(style.dp(8), style.dp(18), style.dp(12), 0);
        addView(middle, middleParams);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(24), style.dp(24), style.dp(28), style.dp(24));
        card.setRotation(-.7f);
        cardParams = new LayoutParams(-1, -2);
        cardParams.topMargin = style.dp(22);
        addView(card, cardParams);

        FrameLayout titleRow = new FrameLayout(context);
        titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        marker = style.serif("", 20, 0, true, 300);
        titleBlock.addView(marker);
        title = style.serif("", 37, 0, false, 200);
        title.setLineSpacing(0, 1.04f);
        title.setLetterSpacing(-.02f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = style.dp(4);
        titleBlock.addView(title, titleParams);
        softTime = style.sans("", 17, 0, false);
        LinearLayout.LayoutParams softParams = new LinearLayout.LayoutParams(-1, -2);
        softParams.setMargins(0, style.dp(7), 0, 0);
        titleBlock.addView(softTime, softParams);
        titleRow.addView(titleBlock, new LayoutParams(-1, -2));
        ring = new YearRingView(context);
        LayoutParams ringParams = new LayoutParams(style.dp(52), style.dp(52), Gravity.TOP | Gravity.END);
        titleRow.addView(ring, ringParams);
        card.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        steps = new LinearLayout(context);
        steps.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(-1, -2);
        stepsParams.setMargins(0, style.dp(18), 0, 0);
        card.addView(steps, stepsParams);
        actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.setMargins(0, style.dp(22), 0, 0);
        card.addView(actions, actionParams);
        primary = new TextView(context);
        primary.setGravity(Gravity.CENTER);
        primary.setMinHeight(style.dp(52));
        primary.setPadding(style.dp(28), 0, style.dp(28), 0);
        primary.setTypeface(style.sansBold);
        primary.setTextSize(17);
        actions.addView(primary, new LinearLayout.LayoutParams(-2, style.dp(52)));
        later = new TextLinkView(context);
        later.setText(R.string.action_later);
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-2, style.dp(52));
        laterParams.setMargins(style.dp(18), 0, 0, 0);
        actions.addView(later, laterParams);

        glint = new View(context);
        glint.setVisibility(INVISIBLE);
        glint.setRotation(-14f);
        LayoutParams glintParams = new LayoutParams(style.dp(64), 0);
        glintParams.topMargin = style.dp(22);
        addView(glint, glintParams);
        afterglow = new View(context);
        afterglow.setVisibility(INVISIBLE);
        LayoutParams afterglowParams = new LayoutParams(-1, 0);
        afterglowParams.topMargin = style.dp(22);
        addView(afterglow, afterglowParams);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, Actions callbacks) {
        boolean focusChanged = boundTaskId != null && !boundTaskId.equals(task.taskId);
        boundTaskId = task.taskId;
        boolean compactOngoing = task.ongoing && task.steps.isEmpty();
        setMinimumHeight(style.dp(compactOngoing ? 205 : task.steps.isEmpty() ? 275 : 387));
        cardParams.topMargin = style.dp(compactOngoing ? 0 : 22);
        card.setLayoutParams(cardParams);
        card.setPadding(style.dp(24), style.dp(24), style.dp(28),
                style.dp(compactOngoing ? 30 : 24));
        actionParams.topMargin = style.dp(compactOngoing ? 30 : 22);
        actions.setLayoutParams(actionParams);
        back.setVisibility(stacked && !compactOngoing ? VISIBLE : GONE);
        middle.setVisibility(stacked && !compactOngoing ? VISIBLE : GONE);
        back.setBackground(style.leaf(palette.leaf3, style.edge(palette, 3), 8, 56, 8, 56));
        back.setRotation(2.2f);
        style.shadow(back, palette, 5, .75f);
        middle.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 56, 8, 56, 8));
        middle.setRotation(-1.5f);
        style.shadow(middle, palette, 5, .75f);
        card.setBackground(style.leaf(palette.leaf1, style.edge(palette, 1), 10, 64, 10, 64));
        style.shadow(card, palette, 12, 1f);
        card.setTranslationY(0f);
        card.setAlpha(1f);
        marker.setText(task.overdue ? R.string.marker_overdue : R.string.marker_now);
        marker.setTextColor(task.overdue ? palette.bad : palette.accent);
        title.setText(task.title);
        title.setTextSize(task.title.length() > 42 ? 30 : 37);
        title.setTextColor(palette.ink);
        softTime.setText(task.softTime);
        softTime.setTextColor(palette.hint);
        ring.setVisibility(task.ringWeeks > 0 ? VISIBLE : GONE);
        ring.bind(task.ringWeeks, palette);
        titleBlock.setPadding(0, 0, 0, 0);
        title.setPadding(0, 0, task.ringWeeks > 0 ? style.dp(66) : 0, 0);
        bindSteps(task, palette, callbacks);
        primary.setText(task.actionLabel(getContext()));
        primary.setTextColor(palette.accentText);
        primary.setBackground(style.pill(palette.accent, 26));
        style.shadow(primary, palette, 5, .7f);
        primary.setOnClickListener(view -> callbacks.onComplete(task));
        later.setVisibility(allowDefer ? VISIBLE : GONE);
        later.bind(palette.hint, palette.dot);
        later.setOnClickListener(view -> {
            deferPending = true;
            callbacks.onDefer(task);
        });
        if (focusChanged) animateFocusChange(palette);
    }

    private void bindSteps(TaskSnapshot task, DayPalette palette, Actions callbacks) {
        steps.setVisibility(task.steps.isEmpty() ? GONE : VISIBLE);
        while (stepRows.size() < task.steps.size()) {
            StepRow row = new StepRow(getContext());
            steps.addView(row.root, new LinearLayout.LayoutParams(-1, -2));
            stepRows.add(row);
        }
        for (int i = 0; i < stepRows.size(); i++) {
            StepRow row = stepRows.get(i);
            if (i >= task.steps.size()) {
                row.root.setVisibility(GONE);
                continue;
            }
            row.root.setVisibility(VISIBLE);
            TaskStepSnapshot step = task.steps.get(i);
            row.dot.bind(step.done, false, palette);
            row.dot.setContentDescription((step.done ? getContext().getString(R.string.marker_done) + ": " : "") + step.label);
            row.dot.setOnClickListener(view -> callbacks.onToggleStep(step));
            row.label.setText(step.done ? strike(step.label) : step.label);
            row.label.setTextColor(step.done ? palette.done : palette.ink);
        }
    }

    private final class StepRow {
        final LinearLayout root = new LinearLayout(getContext());
        final DewDotView dot = new DewDotView(getContext());
        final TextView label = style.sans("", 19, 0, false);

        StepRow(Context context) {
            root.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(style.dp(48), style.dp(48));
            dotParams.setMargins(0, -style.dp(3), 0, -style.dp(4));
            root.addView(dot, dotParams);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMargins(style.dp(4), 0, 0, 0);
            root.addView(label, labelParams);
        }
    }

    private void playGlint(int color, long duration, float alpha) {
        glint.animate().cancel();
        LayoutParams params = (LayoutParams) glint.getLayoutParams();
        params.height = card.getHeight();
        glint.setLayoutParams(params);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, UiStyle.alpha(color, alpha), Color.TRANSPARENT});
        glint.setBackground(sheen);
        glint.setTranslationX(-style.dp(64));
        glint.setVisibility(VISIBLE);
        glint.animate().translationX(Math.max(getWidth(), style.dp(320))).setDuration(duration)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> glint.setVisibility(INVISIBLE));
    }

    private void animateFocusChange(DayPalette palette) {
        boolean deferred = deferPending;
        deferPending = false;
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

    private void playAfterglow(DayPalette palette) {
        afterglow.animate().cancel();
        LayoutParams params = (LayoutParams) afterglow.getLayoutParams();
        params.height = card.getHeight();
        afterglow.setLayoutParams(params);
        afterglow.setRotation(card.getRotation());
        GradientDrawable outline = style.leaf(Color.TRANSPARENT, palette.light,
                10, 64, 10, 64);
        outline.setStroke(style.dp(2), palette.light);
        afterglow.setBackground(outline);
        afterglow.setAlpha(1f);
        afterglow.setVisibility(VISIBLE);
        afterglow.animate().alpha(0f).setDuration(palette.motion.afterglowDurationMs)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> afterglow.setVisibility(INVISIBLE));
    }

    private static CharSequence strike(String text) {
        SpannableString value = new SpannableString(text);
        value.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
}

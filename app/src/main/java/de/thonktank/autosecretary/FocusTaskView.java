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
    private final TextView marker;
    private final TextView title;
    private final TextView softTime;
    private final YearRingView ring;
    private final LinearLayout steps;
    private final List<StepRow> stepRows = new ArrayList<>();
    private final LinearLayout actions;
    private final TextView primary;
    private final TextView later;

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
        card.setElevation(style.dp(12));
        addView(card, new LayoutParams(-1, -2));

        FrameLayout titleRow = new FrameLayout(context);
        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        marker = style.serif("", 20, 0, true, 300);
        titleBlock.addView(marker);
        title = style.serif("", 37, 0, false, 200);
        title.setLineSpacing(0, .96f);
        titleBlock.addView(title, new LinearLayout.LayoutParams(-1, -2));
        softTime = style.sans("", 17, 0, false);
        LinearLayout.LayoutParams softParams = new LinearLayout.LayoutParams(-1, -2);
        softParams.setMargins(0, style.dp(8), 0, 0);
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
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.setMargins(0, style.dp(22), 0, 0);
        card.addView(actions, actionParams);
        primary = new TextView(context);
        primary.setGravity(Gravity.CENTER);
        primary.setMinHeight(style.dp(52));
        primary.setPadding(style.dp(28), 0, style.dp(28), 0);
        primary.setTypeface(style.sansBold);
        primary.setTextSize(17);
        actions.addView(primary, new LinearLayout.LayoutParams(-2, style.dp(52)));
        later = style.sans(context.getString(R.string.action_later), 17, 0, false);
        later.setGravity(Gravity.CENTER);
        later.setMinWidth(style.dp(48));
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-2, style.dp(52));
        laterParams.setMargins(style.dp(18), 0, 0, 0);
        actions.addView(later, laterParams);
    }

    public void bind(TaskSnapshot task, boolean stacked, boolean allowDefer,
                     DayPalette palette, Actions callbacks) {
        setMinimumHeight(style.dp(task.steps.isEmpty() ? 225 : 365));
        back.setVisibility(stacked ? VISIBLE : GONE);
        middle.setVisibility(stacked ? VISIBLE : GONE);
        back.setBackground(style.leaf(palette.leaf3, style.edge(palette, .16f), 8, 56, 8, 56));
        back.setRotation(2.2f);
        back.setElevation(style.dp(5));
        middle.setBackground(style.leaf(palette.leaf2, style.edge(palette, .16f), 8, 56, 8, 56));
        middle.setRotation(-1.5f);
        middle.setElevation(style.dp(5));
        card.setBackground(style.leaf(palette.leaf1, style.edge(palette, .32f), 10, 64, 10, 64));
        marker.setText(task.overdue ? R.string.marker_overdue : R.string.marker_now);
        marker.setTextColor(task.overdue ? palette.bad : palette.accent);
        title.setText(task.title);
        title.setTextSize(task.title.length() > 26 ? 30 : 37);
        title.setTextColor(palette.ink);
        softTime.setText(task.softTime);
        softTime.setTextColor(palette.hint);
        ring.setVisibility(task.ringWeeks > 0 ? VISIBLE : GONE);
        ring.bind(task.ringWeeks, palette);
        bindSteps(task, palette, callbacks);
        primary.setText(task.actionLabel(getContext()));
        primary.setTextColor(palette.accentText);
        primary.setBackground(style.pill(palette.accent, 26));
        primary.setElevation(style.dp(5));
        primary.setOnClickListener(view -> callbacks.onComplete(task));
        later.setVisibility(allowDefer ? VISIBLE : GONE);
        later.setTextColor(palette.hint);
        later.setOnClickListener(view -> {
            card.animate().rotation(1.5f).alpha(.78f).setDuration(180)
                    .withEndAction(() -> callbacks.onDefer(task));
        });
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
            root.addView(dot, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMargins(style.dp(7), 0, 0, 0);
            root.addView(label, labelParams);
        }
    }

    private static CharSequence strike(String text) {
        SpannableString value = new SpannableString(text);
        value.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
}

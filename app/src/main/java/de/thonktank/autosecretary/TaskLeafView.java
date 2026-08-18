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
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class TaskLeafView extends FrameLayout {
    private final UiStyle style;
    private final LinearLayout content;
    private final TextView marker;
    private final TextView title;
    private final TextView softTime;
    private final DewDotView dot;
    private final TextView menu;
    private final LinearLayout progress;
    private final List<View> bars = new ArrayList<>();
    private final TextView progressLabel;
    private final WoodGrainView grain;

    public TaskLeafView(Context context) {
        super(context); style = new UiStyle(context); setClipChildren(false);
        grain = new WoodGrainView(context); addView(grain, new LayoutParams(-1, -1));
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(style.dp(24), style.dp(18), style.dp(15), style.dp(18));
        addView(content, new LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(context); copy.setOrientation(LinearLayout.VERTICAL);
        title = style.serif("", 23, 0, false, 400); title.setLineSpacing(0, 1.15f);
        copy.addView(title);
        marker = style.serif("", 16, 0, true, 300);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(-1, -2);
        markerParams.topMargin = style.dp(3); copy.addView(marker, markerParams);
        softTime = style.sans("", 15, 0, false); copy.addView(softTime, markerParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        dot = new DewDotView(context); row.addView(dot,
                new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        menu = style.sans("⋮", 20, 0, false); menu.setGravity(Gravity.CENTER);
        menu.setContentDescription(context.getString(R.string.content_task_menu));
        row.addView(menu, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        content.addView(row);
        progress = new LinearLayout(context); progress.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2); pp.topMargin = style.dp(10);
        content.addView(progress, pp);
        progressLabel = style.sans("", 14, 0, false);
    }

    public void bind(TaskSnapshot task, String markerText, boolean deep, DayPalette palette,
                     Consumer<TaskSnapshot> complete, Consumer<TaskSnapshot> showMenu) {
        setBackground(style.leaf(deep ? palette.leaf3 : palette.leaf2,
                style.edge(palette, deep ? 3 : 2), 56, 8, 56, 8));
        setRotation(deep ? 1.5f : 1.1f); style.shadow(this, palette, deep ? 5 : 7, deep ? .6f : .7f);
        title.setText(task.done ? strike(task.title) : breakable(task.title));
        title.setTextColor(task.done ? palette.done : palette.ink);
        marker.setText(markerText); marker.setTextColor(task.overdue && !task.done ? palette.bad : palette.muted);
        marker.setVisibility(markerText.isEmpty() ? GONE : VISIBLE);
        softTime.setText(task.softTime); softTime.setTextColor(palette.hint);
        softTime.setVisibility(task.softTime.isEmpty() || task.done || task.overdue ? GONE : VISIBLE);
        int value = task.done ? task.awardedXp : task.claimableXp;
        dot.bind(task.done, false, palette, value);
        dot.setEnabled(!task.occurrenceId.isEmpty());
        dot.setContentDescription((task.done ? getContext().getString(R.string.marker_done) + ": " : "")
                + task.title + ", " + value + " XP");
        dot.setOnClickListener(view -> complete.accept(task));
        menu.setVisibility(task.done ? GONE : VISIBLE); menu.setTextColor(palette.dot);
        menu.setOnClickListener(view -> showMenu.accept(task));
        bindProgress(task, palette);
        post(() -> grain.bind(palette, Collections.singletonList(
                new WoodGrainView.Anchor(dot, task.comboStage))));
    }

    private void bindProgress(TaskSnapshot task, DayPalette palette) {
        boolean visible = task.steps.size() > 1 && !task.done;
        progress.setVisibility(visible ? VISIBLE : GONE); if (!visible) return;
        while (bars.size() < task.steps.size()) {
            View bar = new View(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(style.dp(22), style.dp(5));
            params.rightMargin = style.dp(8); progress.addView(bar, params); bars.add(bar);
        }
        while (bars.size() > task.steps.size()) progress.removeView(bars.remove(bars.size() - 1));
        if (progressLabel.getParent() == null) progress.addView(progressLabel);
        int complete = 0;
        for (int i = 0; i < task.steps.size(); i++) {
            boolean done = task.steps.get(i).done; if (done) complete++;
            bars.get(i).setBackground(style.pill(done ? palette.accent : UiStyle.alpha(palette.dot, .4f), 3));
        }
        progressLabel.setText(getResources().getQuantityString(R.plurals.step_progress,
                task.steps.size(), complete, task.steps.size())); progressLabel.setTextColor(palette.muted);
    }

    private static CharSequence strike(String text) {
        SpannableString value = new SpannableString(text);
        value.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }
    private static String breakable(String text) { return text.replace("-", "-\u200b"); }
}

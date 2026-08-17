package de.thonktank.autosecretary;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TaskLeafView extends LinearLayout {
    private final UiStyle style;
    private final TextView marker;
    private final TextView title;
    private final TextView softTime;
    private final DewDotView dot;
    private final TextView menu;
    private final LayoutParams dotParams;
    private final LayoutParams menuParams;
    private final LinearLayout progress;
    private final List<View> bars = new ArrayList<>();
    private final TextView progressLabel;

    public TaskLeafView(Context context) {
        super(context);
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setPadding(style.dp(24), style.dp(18), style.dp(24), style.dp(18));
        marker = style.serif("", 16, 0, true, 300);
        addView(marker);
        LinearLayout row = new LinearLayout(context);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        title = style.serif("", 23, 0, false, 400);
        title.setLineSpacing(0, 1.15f);
        LayoutParams titleParams = new LayoutParams(0, -2, 1);
        titleParams.setMargins(0, 0, style.dp(2), 0);
        row.addView(title, titleParams);
        dot = new DewDotView(context);
        dotParams = new LayoutParams(style.dp(48), style.dp(48));
        dotParams.topMargin = -style.dp(11);
        dotParams.bottomMargin = -style.dp(11);
        row.addView(dot, dotParams);
        menu = style.sans("⋮", 20, 0, false);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription(context.getString(R.string.content_task_menu));
        menuParams = new LayoutParams(style.dp(48), style.dp(48));
        menuParams.rightMargin = -style.dp(9);
        menuParams.topMargin = -style.dp(9);
        menuParams.bottomMargin = -style.dp(9);
        row.addView(menu, menuParams);
        LayoutParams rowParams = new LayoutParams(-1, -2);
        rowParams.topMargin = style.dp(1);
        addView(row, rowParams);
        softTime = style.sans("", 15, 0, false);
        LayoutParams softParams = new LayoutParams(-1, -2);
        softParams.topMargin = style.dp(2);
        addView(softTime, softParams);
        progress = new LinearLayout(context);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams progressParams = new LayoutParams(-1, -2);
        progressParams.setMargins(0, style.dp(10), 0, 0);
        addView(progress, progressParams);
        progressLabel = style.serif("", 14, 0, true, 300);
    }

    public void bind(TaskSnapshot task, String markerText, boolean deep, DayPalette palette,
                     Consumer<TaskSnapshot> complete, Consumer<TaskSnapshot> showMenu) {
        setBackground(style.leaf(deep ? palette.leaf3 : palette.leaf2,
                style.edge(palette, deep ? 3 : 2), 56, 8, 56, 8));
        setRotation(deep ? 1.5f : 1.1f);
        style.shadow(this, palette, deep ? 5 : 7, deep ? .6f : .7f);
        marker.setText(markerText);
        marker.setTextColor(task.overdue && !task.done ? palette.bad : palette.muted);
        title.setText(task.done ? strike(task.title) : breakable(task.title));
        title.setTextSize(21);
        title.setTextColor(task.done ? palette.ink2 : palette.ink);
        dot.bind(task.done, task.done, palette);
        dot.setEnabled(!task.done);
        dot.setContentDescription((task.done ? getContext().getString(R.string.marker_done) : "") + task.title);
        dot.setOnClickListener(task.done ? null : view -> complete.accept(task));
        menu.setVisibility(task.done ? GONE : VISIBLE);
        dotParams.rightMargin = -style.dp(task.done ? 11 : 7);
        dot.setLayoutParams(dotParams);
        menu.setTextColor(palette.dot);
        menu.setOnClickListener(view -> showMenu.accept(task));
        softTime.setText(task.softTime);
        softTime.setTextColor(palette.hint);
        softTime.setVisibility(task.softTime.isEmpty() || task.done ? GONE : VISIBLE);
        bindProgress(task, palette);
    }

    private void bindProgress(TaskSnapshot task, DayPalette palette) {
        boolean visible = task.steps.size() > 1 && !task.done;
        progress.setVisibility(visible ? VISIBLE : GONE);
        if (!visible) return;
        while (bars.size() < task.steps.size()) {
            View bar = new View(getContext());
            LayoutParams params = new LayoutParams(style.dp(22), style.dp(5));
            params.setMargins(0, 0, style.dp(8), 0);
            progress.addView(bar, Math.max(0, progress.getChildCount() - 1), params);
            bars.add(bar);
        }
        while (bars.size() > task.steps.size()) {
            View removed = bars.remove(bars.size() - 1);
            progress.removeView(removed);
        }
        if (progressLabel.getParent() == null) {
            LayoutParams labelParams = new LayoutParams(-2, -2);
            labelParams.setMargins(style.dp(4), 0, 0, 0);
            progress.addView(progressLabel, labelParams);
        }
        int complete = 0;
        for (int i = 0; i < task.steps.size(); i++) {
            boolean done = task.steps.get(i).done;
            if (done) complete++;
            bars.get(i).setBackground(style.pill(done ? palette.accent
                    : UiStyle.alpha(palette.dot, .4f), 3));
        }
        progressLabel.setText(getResources().getQuantityString(R.plurals.step_progress,
                task.steps.size(), complete, task.steps.size()));
        progressLabel.setTextColor(palette.muted);
    }

    private static CharSequence strike(String text) {
        SpannableString value = new SpannableString(text);
        value.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return value;
    }

    private static String breakable(String text) {
        return text.replace("-", "-\u200b");
    }
}

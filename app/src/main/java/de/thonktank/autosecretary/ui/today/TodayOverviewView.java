package de.thonktank.autosecretary.ui.today;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.AccessibilityRoles;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.presentation.today.TimelineItemUiModel;
import de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.ui.leaf.LeafShape;

import java.util.HashMap;
import java.util.Map;

/** Compact task leaves with an independent day-only action on each row. */
public final class TodayOverviewView extends LinearLayout {
    private static final LeafShape LEAF = new LeafShape(6, 28, 6, 25);
    private static final LeafShape REVERSED = new LeafShape(28, 6, 25, 6);
    private final UiStyle style;
    private final TodayActionSink actions;
    private final LinearLayout timeline;
    private final LinearLayout hiddenRows;
    private final TextView count;
    private final TextView hiddenToggle;
    private final Map<String, TaskRow> rows = new HashMap<>();
    private final java.util.function.Consumer<TimelineTaskUiModel> onHide;
    private boolean hiddenExpanded;
    private TodayUiModel bound;
    private DayPalette palette;

    public TodayOverviewView(Context context, TodayActionSink actions,
                             java.util.function.Consumer<TimelineTaskUiModel> onHide) {
        super(context);
        this.actions = actions;
        this.onHide = onHide;
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        count = style.sans("", 16, 0, true);
        count.setPadding(style.dp(9), 0, style.dp(8), style.dp(6));
        addView(count);
        timeline = column(context);
        timeline.setId(R.id.dashboard_timeline);
        addView(timeline);
        hiddenToggle = button("", 16);
        hiddenToggle.setGravity(Gravity.CENTER_VERTICAL);
        hiddenToggle.setPadding(style.dp(13), 0, style.dp(13), 0);
        hiddenToggle.setOnClickListener(view -> {
            hiddenExpanded = !hiddenExpanded;
            applyHidden();
        });
        addView(hiddenToggle, new LayoutParams(-1, -2));
        hiddenRows = column(context);
        addView(hiddenRows);
    }

    private LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        return column;
    }

    public void bind(TodayUiModel dashboard, DayPalette palette) {
        if (bound == dashboard && this.palette == palette) return;
        bound = dashboard;
        this.palette = palette;
        int taskCount = dashboard.openTasks.size();
        count.setText(getResources().getQuantityString(R.plurals.today_open_tasks, taskCount, taskCount));
        count.setTextColor(palette.ink2);
        Map<String, TaskRow> retained = new HashMap<>();
        int index = 0;
        for (TimelineItemUiModel item : dashboard.timeline) {
            View row;
            if (item.task != null) {
                String key = item.task.actionTarget.item.key();
                TaskRow taskRow = rows.get(key);
                if (taskRow == null) taskRow = new TaskRow(false);
                taskRow.bind(item.task, palette, index);
                retained.put(key, taskRow);
                row = taskRow;
            } else {
                CalendarLeafView event = new CalendarLeafView(getContext());
                event.bind(item.event, palette);
                row = event;
            }
            place(timeline, row, index++);
        }
        if (dashboard.timeline.isEmpty()) {
            TextView empty = style.sans(getContext().getString(R.string.today_no_open_work),
                    16, palette.hint, false);
            empty.setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12));
            empty.setBackground(LEAF.drawable(getContext(), palette.leaf1, palette.leaf1Edge, 1));
            place(timeline, empty, index++);
        }
        while (timeline.getChildCount() > index)
            timeline.removeViewAt(timeline.getChildCount() - 1);
        rows.clear();
        rows.putAll(retained);
        hiddenRows.removeAllViews();
        for (TimelineTaskUiModel item : dashboard.hiddenTasks) {
            TaskRow row = new TaskRow(true);
            row.bind(item, palette, hiddenRows.getChildCount());
            place(hiddenRows, row, hiddenRows.getChildCount());
        }
        hiddenToggle.setText(getContext().getString(R.string.today_hidden_group, dashboard.hiddenTasks.size()));
        hiddenToggle.setTextColor(palette.ink2);
        hiddenToggle.setBackground(REVERSED.drawable(getContext(), palette.leaf2, palette.leaf2Edge, 1));
        hiddenToggle.setVisibility(dashboard.hiddenTasks.isEmpty() ? GONE : VISIBLE);
        applyHidden();
    }

    private void place(LinearLayout parent, View row, int index) {
        if (parent.indexOfChild(row) == index) return;
        if (row.getParent() == parent) parent.removeView(row);
        LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = style.dp(5);
        parent.addView(row, index, params);
    }

    private void applyHidden() {
        hiddenRows.setVisibility(hiddenExpanded ? VISIBLE : GONE);
        hiddenToggle.setSelected(hiddenExpanded);
        hiddenToggle.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                hiddenExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
        hiddenToggle.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(palette.hint));
    }

    private TextView button(String label, int size) {
        TextView button = style.sans(label, size, 0, false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(style.dp(48));
        AccessibilityRoles.button(button);
        return button;
    }

    private final class TaskRow extends LinearLayout {
        private final LinearLayout titleTarget;
        private final TextView title;
        private final TextView progress;
        private final TextView chevron;
        private final TextView visibility;
        private final boolean hidden;

        TaskRow(boolean hidden) {
            super(TodayOverviewView.this.getContext());
            this.hidden = hidden;
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(style.dp(60));
            setPadding(style.dp(12), 0, style.dp(8), 0);
            titleTarget = new LinearLayout(getContext());
            titleTarget.setGravity(Gravity.CENTER_VERTICAL);
            titleTarget.setMinimumHeight(style.dp(48));
            titleTarget.setPadding(0, style.dp(7), style.dp(6), style.dp(7));
            title = style.serif("", 21, 0, false, 400);
            progress = style.sans("", 15, 0, false);
            LinearLayout labels = column(getContext());
            labels.addView(title);
            labels.addView(progress);
            titleTarget.addView(labels, new LayoutParams(0, -2, 1));
            chevron = style.sans("›", 23, 0, false);
            chevron.setGravity(Gravity.CENTER);
            chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            chevron.setVisibility(hidden ? GONE : VISIBLE);
            titleTarget.addView(chevron, new LayoutParams(style.dp(22), -2));
            addView(titleTarget, new LayoutParams(0, -2, 1));
            visibility = button(getContext().getString(
                    hidden ? R.string.today_restore : R.string.today_hide), 15);
            visibility.setPadding(style.dp(7), 0, style.dp(5), 0);
            addView(visibility, new LayoutParams(-2, -2));
            if (!hidden) {
                AccessibilityRoles.button(titleTarget);
                title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }

        void bind(TimelineTaskUiModel item, DayPalette palette, int index) {
            setBackground((index % 2 == 0 ? LEAF : REVERSED).drawable(getContext(),
                    index % 2 == 0 ? palette.leaf1 : palette.leaf2,
                    index % 2 == 0 ? palette.leaf1Edge : palette.leaf2Edge, 1));
            title.setText(item.title);
            title.setTextColor(palette.ink);
            int done = 0;
            for (var step : item.steps) if (step.done) done++;
            String status = item.steps.isEmpty() ? "" : getContext().getString(
                    R.string.today_progress, done, item.steps.size());
            chevron.setTextColor(palette.ink2);
            if (item.overdue) {
                String overdue = getContext().getString(R.string.marker_overdue);
                status = status.isEmpty() ? overdue : overdue + " · " + status;
            }
            progress.setText(status);
            progress.setTextColor(item.overdue ? palette.bad : palette.hint);
            progress.setVisibility(!hidden && !status.isEmpty() ? VISIBLE : GONE);
            if (!hidden) {
                String description = getContext().getString(R.string.today_open_task, item.title);
                titleTarget.setContentDescription(status.isEmpty() ? description : description + ", " + status);
                titleTarget.setOnClickListener(view -> actions.emit(TodayAction.selectTask(item.actionTarget)));
                titleTarget.setOnLongClickListener(view -> {
                    actions.emit(TodayAction.openTaskMenu(item.actionTarget));
                    return true;
                });
            }
            visibility.setTextColor(palette.hint);
            visibility.setContentDescription(getContext().getString(
                    hidden ? R.string.today_restore_task : R.string.today_hide_task, item.title));
            visibility.setOnClickListener(view -> {
                if (hidden) actions.emit(TodayAction.restoreTask(item.actionTarget));
                else onHide.accept(item);
            });
        }
    }
}

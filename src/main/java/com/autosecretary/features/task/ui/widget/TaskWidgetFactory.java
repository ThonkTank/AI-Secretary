package com.autosecretary.features.task.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implements {@link RemoteViewsService.RemoteViewsFactory} to provide remote views for the
 * widget's list adapter. Converts {@link TaskListItem}s to {@link RemoteViews} with layout
 * binding, click intent configuration, and visual state (colors, streak, completion status).
 *
 * <p><b>Key behavior:</b> Only today's uncompleted scheduled items get interactive
 * checkboxes. Past/future days are read-only. See {@link README.md} for design rationale.
 *
 * <p>Data flow:
 * <ol>
 *   <li>{@link TaskWidgetService} creates this factory on-demand (called by Android framework)</li>
 *   <li>{@code onDataSetChanged()} fetches all tasks, filters to selected date, sorts by time</li>
 *   <li>Widget framework calls {@code getViewAt(int)} to render each row</li>
 *   <li>Row click intents are configured with fill-in data (task ID, slot ID)</li>
 * </ol>
 */
public class TaskWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    // Widget displays times in 24-hour format (e.g. "14:30")
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Context context;
    private final TaskDao taskDao;
    private final int colorInProgress;
    private final int colorCompleted;
    private final int colorDefault;
    private final TaskListItemMapper mapper = new TaskListItemMapper();
    /** Filtered list of items scheduled for the currently selected date, sorted by start time. */
    private List<TaskListItem> items = new ArrayList<>();
    /** True when the widget's selected date is today; gates interactive checkboxes (past/future = read-only). */
    private boolean isToday;

    public TaskWidgetFactory(Context context, TaskDao taskDao) {
        this.context = context;
        this.taskDao = taskDao;
        this.colorInProgress = ContextCompat.getColor(context, R.color.task_widget_title_in_progress);
        this.colorCompleted = ContextCompat.getColor(context, R.color.task_widget_title_completed);
        this.colorDefault = ContextCompat.getColor(context, R.color.task_widget_title_default);
    }

    @Override
    public void onCreate() {
        // Data loaded in onDataSetChanged
    }

    @Override
    public void onDataSetChanged() {
        List<Task> tasks = taskDao.readAll();
        List<TaskListItem> allItems = mapper.map(tasks);

        LocalDate selectedDate = TaskWidgetProvider.getSelectedDate(context);
        isToday = selectedDate.equals(LocalDate.now());

        List<TaskListItem> filtered = new ArrayList<>();
        for (TaskListItem item : allItems) {
            if (item.isScheduledOn(selectedDate)) {
                filtered.add(item);
            }
        }
        filtered.sort(Comparator.comparing(i -> i.start, Comparator.nullsLast(Comparator.naturalOrder())));
        items = filtered;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }

        TaskListItem item = items.get(position);
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.task_row_widget);

        rv.setTextViewText(R.id.WidgetRowStart, item.start != null ? item.start.format(TIME_FORMATTER) : "");
        rv.setTextViewText(R.id.WidgetRowEnd, item.end != null ? item.end.format(TIME_FORMATTER) : "");
        rv.setTextViewText(R.id.WidgetRowTitle, item.title);

        // Streak: count of consecutive periods in which the task was completed.
        // Only shown if > 0, as a motivation badge. See CLAUDE.md glossary for details.
        if (item.streak > 0) {
            rv.setTextViewText(R.id.WidgetRowStreak, item.streak + "x");
            rv.setViewVisibility(R.id.WidgetRowStreak, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.WidgetRowStreak, View.GONE);
        }

        rv.setCompoundButtonChecked(R.id.WidgetRowCheckbox, item.completed);

        // Only today's uncompleted scheduled tasks can be toggled via widget.
        // Past/future dates are for viewing only. Checkbox is disabled to prevent
        // accidental updates outside the current day context.
        boolean isInteractive = isToday && item.slotId != null && !item.completed;

        if (isInteractive) {
            // Interactive: set fill-in intent for checkbox toggle
            Intent fillIn = new Intent();
            fillIn.putExtra(TaskWidgetProvider.EXTRA_ACTION, TaskWidgetProvider.ACTION_TOGGLE);
            fillIn.putExtra(TaskWidgetProvider.EXTRA_TASK_ID, item.taskId);
            fillIn.putExtra(TaskWidgetProvider.EXTRA_SLOT_ID, item.slotId);
            rv.setOnClickFillInIntent(R.id.WidgetRowCheckbox, fillIn);
        }

        // Visual state for non-interactive days or completed items
        rv.setBoolean(R.id.WidgetRowCheckbox, "setEnabled", isInteractive);

        // Text color indicates task state: in-progress (realStart set) shows one color,
        // completed (realEnd set) shows another, and unstarted/default state shows default color.
        // Colors are defined in R.color.task_widget_title_* (see task_colors.xml).
        int textColor = item.inProgress ? colorInProgress
                      : item.completed ? colorCompleted
                      : colorDefault;
        rv.setInt(R.id.WidgetRowTitle, "setTextColor", textColor);

        return rv;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public void onDestroy() {
        items.clear();
    }
}

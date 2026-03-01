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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    // Reusable comparator for sorting tasks by start time; avoids allocating new instances on every refresh
    private static final Comparator<TaskListItem> BY_START_TIME =
            Comparator.comparing(i -> i.start, Comparator.nullsLast(Comparator.naturalOrder()));

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

    /**
     * Loads all tasks from the database, filters to the widget's selected date, and sorts
     * by start time. Filtering is done in-memory (no date-scoped DAO query exists yet).
     */
    @Override
    public void onDataSetChanged() {
        List<Task> tasks = taskDao.readAll();
        List<TaskListItem> allItems = mapper.map(tasks);

        LocalDate selectedDate = TaskWidgetProvider.getSelectedDate(context);
        isToday = selectedDate.equals(LocalDate.now());

        items = allItems.stream()
            .filter(item -> item.isScheduledOn(selectedDate))
            .sorted(BY_START_TIME)
            .collect(Collectors.toList());
    }

    @Override
    public int getCount() {
        return items.size();
    }

    /**
     * Formats a time for display in 24-hour format, or returns empty string if time is null.
     */
    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : "";
    }

    /**
     * Returns the text color for a task based on its state: in-progress, completed, or default.
     */
    private int getTaskStateTextColor(TaskListItem item) {
        if (item.inProgress) return colorInProgress;
        if (item.completed) return colorCompleted;
        return colorDefault;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }

        TaskListItem item = items.get(position);
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.task_row_widget);

        rv.setTextViewText(R.id.WidgetRowStart, formatTime(item.start));
        rv.setTextViewText(R.id.WidgetRowEnd, formatTime(item.end));
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
        rv.setContentDescription(R.id.WidgetRowCheckbox,
                context.getString(R.string.task_widget_checkbox_content_description, item.title));

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
        int textColor = getTaskStateTextColor(item);
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

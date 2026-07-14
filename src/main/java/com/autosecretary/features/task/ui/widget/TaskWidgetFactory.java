package com.autosecretary.features.task.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;
import com.autosecretary.features.task.application.LoadTaskWidgetItemsUseCase;
import com.autosecretary.features.task.application.listmodel.TaskListItem;

import com.autosecretary.shared.DateFormatters;

import java.time.LocalTime;
import java.util.ArrayList;
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
 *   <li>{@code onDataSetChanged()} loads the widget read model through {@link LoadTaskWidgetItemsUseCase}</li>
 *   <li>Widget framework calls {@code getViewAt(int)} to render each row</li>
 *   <li>Row click intents are configured with fill-in data (task ID, slot ID)</li>
 * </ol>
 */
public class TaskWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final LoadTaskWidgetItemsUseCase loadTaskWidgetItemsUseCase;
    private final int colorInProgress;
    private final int colorCompleted;
    private final int colorDefault;
    /** Flat, priority-sorted list of open tasks for the selected category filter. */
    private List<TaskListItem> items = new ArrayList<>();

    public TaskWidgetFactory(Context context, LoadTaskWidgetItemsUseCase loadTaskWidgetItemsUseCase) {
        this.context = context;
        this.loadTaskWidgetItemsUseCase = loadTaskWidgetItemsUseCase;
        this.colorInProgress = ContextCompat.getColor(context, R.color.task_widget_title_in_progress);
        this.colorCompleted = ContextCompat.getColor(context, R.color.task_widget_title_completed);
        this.colorDefault = ContextCompat.getColor(context, R.color.task_widget_title_default);
    }

    @Override
    public void onCreate() {
        // Data loaded in onDataSetChanged
    }

    /**
     * Loads the widget read model for the selected category filter through the application layer.
     */
    @Override
    public void onDataSetChanged() {
        String selectedCategoryId = TaskWidgetProvider.getSelectedCategoryId(context);
        items = new ArrayList<>(loadTaskWidgetItemsUseCase.execute(selectedCategoryId));
    }

    @Override
    public int getCount() {
        return items.size();
    }

    /**
     * Formats a time for display in 24-hour format, or returns empty string if time is null.
     */
    private String formatTime(LocalTime time) {
        return time != null ? time.format(DateFormatters.TIME_HH_MM) : "";
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
        if (item.streak > 0 && !item.leisure) {
            rv.setTextViewText(R.id.WidgetRowStreak, item.streak + "x");
            rv.setViewVisibility(R.id.WidgetRowStreak, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.WidgetRowStreak, View.GONE);
        }

        rv.setCompoundButtonChecked(R.id.WidgetRowCheckbox, item.completed);
        int checkboxDescRes;
        if (item.completed) {
            checkboxDescRes = R.string.task_row_checkbox_done;
        } else if (item.inProgress) {
            checkboxDescRes = R.string.task_row_checkbox_complete;
        } else {
            checkboxDescRes = R.string.task_row_checkbox_start;
        }
        rv.setContentDescription(R.id.WidgetRowCheckbox, context.getString(checkboxDescRes));

        // A task can be checked off from the widget only when it has a concrete slot to toggle
        // and is not already completed. Unscheduled tasks show a disabled checkbox.
        boolean isInteractive = item.slotId != null && !item.completed;

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

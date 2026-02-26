package com.autosecretary.features.task.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.task.application.listmodel.TaskListItemMapper;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TaskWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final Context context;
    private final int colorInProgress;
    private final int colorCompleted;
    private final int colorDefault;
    private List<TaskListItem> items = new ArrayList<>();
    private boolean isToday;

    public TaskWidgetFactory(Context context) {
        this.context = context;
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
        AppDatabase db = AppDatabase.getInstance(context);
        TaskDAO dao = db.taskDao();
        List<Task> tasks = dao.readAll();
        TaskListItemMapper mapper = new TaskListItemMapper();
        List<TaskListItem> allItems = mapper.map(tasks);

        LocalDate selectedDate = TaskWidgetProvider.getSelectedDate(context);
        isToday = selectedDate.equals(LocalDate.now());

        List<TaskListItem> filtered = new ArrayList<>();
        for (TaskListItem item : allItems) {
            if (selectedDate.equals(item.day) && item.start != null) {
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

        rv.setTextViewText(R.id.widget_row_start, item.start != null ? item.start.format(TIME_FORMAT) : "");
        rv.setTextViewText(R.id.widget_row_end, item.end != null ? item.end.format(TIME_FORMAT) : "");
        rv.setTextViewText(R.id.widget_row_title, item.title);

        if (item.streak > 0) {
            rv.setTextViewText(R.id.widget_row_streak, item.streak + "x");
            rv.setViewVisibility(R.id.widget_row_streak, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.widget_row_streak, View.GONE);
        }

        rv.setCompoundButtonChecked(R.id.widget_row_checkbox, item.completed);

        if (isToday && item.slotId != null && !item.completed) {
            // Interactive: set fill-in intent for checkbox toggle
            Intent fillIn = new Intent();
            fillIn.putExtra(TaskWidgetProvider.EXTRA_ACTION, TaskWidgetProvider.ACTION_TOGGLE);
            fillIn.putExtra(TaskWidgetProvider.EXTRA_TASK_ID, item.taskId);
            fillIn.putExtra(TaskWidgetProvider.EXTRA_SLOT_ID, item.slotId);
            rv.setOnClickFillInIntent(R.id.widget_row_checkbox, fillIn);
        }

        // Visual state for non-interactive days or completed items
        if (!isToday || item.completed || item.slotId == null) {
            // RemoteViews doesn't support setAlpha on CheckBox, but we can disable via enabled state
            rv.setBoolean(R.id.widget_row_checkbox, "setEnabled", false);
        } else {
            rv.setBoolean(R.id.widget_row_checkbox, "setEnabled", true);
        }

        // In-progress visual hint
        if (item.inProgress) {
            rv.setInt(R.id.widget_row_title, "setTextColor", colorInProgress);
        } else if (item.completed) {
            rv.setInt(R.id.widget_row_title, "setTextColor", colorCompleted);
        } else {
            rv.setInt(R.id.widget_row_title, "setTextColor", colorDefault);
        }

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

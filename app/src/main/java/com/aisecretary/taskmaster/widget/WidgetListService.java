package com.aisecretary.taskmaster.widget;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WidgetListService - Provides data for widget's ListView
 *
 * Phase 4.5.1: Large Widget - Today's Tasks List
 * Uses RemoteViewsFactory to populate the widget's task list.
 */
public class WidgetListService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new WidgetListRemoteViewsFactory(this.getApplicationContext());
    }

    /**
     * RemoteViewsFactory for today's tasks list
     */
    private static class WidgetListRemoteViewsFactory implements RemoteViewsFactory {

        private Context context;
        private List<TaskEntity> tasks = new ArrayList<>();
        private TaskRepository repository;

        public WidgetListRemoteViewsFactory(Context context) {
            this.context = context;
            this.repository = TaskRepository.getInstance(context);
        }

        @Override
        public void onCreate() {
            // Initial load
            loadTasks();
        }

        @Override
        public void onDataSetChanged() {
            // Reload data when widget is refreshed
            loadTasks();
        }

        @Override
        public void onDestroy() {
            tasks.clear();
        }

        @Override
        public int getCount() {
            return Math.min(tasks.size(), 5); // Show max 5 tasks in widget
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= tasks.size()) {
                return null;
            }

            TaskEntity task = tasks.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task_item);

            // Checkbox
            views.setTextViewText(R.id.widget_task_checkbox, task.completed ? "☑" : "☐");

            // Task title
            views.setTextViewText(R.id.widget_task_title, task.title);

            // Task details (priority + time)
            String details = getPriorityStars(task.priority);
            if (task.dueAt > 0) {
                details += " • " + formatTime(task.dueAt);
            }
            views.setTextViewText(R.id.widget_task_details, details);

            // Streak badge
            if (task.isRecurring && task.currentStreak > 0) {
                views.setTextViewText(R.id.widget_task_streak, "🔥" + task.currentStreak);
                views.setViewVisibility(R.id.widget_task_streak, RemoteViews.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widget_task_streak, RemoteViews.GONE);
            }

            // Click handling - fill in Intent for the item
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(TaskWidgetProvider.EXTRA_TASK_ID, task.id);
            views.setOnClickFillInIntent(R.id.widget_task_checkbox, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null; // Use default loading view
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            if (position < tasks.size()) {
                return tasks.get(position).id;
            }
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        /**
         * Load today's tasks from repository
         */
        private void loadTasks() {
            List<TaskEntity> allTasks = repository.getTodayTasks();
            tasks.clear();

            // Limit to top 5 tasks
            int count = Math.min(allTasks.size(), 5);
            for (int i = 0; i < count; i++) {
                tasks.add(allTasks.get(i));
            }
        }

        /**
         * Get priority stars
         */
        private String getPriorityStars(int priority) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < priority; i++) {
                stars.append("⭐");
            }
            return stars.toString();
        }

        /**
         * Format time in HH:mm format
         */
        private String formatTime(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}

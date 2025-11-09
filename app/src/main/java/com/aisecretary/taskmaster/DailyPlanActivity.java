package com.aisecretary.taskmaster;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aisecretary.taskmaster.R;
import com.aisecretary.taskmaster.database.TaskEntity;
import com.aisecretary.taskmaster.repository.TaskRepository;
import com.aisecretary.taskmaster.utils.TaskScheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DailyPlanActivity - Shows timeline-based daily task plan
 *
 * Features:
 * - Timeline view with suggested start times
 * - Based on intelligent task sorting (TaskScheduler)
 * - Considers estimated duration and preferred time of day
 * - Highlights next task
 * - Phase 5.2
 */
public class DailyPlanActivity extends AppCompatActivity {

    private LinearLayout timelineContainer;
    private TextView headerText;
    private TaskRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create layout programmatically
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);

        // Header
        headerText = new TextView(this);
        headerText.setText("📅 Dein Tagesplan");
        headerText.setTextSize(24);
        headerText.setTextColor(Color.parseColor("#6200EE"));
        headerText.setPadding(0, 0, 0, 32);
        mainLayout.addView(headerText);

        // Timeline container
        timelineContainer = new LinearLayout(this);
        timelineContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(timelineContainer);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        // Initialize repository
        repository = TaskRepository.getInstance(this);

        // Generate and display plan
        generateDailyPlan();
    }

    /**
     * Generate daily plan with timeline
     */
    private void generateDailyPlan() {
        // Get today's sorted tasks
        List<TaskEntity> tasks = repository.getTodaysSortedTasks();

        if (tasks.isEmpty()) {
            showEmptyState();
            return;
        }

        // Generate timeline entries
        List<TimelineEntry> timeline = generateTimeline(tasks);

        // Display timeline
        for (int i = 0; i < timeline.size(); i++) {
            TimelineEntry entry = timeline.get(i);
            boolean isNext = i == 0 && !entry.task.completed;
            addTimelineItem(entry, isNext);
        }

        // Add summary footer
        addSummaryFooter(timeline);
    }

    /**
     * Generate timeline entries with start times
     */
    private List<TimelineEntry> generateTimeline(List<TaskEntity> tasks) {
        List<TimelineEntry> timeline = new ArrayList<>();

        // Start with current time or next full hour
        Calendar calendar = Calendar.getInstance();
        int currentMinute = calendar.get(Calendar.MINUTE);
        if (currentMinute > 0) {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
            calendar.set(Calendar.MINUTE, 0);
        }
        calendar.set(Calendar.SECOND, 0);

        long currentSlotStart = calendar.getTimeInMillis();

        for (TaskEntity task : tasks) {
            if (task.completed) {
                // Already completed - show completion time
                TimelineEntry entry = new TimelineEntry();
                entry.task = task;
                entry.startTime = task.completedAt;
                entry.endTime = task.completedAt;
                entry.isCompleted = true;
                timeline.add(entry);
            } else {
                // Not completed - suggest time slot
                TimelineEntry entry = new TimelineEntry();
                entry.task = task;

                // Consider preferred time of day
                long suggestedStart = suggestStartTime(task, currentSlotStart);

                entry.startTime = suggestedStart;

                // Calculate end time based on estimated duration
                long duration = task.estimatedDuration > 0
                        ? task.estimatedDuration
                        : 1800000; // Default 30 minutes

                entry.endTime = suggestedStart + duration;
                entry.isCompleted = false;

                timeline.add(entry);

                // Next slot starts after this task + 15min break
                currentSlotStart = entry.endTime + 900000; // 15 min break
            }
        }

        return timeline;
    }

    /**
     * Suggest start time considering preferred time of day
     */
    private long suggestStartTime(TaskEntity task, long defaultStart) {
        if (task.preferredTimeOfDay == null || task.preferredTimeOfDay.isEmpty()) {
            return defaultStart;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(defaultStart);

        int targetHour = -1;

        switch (task.preferredTimeOfDay) {
            case "morning":
                targetHour = 9; // 9 AM
                break;
            case "afternoon":
                targetHour = 14; // 2 PM
                break;
            case "evening":
                targetHour = 18; // 6 PM
                break;
            case "night":
                targetHour = 21; // 9 PM
                break;
        }

        if (targetHour >= 0) {
            int currentHour = cal.get(Calendar.HOUR_OF_DAY);

            // If preferred time hasn't passed yet, suggest it
            if (currentHour < targetHour) {
                cal.set(Calendar.HOUR_OF_DAY, targetHour);
                cal.set(Calendar.MINUTE, 0);
                return cal.getTimeInMillis();
            }
        }

        return defaultStart;
    }

    /**
     * Add timeline item to UI
     */
    private void addTimelineItem(TimelineEntry entry, boolean isNext) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 24, 0, 24);

        // Timeline indicator
        LinearLayout timelineIndicator = new LinearLayout(this);
        timelineIndicator.setOrientation(LinearLayout.VERTICAL);
        timelineIndicator.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.MATCH_PARENT);
        timelineIndicator.setLayoutParams(indicatorParams);

        // Dot
        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(24, 24);
        dot.setLayoutParams(dotParams);

        if (entry.isCompleted) {
            dot.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
        } else if (isNext) {
            dot.setBackgroundColor(Color.parseColor("#FF6B00")); // Orange
        } else {
            dot.setBackgroundColor(Color.parseColor("#757575")); // Grey
        }

        timelineIndicator.addView(dot);

        // Line
        View line = new View(this);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(4, LinearLayout.LayoutParams.MATCH_PARENT);
        lineParams.topMargin = 8;
        line.setLayoutParams(lineParams);
        line.setBackgroundColor(Color.parseColor("#E0E0E0"));
        timelineIndicator.addView(line);

        itemLayout.addView(timelineIndicator);

        // Content
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        contentLayout.setLayoutParams(contentParams);
        contentLayout.setPadding(24, 0, 0, 0);

        // Time label
        TextView timeText = new TextView(this);
        String timeLabel;

        if (entry.isCompleted) {
            timeLabel = "✓ " + formatTime(entry.startTime);
        } else if (isNext) {
            timeLabel = "➤ " + formatTime(entry.startTime) + " - " + formatTime(entry.endTime);
        } else {
            timeLabel = formatTime(entry.startTime) + " - " + formatTime(entry.endTime);
        }

        timeText.setText(timeLabel);
        timeText.setTextSize(14);
        timeText.setTextColor(entry.isCompleted ? Color.parseColor("#4CAF50") : Color.parseColor("#757575"));

        if (isNext) {
            timeText.setTextColor(Color.parseColor("#FF6B00"));
            timeText.setTextSize(16);
        }

        contentLayout.addView(timeText);

        // Task title
        TextView titleText = new TextView(this);
        titleText.setText(entry.task.getPriorityStars() + " " + entry.task.title);
        titleText.setTextSize(isNext ? 18 : 16);
        titleText.setTextColor(Color.parseColor("#000000"));
        titleText.setPadding(0, 8, 0, 0);

        if (entry.isCompleted) {
            titleText.setPaintFlags(titleText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            titleText.setTextColor(Color.parseColor("#757575"));
        }

        contentLayout.addView(titleText);

        // Task details
        if (entry.task.description != null && !entry.task.description.isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(entry.task.description);
            descText.setTextSize(14);
            descText.setTextColor(Color.parseColor("#757575"));
            descText.setPadding(0, 4, 0, 0);
            contentLayout.addView(descText);
        }

        // Duration estimate
        if (entry.task.estimatedDuration > 0) {
            TextView durationText = new TextView(this);
            long minutes = entry.task.estimatedDuration / 60000;
            durationText.setText("⏱️ ~" + minutes + " Min");
            durationText.setTextSize(12);
            durationText.setTextColor(Color.parseColor("#9E9E9E"));
            durationText.setPadding(0, 4, 0, 0);
            contentLayout.addView(durationText);
        }

        itemLayout.addView(contentLayout);
        timelineContainer.addView(itemLayout);
    }

    /**
     * Add summary footer
     */
    private void addSummaryFooter(List<TimelineEntry> timeline) {
        if (timeline.isEmpty()) return;

        LinearLayout summaryLayout = new LinearLayout(this);
        summaryLayout.setOrientation(LinearLayout.VERTICAL);
        summaryLayout.setPadding(16, 48, 16, 16);
        summaryLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        TextView summaryTitle = new TextView(this);
        summaryTitle.setText("📊 Zusammenfassung");
        summaryTitle.setTextSize(18);
        summaryTitle.setTextColor(Color.parseColor("#6200EE"));
        summaryLayout.addView(summaryTitle);

        // Calculate totals
        int totalTasks = timeline.size();
        int completedTasks = 0;
        long totalEstimatedTime = 0;

        for (TimelineEntry entry : timeline) {
            if (entry.isCompleted) {
                completedTasks++;
            } else {
                totalEstimatedTime += (entry.endTime - entry.startTime);
            }
        }

        // Summary stats
        TextView statsText = new TextView(this);
        int remainingTasks = totalTasks - completedTasks;
        long hours = totalEstimatedTime / 3600000;
        long minutes = (totalEstimatedTime % 3600000) / 60000;

        String summary = String.format(Locale.getDefault(),
                "• %d / %d Aufgaben erledigt\n• %d Aufgabe%s übrig\n• Geschätzte Zeit: %dh %dmin",
                completedTasks, totalTasks,
                remainingTasks, remainingTasks != 1 ? "n" : "",
                hours, minutes);

        statsText.setText(summary);
        statsText.setTextSize(14);
        statsText.setTextColor(Color.parseColor("#000000"));
        statsText.setPadding(0, 16, 0, 0);
        summaryLayout.addView(statsText);

        // Finish time
        if (!timeline.isEmpty() && remainingTasks > 0) {
            TimelineEntry lastEntry = timeline.get(timeline.size() - 1);
            TextView finishText = new TextView(this);
            finishText.setText("🏁 Voraussichtlich fertig um " + formatTime(lastEntry.endTime));
            finishText.setTextSize(14);
            finishText.setTextColor(Color.parseColor("#4CAF50"));
            finishText.setPadding(0, 8, 0, 0);
            summaryLayout.addView(finishText);
        }

        timelineContainer.addView(summaryLayout);
    }

    /**
     * Show empty state
     */
    private void showEmptyState() {
        TextView emptyText = new TextView(this);
        emptyText.setText("🎉 Keine Aufgaben für heute!\n\nGenieße deinen freien Tag!");
        emptyText.setTextSize(18);
        emptyText.setTextColor(Color.parseColor("#757575"));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(0, 100, 0, 0);
        timelineContainer.addView(emptyText);
    }

    /**
     * Format time for display
     */
    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Timeline entry data class
     */
    private static class TimelineEntry {
        TaskEntity task;
        long startTime;
        long endTime;
        boolean isCompleted;
    }
}

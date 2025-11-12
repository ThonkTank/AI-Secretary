package com.secretary.helloworld.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import com.secretary.helloworld.R;
import com.secretary.helloworld.Task;
import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.data.dao.TaskDao;
import com.secretary.helloworld.data.dao.CompletionDao;
import com.secretary.helloworld.utils.StatsManager;

/**
 * Dialog for completing a task with additional tracking information.
 * Extracted from TaskActivity for better code organization.
 */
public class CompletionDialog {
    private static final String TAG = "CompletionDialog";

    private final Context context;
    private final TaskDao taskDao;
    private final CompletionDao completionDao;
    private final StatsManager statsManager;
    private final AppLogger logger;

    private AlertDialog dialog;
    private CompletionListener listener;

    /**
     * Listener interface for completion events
     */
    public interface CompletionListener {
        void onTaskCompleted(Task task);
        void onCompletionCancelled(Task task);
    }

    public CompletionDialog(Context context, CompletionListener listener) {
        this.context = context;
        this.taskDao = new TaskDao(context);
        this.completionDao = new CompletionDao(context);
        this.statsManager = new StatsManager(context);
        this.logger = AppLogger.getInstance(context);
        this.listener = listener;
    }

    /**
     * Show the completion dialog for a task
     */
    public void show(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_completion, null);
        builder.setView(dialogView);

        dialog = builder.create();

        // Find dialog views
        TextView taskTitleText = dialogView.findViewById(R.id.completionTaskTitle);
        EditText timeSpentInput = dialogView.findViewById(R.id.timeSpentInput);
        TextView averageTimeText = dialogView.findViewById(R.id.averageTimeText);
        SeekBar difficultySeekBar = dialogView.findViewById(R.id.difficultySeekBar);
        TextView difficultyValueText = dialogView.findViewById(R.id.difficultyValueText);
        EditText notesInput = dialogView.findViewById(R.id.completionNotesInput);
        CheckBox quickCompleteCheckBox = dialogView.findViewById(R.id.quickCompleteCheckBox);
        Button skipButton = dialogView.findViewById(R.id.skipDetailsButton);
        Button saveButton = dialogView.findViewById(R.id.saveCompletionButton);

        // Set task title
        taskTitleText.setText(task.getTitle());

        // Show average time if available
        int avgTime = completionDao.getAverageCompletionTime(task.getId());
        if (avgTime > 0) {
            averageTimeText.setText("(Avg: " + avgTime + " min)");
            timeSpentInput.setText(String.valueOf(avgTime));
        }

        // Setup difficulty slider
        difficultySeekBar.setMax(10);
        difficultySeekBar.setProgress(5);
        difficultyValueText.setText("5");

        difficultySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                difficultyValueText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Quick complete checkbox
        quickCompleteCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int visibility = isChecked ? View.GONE : View.VISIBLE;
            timeSpentInput.setVisibility(visibility);
            averageTimeText.setVisibility(visibility);
            difficultySeekBar.setVisibility(visibility);
            difficultyValueText.setVisibility(visibility);
            notesInput.setVisibility(visibility);

            // Find the parent layouts and hide/show them
            View timeLayout = dialogView.findViewById(R.id.timeSpentInput).getParent();
            View difficultyLayout = dialogView.findViewById(R.id.difficultySeekBar).getParent();
            View notesLayout = dialogView.findViewById(R.id.completionNotesInput).getParent();

            if (timeLayout instanceof View) {
                ((View) timeLayout).setVisibility(visibility);
            }
            if (difficultyLayout instanceof View) {
                ((View) difficultyLayout).setVisibility(visibility);
            }
            if (notesLayout instanceof View) {
                ((View) notesLayout).setVisibility(visibility);
            }
        });

        // Skip button - complete without tracking
        skipButton.setOnClickListener(v -> {
            completeTaskBasic(task);
            dialog.dismiss();
        });

        // Save button - complete with tracking
        saveButton.setOnClickListener(v -> {
            if (quickCompleteCheckBox.isChecked()) {
                // Quick complete
                completeTaskBasic(task);
            } else {
                // Complete with tracking
                String timeStr = timeSpentInput.getText().toString().trim();
                int timeSpent = 0;
                try {
                    timeSpent = Integer.parseInt(timeStr);
                } catch (NumberFormatException e) {
                    // Keep as 0
                }

                int difficulty = difficultySeekBar.getProgress();
                String notes = notesInput.getText().toString().trim();

                completeTaskWithTracking(task, timeSpent, difficulty, notes);
            }
            dialog.dismiss();
        });

        // Handle dialog cancel
        dialog.setOnCancelListener(dialogInterface -> {
            if (listener != null) {
                listener.onCompletionCancelled(task);
            }
        });

        dialog.show();
    }

    /**
     * Complete task without tracking
     */
    private void completeTaskBasic(Task task) {
        task.setCompleted(true);
        task.setLastCompletedDate(System.currentTimeMillis());

        // Handle recurrence
        if (task.isRecurring()) {
            handleRecurringTaskCompletion(task);
        }

        // Update streak
        statsManager.updateTaskStreak(task);

        // Save to database
        taskDao.updateTask(task);
        taskDao.markTaskCompleted(task.getId(), true);

        logger.info(TAG, "Task completed (basic): " + task.getTitle());

        if (listener != null) {
            listener.onTaskCompleted(task);
        }
    }

    /**
     * Complete task with detailed tracking
     */
    private void completeTaskWithTracking(Task task, int timeSpent, int difficulty, String notes) {
        // Record completion details
        completionDao.recordCompletion(task.getId(), timeSpent, difficulty, notes);

        // Mark task as completed
        task.setCompleted(true);
        task.setLastCompletedDate(System.currentTimeMillis());

        // Handle recurrence
        if (task.isRecurring()) {
            handleRecurringTaskCompletion(task);
        }

        // Update streak
        statsManager.updateTaskStreak(task);

        // Save to database
        taskDao.updateTask(task);
        taskDao.markTaskCompleted(task.getId(), true);

        logger.info(TAG, "Task completed with tracking: " + task.getTitle() +
                    " (Time: " + timeSpent + " min, Difficulty: " + difficulty + ")");

        if (listener != null) {
            listener.onTaskCompleted(task);
        }
    }

    /**
     * Handle completion for recurring tasks
     */
    private void handleRecurringTaskCompletion(Task task) {
        if (task.getRecurrenceType() == Task.RECURRENCE_FREQUENCY) {
            // Update frequency count
            int completions = task.getCompletionsThisPeriod() + 1;
            task.setCompletionsThisPeriod(completions);

            // Check if period needs reset
            long periodStart = task.getCurrentPeriodStart();
            long periodEnd = calculatePeriodEnd(periodStart, task.getRecurrenceUnit());

            if (System.currentTimeMillis() > periodEnd) {
                // Start new period
                task.setCurrentPeriodStart(System.currentTimeMillis());
                task.setCompletionsThisPeriod(1);
            } else {
                task.setCompletionsThisPeriod(completions);
            }

            // Check if goal is met
            if (completions >= task.getRecurrenceAmount()) {
                // Goal met, task stays completed
                task.setCompleted(true);
            } else {
                // More completions needed
                task.setCompleted(false);
            }
        }
        // For interval tasks, they stay completed and will reappear after the interval
    }

    /**
     * Calculate when a period ends based on unit
     */
    private long calculatePeriodEnd(long periodStart, int unit) {
        long duration;
        switch (unit) {
            case Task.UNIT_WEEK:
                duration = 7L * 24 * 60 * 60 * 1000;
                break;
            case Task.UNIT_MONTH:
                duration = 30L * 24 * 60 * 60 * 1000;
                break;
            default: // UNIT_DAY
                duration = 24L * 60 * 60 * 1000;
                break;
        }
        return periodStart + duration;
    }

    /**
     * Dismiss the dialog
     */
    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Helper class for managing task-related dialogs.
 * Extracted from TaskActivity to reduce its size and improve organization.
 */
public class TaskDialogHelper {
    private final Activity context;
    private final TaskDatabaseHelper dbHelper;
    private final AppLogger logger;
    private final SimpleDateFormat dateFormat;

    // Listener interfaces
    public interface OnTaskSavedListener {
        void onTaskSaved(Task task);
        void onTasksNeedReload();
    }

    public interface OnTaskCompletedListener {
        void onTaskCompleted(Task task);
        void onCompletionCancelled(Task task);
    }

    private OnTaskSavedListener taskSavedListener;
    private OnTaskCompletedListener taskCompletedListener;

    public TaskDialogHelper(Activity context, TaskDatabaseHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.logger = AppLogger.getInstance(context);
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    public void setOnTaskSavedListener(OnTaskSavedListener listener) {
        this.taskSavedListener = listener;
    }

    public void setOnTaskCompletedListener(OnTaskCompletedListener listener) {
        this.taskCompletedListener = listener;
    }

    /**
     * Show dialog for adding a new task
     */
    public void showAddTaskDialog(List<String> categories) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find views
        EditText titleInput = dialogView.findViewById(R.id.taskTitleInput);
        EditText descriptionInput = dialogView.findViewById(R.id.taskDescriptionInput);
        AutoCompleteTextView categoryInput = dialogView.findViewById(R.id.taskCategoryInput);
        TextView dueDateText = dialogView.findViewById(R.id.taskDueDateText);
        Button selectDueDateButton = dialogView.findViewById(R.id.selectDueDateButton);
        Button clearDueDateButton = dialogView.findViewById(R.id.clearDueDateButton);
        Spinner prioritySpinner = dialogView.findViewById(R.id.taskPrioritySpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveTaskButton);

        // Recurrence views
        CheckBox recurrenceCheckBox = dialogView.findViewById(R.id.recurrenceCheckBox);
        LinearLayout recurrenceOptionsLayout = dialogView.findViewById(R.id.recurrenceOptionsLayout);
        Spinner recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner);
        EditText recurrenceAmountInput = dialogView.findViewById(R.id.recurrenceAmountInput);
        Spinner recurrenceUnitSpinner = dialogView.findViewById(R.id.recurrenceUnitSpinner);

        // Variable to track selected due date
        final long[] selectedDueDate = {0};

        // Setup category autocomplete
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, categories);
        categoryInput.setAdapter(categoryAdapter);
        categoryInput.setText("General"); // Default

        // Setup due date picker
        setupDatePicker(selectDueDateButton, dueDateText, selectedDueDate);

        // Clear date button
        clearDueDateButton.setOnClickListener(v -> {
            selectedDueDate[0] = 0;
            dueDateText.setText("No due date");
        });

        // Setup priority spinner
        String[] priorities = {"Low", "Medium", "High"};
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, priorities);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
        prioritySpinner.setSelection(1); // Default to Medium

        // Setup recurrence
        setupRecurrenceOptions(recurrenceCheckBox, recurrenceOptionsLayout,
                recurrenceTypeSpinner, recurrenceUnitSpinner);

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            int priority = prioritySpinner.getSelectedItemPosition();

            if (title.isEmpty()) {
                Toast.makeText(context, "Please enter a task title", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create new task
            Task newTask = new Task();
            newTask.setTitle(title);
            newTask.setDescription(description);
            newTask.setCategory(category.isEmpty() ? "General" : category);
            newTask.setPriority(priority);
            newTask.setCreatedAt(System.currentTimeMillis());
            newTask.setDueDate(selectedDueDate[0]);

            // Handle recurrence
            if (recurrenceCheckBox.isChecked()) {
                setupTaskRecurrence(newTask, recurrenceTypeSpinner,
                        recurrenceAmountInput, recurrenceUnitSpinner);
            }

            // Save to database
            long taskId = dbHelper.insertTask(newTask);
            newTask.setId(taskId);

            logger.info("TaskDialogHelper", "New task created: " + title);
            Toast.makeText(context, "Task added successfully", Toast.LENGTH_SHORT).show();

            dialog.dismiss();

            if (taskSavedListener != null) {
                taskSavedListener.onTaskSaved(newTask);
            }
        });

        dialog.show();
    }

    /**
     * Show dialog for editing an existing task
     */
    public void showEditTaskDialog(Task existingTask, List<String> categories) {
        // This will be a simplified version for now
        // The full implementation would be similar to showAddTaskDialog
        // but with pre-populated fields

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Task");
        builder.setMessage("Edit functionality will be implemented here");
        builder.setPositiveButton("OK", null);
        builder.show();

        // TODO: Implement full edit dialog
    }

    /**
     * Show completion dialog with time tracking
     */
    public void showCompletionDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_completion, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find views
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
        int avgTime = dbHelper.getAverageCompletionTime(task.getId());
        if (avgTime > 0) {
            averageTimeText.setText("(Avg: " + avgTime + " min)");
            averageTimeText.setVisibility(View.VISIBLE);
        }

        // Setup difficulty seekbar
        difficultySeekBar.setMax(10);
        difficultySeekBar.setProgress(5); // Default to medium
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
        });

        // Skip button - complete without tracking
        skipButton.setOnClickListener(v -> {
            completeTaskBasic(task);
            dialog.dismiss();
        });

        // Save button - complete with tracking
        saveButton.setOnClickListener(v -> {
            if (quickCompleteCheckBox.isChecked()) {
                completeTaskBasic(task);
            } else {
                int timeSpent = 0;
                try {
                    timeSpent = Integer.parseInt(timeSpentInput.getText().toString());
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
            if (taskCompletedListener != null) {
                taskCompletedListener.onCompletionCancelled(task);
            }
        });

        dialog.show();
    }

    // Helper methods
    private void setupDatePicker(Button button, TextView display, long[] selectedDate) {
        button.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (selectedDate[0] > 0) {
                calendar.setTimeInMillis(selectedDate[0]);
            }

            DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        selectedDate[0] = calendar.getTimeInMillis();
                        display.setText(dateFormat.format(calendar.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));

            datePickerDialog.show();
        });
    }

    private void setupRecurrenceOptions(CheckBox checkBox, LinearLayout layout,
                                       Spinner typeSpinner, Spinner unitSpinner) {
        // Setup recurrence type spinner
        String[] recurrenceTypes = {"Interval (e.g., every 3 days)", "Frequency (e.g., 3 times per week)"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, recurrenceTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        // Setup unit spinner
        String[] units = {"Day(s)", "Week(s)", "Month(s)"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        // Toggle recurrence options visibility
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void setupTaskRecurrence(Task task, Spinner typeSpinner,
                                    EditText amountInput, Spinner unitSpinner) {
        int recurrenceType = typeSpinner.getSelectedItemPosition() == 0 ?
                Task.RECURRENCE_INTERVAL : Task.RECURRENCE_FREQUENCY;

        int amount = 1;
        try {
            amount = Integer.parseInt(amountInput.getText().toString());
            if (amount < 1) amount = 1;
        } catch (NumberFormatException e) {
            amount = 1;
        }

        task.setRecurrenceType(recurrenceType);
        task.setRecurrenceAmount(amount);
        task.setRecurrenceUnit(unitSpinner.getSelectedItemPosition());

        // Initialize period for frequency tasks
        if (recurrenceType == Task.RECURRENCE_FREQUENCY) {
            task.setCurrentPeriodStart(System.currentTimeMillis());
            task.setCompletionsThisPeriod(0);
        }
    }

    private void completeTaskBasic(Task task) {
        task.setCompleted(true);
        dbHelper.markTaskCompleted(task.getId(), true);

        logger.info("TaskDialogHelper", "Task completed: " + task.getTitle());
        Toast.makeText(context, "Task completed!", Toast.LENGTH_SHORT).show();

        if (taskCompletedListener != null) {
            taskCompletedListener.onTaskCompleted(task);
        }
    }

    private void completeTaskWithTracking(Task task, int timeSpent, int difficulty, String notes) {
        // Save completion details
        dbHelper.saveCompletion(task.getId(), timeSpent, difficulty, notes);

        // Mark task as completed
        task.setCompleted(true);
        dbHelper.markTaskCompleted(task.getId(), true);

        logger.info("TaskDialogHelper", "Task completed with tracking: " + task.getTitle() +
                " (Time: " + timeSpent + " min, Difficulty: " + difficulty + ")");

        Toast.makeText(context, "Task completed with tracking!", Toast.LENGTH_SHORT).show();

        if (taskCompletedListener != null) {
            taskCompletedListener.onTaskCompleted(task);
        }
    }
}
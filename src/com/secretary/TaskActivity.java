package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskActivity extends Activity {
    private static final String TAG = "TaskActivity";

    private TaskDatabaseHelper dbHelper;
    private ListView taskListView;
    private TextView emptyTasksText;
    private Button addTaskButton;
    private TaskListAdapter adapter;
    private List<Task> taskList;
    private AppLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        logger = AppLogger.getInstance(this);
        logger.info(TAG, "TaskActivity started");

        // Initialize database
        dbHelper = new TaskDatabaseHelper(this);

        // Find views
        taskListView = findViewById(R.id.taskListView);
        emptyTasksText = findViewById(R.id.emptyTasksText);
        addTaskButton = findViewById(R.id.addTaskButton);

        // Initialize task list
        taskList = new ArrayList<>();

        // Setup adapter
        adapter = new TaskListAdapter();
        taskListView.setAdapter(adapter);

        // Setup add button
        addTaskButton.setOnClickListener(v -> showAddTaskDialog());

        // Load tasks
        loadTasks();

        logger.info(TAG, "TaskActivity initialized");
    }

    private void loadTasks() {
        taskList.clear();
        taskList.addAll(dbHelper.getAllTasks());
        adapter.notifyDataSetChanged();

        // Show/hide empty view
        if (taskList.isEmpty()) {
            taskListView.setVisibility(View.GONE);
            emptyTasksText.setVisibility(View.VISIBLE);
        } else {
            taskListView.setVisibility(View.VISIBLE);
            emptyTasksText.setVisibility(View.GONE);
        }

        logger.info(TAG, "Loaded " + taskList.size() + " tasks");
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find dialog views
        EditText titleInput = dialogView.findViewById(R.id.taskTitleInput);
        EditText descriptionInput = dialogView.findViewById(R.id.taskDescriptionInput);
        Spinner prioritySpinner = dialogView.findViewById(R.id.taskPrioritySpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveTaskButton);

        // Recurrence views
        CheckBox recurrenceCheckBox = dialogView.findViewById(R.id.recurrenceCheckBox);
        LinearLayout recurrenceOptionsLayout = dialogView.findViewById(R.id.recurrenceOptionsLayout);
        Spinner recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner);
        EditText recurrenceAmountInput = dialogView.findViewById(R.id.recurrenceAmountInput);
        TextView recurrenceLabel = dialogView.findViewById(R.id.recurrenceLabel);
        Spinner recurrenceUnitSpinner = dialogView.findViewById(R.id.recurrenceUnitSpinner);

        // Setup priority spinner
        String[] priorities = {"Low", "Medium", "High", "Urgent"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, priorities);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(spinnerAdapter);
        prioritySpinner.setSelection(1); // Default to Medium

        // Setup recurrence type spinner
        String[] recurrenceTypes = {"Every X Y", "X times per Y"};
        ArrayAdapter<String> recTypeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, recurrenceTypes);
        recTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceTypeSpinner.setAdapter(recTypeAdapter);

        // Setup recurrence unit spinner
        String[] recurrenceUnits = {"Day", "Week", "Month", "Year"};
        ArrayAdapter<String> recUnitAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, recurrenceUnits);
        recUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurrenceUnitSpinner.setAdapter(recUnitAdapter);

        // Handle recurrence checkbox
        recurrenceCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            recurrenceOptionsLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Handle recurrence type selection
        recurrenceTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // "Every X Y" selected
                    recurrenceLabel.setText(" ");
                } else {
                    // "X times per Y" selected
                    recurrenceLabel.setText(" times per ");
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            int priority = prioritySpinner.getSelectedItemPosition();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create and save task
            Task task = new Task(title, description);
            task.setPriority(priority);

            // Handle recurrence
            if (recurrenceCheckBox.isChecked()) {
                String amountStr = recurrenceAmountInput.getText().toString().trim();
                int amount = 1;
                try {
                    amount = Integer.parseInt(amountStr);
                    if (amount < 1) amount = 1;
                } catch (NumberFormatException e) {
                    amount = 1;
                }

                int recurrenceType = recurrenceTypeSpinner.getSelectedItemPosition() == 0 ?
                    Task.RECURRENCE_INTERVAL : Task.RECURRENCE_FREQUENCY;

                task.setRecurrenceType(recurrenceType);
                task.setRecurrenceAmount(amount);
                task.setRecurrenceUnit(recurrenceUnitSpinner.getSelectedItemPosition());

                // Initialize period tracking for frequency type
                if (recurrenceType == Task.RECURRENCE_FREQUENCY) {
                    task.setCurrentPeriodStart(System.currentTimeMillis());
                    task.setCompletionsThisPeriod(0);
                }
            }

            long id = dbHelper.insertTask(task);
            task.setId(id);

            String message = "Task added";
            if (task.isRecurring()) {
                message += " - " + task.getRecurrenceString();
            }

            logger.info(TAG, "Task created: " + title + " (Recurring: " + task.isRecurring() + ")");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            dialog.dismiss();
            loadTasks();
        });

        dialog.show();
    }

    // Custom adapter for task list
    private class TaskListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return taskList.size();
        }

        @Override
        public Task getItem(int position) {
            return taskList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return taskList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(TaskActivity.this)
                        .inflate(R.layout.task_list_item, parent, false);
            }

            Task task = getItem(position);

            // Find views
            CheckBox checkBox = convertView.findViewById(R.id.taskCheckBox);
            TextView titleText = convertView.findViewById(R.id.taskTitleText);
            TextView descriptionText = convertView.findViewById(R.id.taskDescriptionText);
            TextView priorityText = convertView.findViewById(R.id.taskPriorityText);
            Button deleteButton = convertView.findViewById(R.id.deleteTaskButton);

            // Set data
            checkBox.setChecked(task.isCompleted());
            titleText.setText(task.getTitle());

            // Strike through if completed
            if (task.isCompleted()) {
                titleText.setPaintFlags(titleText.getPaintFlags() |
                                       android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                titleText.setPaintFlags(titleText.getPaintFlags() &
                                       (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            }

            // Description
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                descriptionText.setText(task.getDescription());
                descriptionText.setVisibility(View.VISIBLE);
            } else {
                descriptionText.setVisibility(View.GONE);
            }

            // Priority and Recurrence
            String info = "Priority: " + task.getPriorityString();
            if (task.isRecurring()) {
                info += " | 🔁 " + task.getRecurrenceString();
                // Add progress for frequency tasks
                if (task.getRecurrenceType() == Task.RECURRENCE_FREQUENCY) {
                    info += " " + task.getProgressString();
                }
                // Show when interval tasks will reappear
                if (task.getRecurrenceType() == Task.RECURRENCE_INTERVAL && task.isCompleted()) {
                    info += task.getNextAppearanceString();
                }
            }
            priorityText.setText(info);

            // Checkbox listener
            checkBox.setOnClickListener(v -> {
                task.setCompleted(checkBox.isChecked());
                dbHelper.markTaskCompleted(task.getId(), checkBox.isChecked());
                logger.info(TAG, "Task " + task.getTitle() + " marked as " +
                          (checkBox.isChecked() ? "completed" : "active"));
                loadTasks();
            });

            // Delete button
            deleteButton.setOnClickListener(v -> {
                new AlertDialog.Builder(TaskActivity.this)
                        .setTitle("Delete Task")
                        .setMessage("Are you sure you want to delete this task?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            dbHelper.deleteTask(task.getId());
                            logger.info(TAG, "Task deleted: " + task.getTitle());
                            Toast.makeText(TaskActivity.this, "Task deleted",
                                         Toast.LENGTH_SHORT).show();
                            loadTasks();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            return convertView;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskActivity extends Activity implements TaskListAdapter.TaskActionListener {
    private static final String TAG = "TaskActivity";

    private TaskDatabaseHelper dbHelper;
    private ListView taskListView;
    private TextView emptyTasksText;
    private TextView statisticsText;
    private Button addTaskButton;
    private EditText searchEditText;
    private Spinner statusFilterSpinner;
    private Spinner priorityFilterSpinner;
    private Spinner categoryFilterSpinner;
    private Spinner sortBySpinner;
    private TaskListAdapter adapter;
    private TaskFilterManager filterManager;
    private TaskDialogHelper dialogHelper;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private List<String> allCategories;
    private AppLogger logger;

    // Filter states (will be managed by TaskFilterManager)
    private String searchQuery = "";
    private int statusFilter = 0; // 0=All, 1=Active, 2=Completed
    private int priorityFilter = -1; // -1=All, 0=Low, 1=Medium, 2=High, 3=Urgent
    private String categoryFilter = null; // null=All categories
    private int sortOption = 0; // 0=Priority, 1=Due Date, 2=Category, 3=Created Date, 4=Title

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
        statisticsText = findViewById(R.id.taskStatisticsText);
        addTaskButton = findViewById(R.id.addTaskButton);
        searchEditText = findViewById(R.id.searchEditText);
        statusFilterSpinner = findViewById(R.id.statusFilterSpinner);
        priorityFilterSpinner = findViewById(R.id.priorityFilterSpinner);
        categoryFilterSpinner = findViewById(R.id.categoryFilterSpinner);
        sortBySpinner = findViewById(R.id.sortBySpinner);

        // Initialize task lists
        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        allCategories = new ArrayList<>();

        // Initialize filter manager
        filterManager = new TaskFilterManager();

        // Initialize dialog helper
        dialogHelper = new TaskDialogHelper(this, dbHelper);
        setupDialogListeners();

        // Setup adapter with the new external class
        adapter = new TaskListAdapter(this, filteredTaskList, this);
        taskListView.setAdapter(adapter);

        // Setup spinners
        setupFilterSpinners();

        // Setup search
        setupSearch();

        // Setup add button
        addTaskButton.setOnClickListener(v -> showAddTaskDialog());

        // Load tasks
        loadTasks();

        logger.info(TAG, "TaskActivity initialized");
    }

    private void setupFilterSpinners() {
        // Status filter spinner
        String[] statusOptions = {"All Tasks", "Active", "Completed"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusFilterSpinner.setAdapter(statusAdapter);
        statusFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                statusFilter = position;
                applyFilters();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Priority filter spinner
        String[] priorityOptions = {"All Priorities", "Low", "Medium", "High", "Urgent"};
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, priorityOptions);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        priorityFilterSpinner.setAdapter(priorityAdapter);
        priorityFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                priorityFilter = position - 1; // -1 for All, 0-3 for specific priorities
                applyFilters();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Load categories and setup category filter
        updateCategoryFilter();

        // Setup sort spinner
        setupSortSpinner();
    }

    private void setupSortSpinner() {
        String[] sortOptions = {
            "Priority (High to Low)",
            "Due Date (Nearest First)",
            "Category (A to Z)",
            "Created Date (Newest First)",
            "Title (A to Z)"
        };

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortBySpinner.setAdapter(sortAdapter);

        sortBySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sortOption = position;
                applyFilters(); // Use applyFilters which includes sorting
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilters() {
        // Configure filter manager based on current UI state
        filterManager.setSearchQuery(searchQuery);
        filterManager.setCategoryFilter(categoryFilter);

        // Convert status filter to enum
        TaskFilterManager.CompletionFilter completionFilter =
            statusFilter == 1 ? TaskFilterManager.CompletionFilter.ACTIVE_ONLY :
            statusFilter == 2 ? TaskFilterManager.CompletionFilter.COMPLETED_ONLY :
            TaskFilterManager.CompletionFilter.ALL;
        filterManager.setCompletionFilter(completionFilter);

        // Convert sort option to enum
        TaskFilterManager.SortOption[] sortOptions = TaskFilterManager.SortOption.values();
        if (sortOption >= 0 && sortOption < sortOptions.length) {
            filterManager.setSortOption(sortOptions[sortOption]);
        }

        // Apply filters and sorting
        filteredTaskList.clear();
        filteredTaskList.addAll(filterManager.applyFilters(taskList));
        filterManager.sortTasks(filteredTaskList);

        // Show/hide empty view
        if (filteredTaskList.isEmpty()) {
            taskListView.setVisibility(View.GONE);
            emptyTasksText.setVisibility(View.VISIBLE);
            if (!searchQuery.isEmpty() || statusFilter > 0 || priorityFilter >= 0) {
                emptyTasksText.setText("No tasks match your filters.");
            } else {
                emptyTasksText.setText("No tasks yet.\nTap + to add a task.");
            }
        } else {
            taskListView.setVisibility(View.VISIBLE);
            emptyTasksText.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
        logger.info(TAG, "Filters applied: " + filteredTaskList.size() + " tasks shown");
    }


    private void loadTasks() {
        taskList.clear();
        taskList.addAll(dbHelper.getAllTasks());
        updateCategoryFilter(); // Update category filter with any new categories
        updateStatistics(); // Update statistics display
        applyFilters(); // Apply filters after loading

        logger.info(TAG, "Loaded " + taskList.size() + " tasks");
    }

    private void updateStatistics() {
        int todayCount = dbHelper.getTasksCompletedToday();
        int weekCount = dbHelper.getTasksCompletedLast7Days();
        int overdueCount = dbHelper.getOverdueTasksCount();

        String statsText = String.format("Today: %d | Week: %d | Overdue: %d",
                todayCount, weekCount, overdueCount);

        statisticsText.setText(statsText);

        // Highlight if there are overdue tasks
        if (overdueCount > 0) {
            statisticsText.setTextColor(0xFFFFAA00); // Orange for warning
        } else {
            statisticsText.setTextColor(0xFFFFFFFF); // White
        }
    }

    private void updateCategoryFilter() {
        // Get all unique categories from database
        allCategories = dbHelper.getAllCategories();

        // Build options for category filter spinner
        String[] categoryOptions = new String[allCategories.size() + 1];
        categoryOptions[0] = "All Categories";
        for (int i = 0; i < allCategories.size(); i++) {
            categoryOptions[i + 1] = allCategories.get(i);
        }

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoryOptions);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categoryFilterSpinner.setAdapter(categoryAdapter);

        // Restore previous selection if possible
        if (categoryFilter != null) {
            int index = allCategories.indexOf(categoryFilter) + 1;
            if (index > 0 && index < categoryOptions.length) {
                categoryFilterSpinner.setSelection(index);
            }
        }

        categoryFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    categoryFilter = null; // All categories
                } else {
                    categoryFilter = allCategories.get(position - 1);
                }
                applyFilters();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupDialogListeners() {
        // Set up listeners for TaskDialogHelper callbacks
        dialogHelper.setOnTaskSavedListener(new TaskDialogHelper.OnTaskSavedListener() {
            @Override
            public void onTaskSaved(Task task) {
                loadTasks(); // Reload the task list after saving
            }

            @Override
            public void onTasksNeedReload() {
                loadTasks();
            }
        });

        dialogHelper.setOnTaskCompletedListener(new TaskDialogHelper.OnTaskCompletedListener() {
            @Override
            public void onTaskCompleted(Task task) {
                loadTasks(); // Reload after completion
            }

            @Override
            public void onCompletionCancelled(Task task) {
                // Task was toggled but user cancelled completion dialog
                // Need to revert the completion status
                task.setCompleted(false);
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void showAddTaskDialog() {
        // Delegate to TaskDialogHelper
        dialogHelper.showAddTaskDialog(allCategories);
    }

    private void showEditTaskDialog(Task existingTask) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find dialog views
        EditText titleInput = dialogView.findViewById(R.id.taskTitleInput);
        EditText descriptionInput = dialogView.findViewById(R.id.taskDescriptionInput);
        AutoCompleteTextView categoryInput = dialogView.findViewById(R.id.taskCategoryInput);
        TextView dueDateText = dialogView.findViewById(R.id.taskDueDateText);
        Button selectDueDateButton = dialogView.findViewById(R.id.selectDueDateButton);
        Button clearDueDateButton = dialogView.findViewById(R.id.clearDueDateButton);
        Spinner prioritySpinner = dialogView.findViewById(R.id.taskPrioritySpinner);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button saveButton = dialogView.findViewById(R.id.saveTaskButton);

        // Variables to track selected due date
        final long[] selectedDueDate = {0};

        // Recurrence views
        CheckBox recurrenceCheckBox = dialogView.findViewById(R.id.recurrenceCheckBox);
        LinearLayout recurrenceOptionsLayout = dialogView.findViewById(R.id.recurrenceOptionsLayout);
        Spinner recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner);
        EditText recurrenceAmountInput = dialogView.findViewById(R.id.recurrenceAmountInput);
        TextView recurrenceLabel = dialogView.findViewById(R.id.recurrenceLabel);
        Spinner recurrenceUnitSpinner = dialogView.findViewById(R.id.recurrenceUnitSpinner);

        // Update dialog title - the first TextView in the dialog layout
        TextView titleView = (TextView) ((LinearLayout) dialogView).getChildAt(0);
        if (titleView != null) {
            titleView.setText("Edit Task");
        }

        // Setup category autocomplete
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, allCategories);
        categoryInput.setAdapter(categoryAdapter);

        // Setup due date picker
        selectDueDateButton.setOnClickListener(v -> {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            if (selectedDueDate[0] > 0) {
                calendar.setTimeInMillis(selectedDueDate[0]);
            }

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    selectedDueDate[0] = calendar.getTimeInMillis();

                    // Format and display the date
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    dueDateText.setText(sdf.format(calendar.getTime()));
                    clearDueDateButton.setVisibility(View.VISIBLE);
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH));

            // Set minimum date to today
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        // Clear due date button
        clearDueDateButton.setOnClickListener(v -> {
            selectedDueDate[0] = 0;
            dueDateText.setText("No due date set");
            clearDueDateButton.setVisibility(View.GONE);
        });

        // Setup priority spinner
        String[] priorities = {"Low", "Medium", "High", "Urgent"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, priorities);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(spinnerAdapter);

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

        // Pre-populate with existing task data
        titleInput.setText(existingTask.getTitle());
        descriptionInput.setText(existingTask.getDescription());
        categoryInput.setText(existingTask.getCategory());

        // Set due date if exists
        if (existingTask.getDueDate() > 0) {
            selectedDueDate[0] = existingTask.getDueDate();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            dueDateText.setText(sdf.format(new Date(selectedDueDate[0])));
            clearDueDateButton.setVisibility(View.VISIBLE);
        }

        prioritySpinner.setSelection(existingTask.getPriority());

        // Pre-populate recurrence if task is recurring
        if (existingTask.isRecurring()) {
            recurrenceCheckBox.setChecked(true);
            recurrenceOptionsLayout.setVisibility(View.VISIBLE);

            // Set recurrence type
            if (existingTask.getRecurrenceType() == Task.RECURRENCE_INTERVAL) {
                recurrenceTypeSpinner.setSelection(0);
                recurrenceLabel.setText(" ");
            } else {
                recurrenceTypeSpinner.setSelection(1);
                recurrenceLabel.setText(" times per ");
            }

            // Set amount and unit
            recurrenceAmountInput.setText(String.valueOf(existingTask.getRecurrenceAmount()));
            recurrenceUnitSpinner.setSelection(existingTask.getRecurrenceUnit());
        }

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

        // Update save button text
        saveButton.setText("Update");

        // Cancel button
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        // Save button
        saveButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            if (category.isEmpty()) {
                category = "General"; // Default if no category entered
            }
            int priority = prioritySpinner.getSelectedItemPosition();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update existing task
            existingTask.setTitle(title);
            existingTask.setDescription(description);
            existingTask.setCategory(category);
            existingTask.setPriority(priority);
            existingTask.setDueDate(selectedDueDate[0]);

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

                existingTask.setRecurrenceType(recurrenceType);
                existingTask.setRecurrenceAmount(amount);
                existingTask.setRecurrenceUnit(recurrenceUnitSpinner.getSelectedItemPosition());

                // If changing to frequency type and not already tracking, initialize period
                if (recurrenceType == Task.RECURRENCE_FREQUENCY &&
                    existingTask.getCurrentPeriodStart() == 0) {
                    existingTask.setCurrentPeriodStart(System.currentTimeMillis());
                    existingTask.setCompletionsThisPeriod(0);
                }
            } else {
                // Remove recurrence
                existingTask.setRecurrenceType(Task.RECURRENCE_NONE);
                existingTask.setRecurrenceAmount(0);
                existingTask.setRecurrenceUnit(Task.UNIT_DAY);
                existingTask.setCompletionsThisPeriod(0);
                existingTask.setCurrentPeriodStart(0);
            }

            // Update in database
            dbHelper.updateTask(existingTask);

            String message = "Task updated";
            if (existingTask.isRecurring()) {
                message += " - " + existingTask.getRecurrenceString();
            }

            logger.info(TAG, "Task updated: " + title + " (Recurring: " + existingTask.isRecurring() + ")");
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            dialog.dismiss();
            loadTasks();
        });

        dialog.show();
    }

    // Implementation of TaskListAdapter.TaskActionListener interface
    @Override
    public void onTaskCheckChanged(Task task, boolean isChecked) {
        if (isChecked) {
            showCompletionDialog(task);
        } else {
            task.setCompleted(false);
            dbHelper.markTaskCompleted(task.getId(), false);
            logger.info(TAG, "Task " + task.getTitle() + " marked as active");
            loadTasks();
        }
    }

    @Override
    public void onTaskEdit(Task task) {
        showEditTaskDialog(task);
    }

    @Override
    public void onTaskDelete(Task task) {
        dbHelper.deleteTask(task.getId());
        logger.info(TAG, "Task deleted: " + task.getTitle());
        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
        loadTasks();
    }

    @Override
    public void onTasksChanged() {
        loadTasks();
    }


    private void showCompletionDialog(Task task) {
        // Delegate to TaskDialogHelper
        dialogHelper.showCompletionDialog(task);
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
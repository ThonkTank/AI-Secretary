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

public class TaskActivity extends Activity {
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
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private List<String> allCategories;
    private AppLogger logger;

    // Filter states
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

        // Setup adapter
        adapter = new TaskListAdapter();
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
                applySorting();
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
        filteredTaskList.clear();

        for (Task task : taskList) {
            // Check status filter
            if (statusFilter == 1 && task.isCompleted()) continue; // Active filter, skip completed
            if (statusFilter == 2 && !task.isCompleted()) continue; // Completed filter, skip active

            // Check priority filter
            if (priorityFilter >= 0 && task.getPriority() != priorityFilter) continue;

            // Check category filter
            if (categoryFilter != null && !task.getCategory().equals(categoryFilter)) continue;

            // Check search query
            if (!searchQuery.isEmpty()) {
                String title = task.getTitle().toLowerCase();
                String description = task.getDescription() != null ? task.getDescription().toLowerCase() : "";
                if (!title.contains(searchQuery) && !description.contains(searchQuery)) {
                    continue;
                }
            }

            // Task passes all filters
            filteredTaskList.add(task);
        }

        // Apply sorting after filtering
        applySorting();

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

        logger.info(TAG, "Filters applied: " + filteredTaskList.size() + " tasks shown");
    }

    private void applySorting() {
        if (filteredTaskList == null || filteredTaskList.isEmpty()) {
            return;
        }

        java.util.Collections.sort(filteredTaskList, (task1, task2) -> {
            switch (sortOption) {
                case 0: // Priority (High to Low)
                    // First by completion status, then by priority
                    if (task1.isCompleted() != task2.isCompleted()) {
                        return task1.isCompleted() ? 1 : -1;
                    }
                    return Integer.compare(task2.getPriority(), task1.getPriority());

                case 1: // Due Date (Nearest First)
                    // Tasks without due date go to the end
                    if (task1.getDueDate() == 0 && task2.getDueDate() == 0) {
                        return 0;
                    }
                    if (task1.getDueDate() == 0) return 1;
                    if (task2.getDueDate() == 0) return -1;
                    return Long.compare(task1.getDueDate(), task2.getDueDate());

                case 2: // Category (A to Z)
                    String cat1 = task1.getCategory() != null ? task1.getCategory() : "";
                    String cat2 = task2.getCategory() != null ? task2.getCategory() : "";
                    return cat1.compareToIgnoreCase(cat2);

                case 3: // Created Date (Newest First)
                    return Long.compare(task2.getCreatedAt(), task1.getCreatedAt());

                case 4: // Title (A to Z)
                    return task1.getTitle().compareToIgnoreCase(task2.getTitle());

                default:
                    return 0;
            }
        });

        adapter.notifyDataSetChanged();
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

    private void showAddTaskDialog() {
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

        // Setup category autocomplete
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, allCategories);
        categoryInput.setAdapter(categoryAdapter);
        categoryInput.setText("General"); // Default to General

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
            String category = categoryInput.getText().toString().trim();
            if (category.isEmpty()) {
                category = "General"; // Default if no category entered
            }
            int priority = prioritySpinner.getSelectedItemPosition();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create and save task
            Task task = new Task(title, description);
            task.setCategory(category);
            task.setPriority(priority);
            task.setDueDate(selectedDueDate[0]);

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

    // Custom adapter for task list
    private class TaskListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredTaskList.size();
        }

        @Override
        public Task getItem(int position) {
            return filteredTaskList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return filteredTaskList.get(position).getId();
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
            Button editButton = convertView.findViewById(R.id.editTaskButton);
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

            // Category, Priority, Due Date and Recurrence
            String info = task.getCategory() + " | Priority: " + task.getPriorityString();

            // Add due date if set
            if (task.getDueDate() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                info += " | Due: " + sdf.format(new Date(task.getDueDate()));

                // Check if overdue
                if (!task.isCompleted() && task.getDueDate() < System.currentTimeMillis()) {
                    info += " (OVERDUE)";
                }
            }

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
                if (checkBox.isChecked()) {
                    // Show completion dialog when marking as complete
                    showCompletionDialog(task);
                } else {
                    // Direct uncheck (no dialog needed)
                    task.setCompleted(false);
                    dbHelper.markTaskCompleted(task.getId(), false);
                    logger.info(TAG, "Task " + task.getTitle() + " marked as active");
                    loadTasks();
                }
            });

            // Edit button
            editButton.setOnClickListener(v -> {
                showEditTaskDialog(task);
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

    private void showCompletionDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_completion, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

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
        int avgTime = dbHelper.getAverageCompletionTime(task.getId());
        if (avgTime > 0) {
            averageTimeText.setText("(Avg: " + avgTime + " min)");
            timeSpentInput.setText(String.valueOf(avgTime));
        }

        // Setup difficulty seekbar
        difficultySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                difficultyValueText.setText("Difficulty: " + progress + "/10");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Skip button (complete without details)
        skipButton.setOnClickListener(v -> {
            task.setCompleted(true);
            dbHelper.markTaskCompleted(task.getId(), true);
            logger.info(TAG, "Task " + task.getTitle() + " completed (quick)");
            dialog.dismiss();
            loadTasks();
        });

        // Save button (complete with details)
        saveButton.setOnClickListener(v -> {
            int timeSpent = 0;
            try {
                timeSpent = Integer.parseInt(timeSpentInput.getText().toString());
            } catch (NumberFormatException e) {
                // Default to 0 if invalid
            }

            int difficulty = difficultySeekBar.getProgress();
            String notes = notesInput.getText().toString().trim();

            // Save completion details
            dbHelper.saveCompletion(task.getId(), timeSpent, difficulty, notes);

            // Mark task as completed
            task.setCompleted(true);
            dbHelper.markTaskCompleted(task.getId(), true);

            logger.info(TAG, "Task " + task.getTitle() + " completed with details " +
                       "(time: " + timeSpent + " min, difficulty: " + difficulty + ")");

            // Save quick complete preference if checked
            if (quickCompleteCheckBox.isChecked()) {
                // TODO: Save preference for this task
            }

            dialog.dismiss();
            loadTasks();
        });

        dialog.show();
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
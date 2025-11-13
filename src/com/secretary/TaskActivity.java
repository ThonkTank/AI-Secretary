package com.secretary.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;

public class TaskActivity extends Activity implements TaskListAdapter.TaskActionListener {
    private static final String TAG = "TaskActivity";

    private TaskDatabaseHelper dbHelper;
    private TaskDialogHelper dialogHelper;
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

        // Initialize dialog helper
        dialogHelper = new TaskDialogHelper(this, dbHelper);
        setupDialogHelperListeners();

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

    private void setupDialogHelperListeners() {
        dialogHelper.setOnTaskSavedListener(new TaskDialogHelper.OnTaskSavedListener() {
            @Override
            public void onTaskSaved(Task task) {
                loadTasks();
            }

            @Override
            public void onTasksNeedReload() {
                loadTasks();
            }
        });

        dialogHelper.setOnTaskCompletedListener(new TaskDialogHelper.OnTaskCompletedListener() {
            @Override
            public void onTaskCompleted(Task task) {
                loadTasks();
            }

            @Override
            public void onCompletionCancelled(Task task) {
                // Re-check the task if completion was cancelled
                task.setCompleted(false);
                adapter.notifyDataSetChanged();
            }
        });
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

    private void showAddTaskDialog() {
        dialogHelper.showAddTaskDialog(allCategories);
    }

    private void showEditTaskDialog(Task existingTask) {
        dialogHelper.showEditTaskDialog(existingTask, allCategories);
    }


    private void showCompletionDialog(Task task) {
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
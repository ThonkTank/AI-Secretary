package com.secretary.helloworld.ui.activities;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import com.secretary.helloworld.R;
import com.secretary.helloworld.Task;
import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.data.dao.TaskDao;
import com.secretary.helloworld.data.dao.CompletionDao;
import com.secretary.helloworld.ui.adapters.TaskListAdapter;
import com.secretary.helloworld.ui.dialogs.TaskEditDialog;
import com.secretary.helloworld.ui.dialogs.CompletionDialog;
import com.secretary.helloworld.utils.StatsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simplified Task Activity using extracted components.
 * Reduced from 971 lines to ~300 lines through proper separation of concerns.
 */
public class TaskActivity extends Activity implements
        TaskListAdapter.TaskActionListener,
        TaskEditDialog.TaskSaveListener,
        CompletionDialog.CompletionListener {

    private static final String TAG = "TaskActivity";

    // Data components
    private TaskDao taskDao;
    private CompletionDao completionDao;
    private StatsManager statsManager;
    private AppLogger logger;

    // UI components
    private ListView taskListView;
    private TextView emptyText;
    private TextView statsText;
    private EditText searchInput;
    private Spinner categorySpinner;
    private Spinner completionSpinner;
    private Spinner sortSpinner;
    private Button addTaskButton;

    // Adapters and dialogs
    private TaskListAdapter taskAdapter;
    private TaskEditDialog editDialog;
    private CompletionDialog completionDialog;

    // Data
    private List<Task> allTaskList = new ArrayList<>();
    private List<Task> filteredTaskList = new ArrayList<>();
    private List<String> categories = new ArrayList<>();

    // Filter and sort state
    private int selectedCategory = 0;
    private int completionFilter = 0;
    private int sortOption = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize components
        initializeComponents();

        // Setup UI
        setupViews();
        setupFilters();
        setupSearch();

        // Load initial data
        loadTasks();
    }

    /**
     * Initialize data components
     */
    private void initializeComponents() {
        logger = AppLogger.getInstance(this);
        taskDao = new TaskDao(this);
        completionDao = new CompletionDao(this);
        statsManager = new StatsManager(this);

        editDialog = new TaskEditDialog(this, this);
        completionDialog = new CompletionDialog(this, this);

        logger.info(TAG, "TaskActivity initialized with refactored architecture");
    }

    /**
     * Setup UI views
     */
    private void setupViews() {
        taskListView = findViewById(R.id.taskListView);
        emptyText = findViewById(R.id.emptyTaskText);
        statsText = findViewById(R.id.statsText);
        searchInput = findViewById(R.id.searchInput);
        categorySpinner = findViewById(R.id.categoryFilter);
        completionSpinner = findViewById(R.id.completionFilter);
        sortSpinner = findViewById(R.id.sortSpinner);
        addTaskButton = findViewById(R.id.addTaskButton);

        // Setup task list adapter
        taskAdapter = new TaskListAdapter(this, filteredTaskList, this);
        taskListView.setAdapter(taskAdapter);

        // Add task button
        addTaskButton.setOnClickListener(v -> editDialog.showAddDialog());
    }

    /**
     * Setup filter spinners
     */
    private void setupFilters() {
        // Category filter
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = position;
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Completion filter
        String[] completionOptions = {"All Tasks", "Active Only", "Completed Only"};
        completionSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, completionOptions));

        completionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                completionFilter = position;
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Sort options
        String[] sortOptions = {
            "Priority (High to Low)",
            "Due Date (Nearest First)",
            "Created (Newest First)",
            "Title (A-Z)",
            "Category"
        };
        sortSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, sortOptions));

        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortOption = position;
                applySorting();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Setup search functionality
     */
    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
    }

    /**
     * Load tasks from database
     */
    private void loadTasks() {
        allTaskList = taskDao.getAllTasks();
        updateCategoryFilter();
        applyFilters();
        updateStatistics();

        logger.debug(TAG, "Loaded " + allTaskList.size() + " tasks");
    }

    /**
     * Update category filter options
     */
    private void updateCategoryFilter() {
        categories.clear();
        categories.add("All Categories");
        categories.addAll(taskDao.getAllCategories());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(adapter);
    }

    /**
     * Apply filters to task list
     */
    private void applyFilters() {
        filteredTaskList.clear();
        String searchQuery = searchInput.getText().toString().toLowerCase().trim();

        for (Task task : allTaskList) {
            // Apply completion filter
            if (completionFilter == 1 && task.isCompleted()) continue;
            if (completionFilter == 2 && !task.isCompleted()) continue;

            // Apply category filter
            if (selectedCategory > 0) {
                String selectedCat = categories.get(selectedCategory);
                if (task.getCategory() == null || !task.getCategory().equals(selectedCat)) {
                    continue;
                }
            }

            // Apply search filter
            if (!searchQuery.isEmpty()) {
                boolean matches = task.getTitle().toLowerCase().contains(searchQuery) ||
                        (task.getDescription() != null &&
                         task.getDescription().toLowerCase().contains(searchQuery));
                if (!matches) continue;
            }

            filteredTaskList.add(task);
        }

        applySorting();
    }

    /**
     * Apply sorting to filtered list
     */
    private void applySorting() {
        Collections.sort(filteredTaskList, (task1, task2) -> {
            switch (sortOption) {
                case 0: // Priority (High to Low)
                    return Integer.compare(task2.getPriority(), task1.getPriority());
                case 1: // Due Date (Nearest First)
                    if (task1.getDueDate() == 0) return 1;
                    if (task2.getDueDate() == 0) return -1;
                    return Long.compare(task1.getDueDate(), task2.getDueDate());
                case 2: // Created (Newest First)
                    return Long.compare(task2.getCreatedAt(), task1.getCreatedAt());
                case 3: // Title (A-Z)
                    return task1.getTitle().compareToIgnoreCase(task2.getTitle());
                case 4: // Category
                    String cat1 = task1.getCategory() != null ? task1.getCategory() : "";
                    String cat2 = task2.getCategory() != null ? task2.getCategory() : "";
                    return cat1.compareToIgnoreCase(cat2);
                default:
                    return 0;
            }
        });

        updateUI();
    }

    /**
     * Update UI after data changes
     */
    private void updateUI() {
        taskAdapter.notifyDataSetChanged();
        emptyText.setVisibility(filteredTaskList.isEmpty() ? View.VISIBLE : View.GONE);
        taskListView.setVisibility(filteredTaskList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Update statistics display
     */
    private void updateStatistics() {
        statsManager.calculateStatistics(allTaskList);
        statsText.setText(statsManager.getStatsSummary());
    }

    // TaskListAdapter.TaskActionListener implementation

    @Override
    public void onTaskCheckedChanged(Task task, boolean isChecked) {
        if (isChecked) {
            completionDialog.show(task);
        } else {
            task.setCompleted(false);
            taskDao.markTaskCompleted(task.getId(), false);
            logger.info(TAG, "Task " + task.getTitle() + " marked as active");
            loadTasks();
        }
    }

    @Override
    public void onTaskEdit(Task task) {
        editDialog.showEditDialog(task);
    }

    @Override
    public void onTaskDelete(Task task) {
        taskDao.deleteTask(task.getId());
        completionDao.deleteCompletionsForTask(task.getId());
        logger.info(TAG, "Task deleted: " + task.getTitle());
        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
        loadTasks();
    }

    // TaskEditDialog.TaskSaveListener implementation

    @Override
    public void onTaskSaved(Task task) {
        String message = task.getId() == 0 ? "Task created" : "Task updated";
        if (task.isRecurring()) {
            message += " - " + task.getRecurrenceString();
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        loadTasks();
    }

    // CompletionDialog.CompletionListener implementation

    @Override
    public void onTaskCompleted(Task task) {
        Toast.makeText(this, "Task completed!", Toast.LENGTH_SHORT).show();
        loadTasks();
    }

    @Override
    public void onCompletionCancelled(Task task) {
        // Reset checkbox state
        loadTasks();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        taskDao.close();
        completionDao.close();
        statsManager.close();
    }
}
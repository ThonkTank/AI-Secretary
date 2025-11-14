package com.secretary.helloworld

import com.secretary.helloworld.core.logging.AppLogger
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.util.*

/**
 * Task Activity - Main Task Management UI
 * Phase 4.5.3 Wave 9: Converted to Kotlin
 *
 * Displays task list with search, filtering, and sorting.
 * Delegates dialog management to TaskDialogHelper.
 * Implements TaskActionListener for adapter callbacks.
 */
class TaskActivity : Activity(), TaskListAdapter.TaskActionListener {

    companion object {
        private const val TAG = "TaskActivity"
    }

    // Dependencies
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var dialogHelper: TaskDialogHelper
    private lateinit var filterManager: TaskFilterManager
    private lateinit var logger: AppLogger

    // Views
    private lateinit var taskListView: ListView
    private lateinit var emptyTasksText: TextView
    private lateinit var statisticsText: TextView
    private lateinit var addTaskButton: Button
    private lateinit var searchEditText: EditText
    private lateinit var statusFilterSpinner: Spinner
    private lateinit var priorityFilterSpinner: Spinner
    private lateinit var categoryFilterSpinner: Spinner
    private lateinit var sortBySpinner: Spinner

    // Data
    private lateinit var adapter: TaskListAdapter
    private val taskList = ArrayList<Task>()
    private val filteredTaskList = ArrayList<Task>()
    private var allCategories = listOf<String>()

    // Filter states
    private var searchQuery = ""
    private var statusFilter = 0        // 0=All, 1=Active, 2=Completed
    private var priorityFilter = -1     // -1=All, 0=Low, 1=Medium, 2=High
    private var categoryFilter: String? = null
    private var sortOption = 0          // Index in TaskFilterManager.SortOption enum

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_tasks)
            logger = AppLogger.getInstance(this)
            logger.info(TAG, "TaskActivity started - setContentView successful")

            // Initialize components
            dbHelper = TaskDatabaseHelper(this)
            dialogHelper = TaskDialogHelper(this, dbHelper)
            setupDialogHelperListeners()

            // Find views
            taskListView = findViewById(R.id.taskListView)
            emptyTasksText = findViewById(R.id.emptyTasksText)
            statisticsText = findViewById(R.id.taskStatisticsText)
            addTaskButton = findViewById(R.id.addTaskButton)
            searchEditText = findViewById(R.id.searchEditText)
            statusFilterSpinner = findViewById(R.id.statusFilterSpinner)
            priorityFilterSpinner = findViewById(R.id.priorityFilterSpinner)
            categoryFilterSpinner = findViewById(R.id.categoryFilterSpinner)
            sortBySpinner = findViewById(R.id.sortBySpinner)

            logger.info(TAG, "All views found successfully")

            // Setup adapter
            adapter = TaskListAdapter(this, filteredTaskList, this)
            taskListView.adapter = adapter

            // Setup filter manager
            filterManager = TaskFilterManager()

            // Setup UI
            setupFilterSpinners()
            setupSearch()

            // Add task button
            addTaskButton.setOnClickListener {
                logger.info(TAG, "Add task button clicked")
                showAddTaskDialog()
            }

            // Initial load
            loadTasks()

            logger.info(TAG, "TaskActivity onCreate completed successfully")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "FATAL ERROR in TaskActivity.onCreate()", e)
            logger?.error(TAG, "TaskActivity onCreate crashed: ${e.message}", e)

            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to open Tasks:\n${e.message}")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
        }
    }

    /**
     * Setup listeners for TaskDialogHelper callbacks
     */
    private fun setupDialogHelperListeners() {
        dialogHelper.setOnTaskSavedListener(object : TaskDialogHelper.OnTaskSavedListener {
            override fun onTaskSaved(task: Task) {
                loadTasks()
            }

            override fun onTasksNeedReload() {
                loadTasks()
            }
        })

        dialogHelper.setOnTaskCompletedListener(object : TaskDialogHelper.OnTaskCompletedListener {
            override fun onTaskCompleted(task: Task) {
                loadTasks()
            }

            override fun onCompletionCancelled(task: Task) {
                // Unchecked the checkbox - reload to reset UI
                loadTasks()
            }
        })
    }

    /**
     * Setup filter spinners with adapters and listeners
     */
    private fun setupFilterSpinners() {
        // Status filter
        val statusOptions = arrayOf("All Tasks", "Active Only", "Completed Only")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        statusFilterSpinner.adapter = statusAdapter
        statusFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                statusFilter = position
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Priority filter
        val priorityOptions = arrayOf("All Priorities", "Low", "Medium", "High")
        val priorityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorityOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        priorityFilterSpinner.adapter = priorityAdapter
        priorityFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                priorityFilter = position - 1 // -1 = All, 0 = Low, 1 = Medium, 2 = High
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Sort by
        val sortOptions = arrayOf("Sort: Priority", "Sort: Due Date", "Sort: Category", "Sort: Created", "Sort: Title")
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sortBySpinner.adapter = sortAdapter
        sortBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortOption = position
                applyFilters() // Use applyFilters which includes sorting
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Setup search text watcher
     */
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().lowercase(Locale.getDefault())
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Apply current filters and sorting to task list
     */
    private fun applyFilters() {
        // Configure filter manager based on current UI state
        filterManager.searchQuery = searchQuery
        filterManager.categoryFilter = categoryFilter

        // Convert status filter to enum
        val completionFilter = when (statusFilter) {
            1 -> TaskFilterManager.CompletionFilter.ACTIVE_ONLY
            2 -> TaskFilterManager.CompletionFilter.COMPLETED_ONLY
            else -> TaskFilterManager.CompletionFilter.ALL
        }
        filterManager.completionFilter = completionFilter

        // Convert sort option to enum
        val sortOptions = TaskFilterManager.SortOption.values()
        if (sortOption in sortOptions.indices) {
            filterManager.sortOption = sortOptions[sortOption]
        }

        // Apply filters and sorting
        filteredTaskList.clear()
        filteredTaskList.addAll(filterManager.applyFilters(taskList))
        filterManager.sortTasks(filteredTaskList)

        // Show/hide empty view
        if (filteredTaskList.isEmpty()) {
            taskListView.visibility = View.GONE
            emptyTasksText.visibility = View.VISIBLE
            if (searchQuery.isNotEmpty() || statusFilter > 0 || priorityFilter >= 0) {
                emptyTasksText.text = "No tasks match your filters."
            } else {
                emptyTasksText.text = "No tasks yet.\nTap + to add a task."
            }
        } else {
            taskListView.visibility = View.VISIBLE
            emptyTasksText.visibility = View.GONE
        }

        adapter.notifyDataSetChanged()
        logger.info(TAG, "Filters applied: ${filteredTaskList.size} tasks shown")
    }

    /**
     * Load all tasks from database
     */
    private fun loadTasks() {
        taskList.clear()
        taskList.addAll(dbHelper.getAllTasks())
        updateCategoryFilter() // Update category filter with any new categories
        updateStatistics() // Update statistics display
        applyFilters() // Apply filters after loading

        logger.info(TAG, "Loaded ${taskList.size} tasks")
    }

    /**
     * Update statistics display at top of screen
     */
    private fun updateStatistics() {
        val todayCount = dbHelper.getTasksCompletedToday()
        val weekCount = dbHelper.getTasksCompletedLast7Days()
        val overdueCount = dbHelper.getOverdueTasksCount()

        val statsText = "Today: $todayCount | Week: $weekCount | Overdue: $overdueCount"

        statisticsText.text = statsText

        // Highlight if there are overdue tasks
        statisticsText.setTextColor(
            if (overdueCount > 0) 0xFFFFAA00.toInt() // Orange for warning
            else 0xFFFFFFFF.toInt() // White
        )
    }

    /**
     * Update category filter spinner with all categories from database
     */
    private fun updateCategoryFilter() {
        // Get all unique categories from database
        allCategories = dbHelper.getAllCategories()

        // Build options for category filter spinner
        val categoryOptions = Array(allCategories.size + 1) { i ->
            if (i == 0) "All Categories" else allCategories[i - 1]
        }

        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        categoryFilterSpinner.adapter = categoryAdapter

        // Restore previous selection if possible
        categoryFilter?.let { filter ->
            val index = allCategories.indexOf(filter) + 1
            if (index > 0 && index < categoryOptions.size) {
                categoryFilterSpinner.setSelection(index)
            }
        }

        categoryFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                categoryFilter = if (position == 0) null else allCategories[position - 1]
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Show dialog for adding a new task
     */
    private fun showAddTaskDialog() {
        dialogHelper.showAddTaskDialog(allCategories)
    }

    /**
     * Show dialog for editing an existing task
     */
    private fun showEditTaskDialog(existingTask: Task) {
        dialogHelper.showEditTaskDialog(existingTask, allCategories)
    }

    /**
     * Show completion dialog with time tracking
     */
    private fun showCompletionDialog(task: Task) {
        dialogHelper.showCompletionDialog(task)
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbHelper.close()
    }

    // ========== TaskActionListener Interface Implementation ==========

    override fun onTaskCheckChanged(task: Task, isChecked: Boolean) {
        logger.info(TAG, "Task checkbox changed: ${task.title} -> $isChecked")

        if (isChecked) {
            // Show completion dialog
            showCompletionDialog(task)
        } else {
            // Unchecked - mark as incomplete
            task.isCompleted = false
            dbHelper.updateTask(task)
            loadTasks()
        }
    }

    override fun onTaskEdit(task: Task) {
        logger.info(TAG, "Edit task: ${task.title}")
        showEditTaskDialog(task)
    }

    override fun onTaskDelete(task: Task) {
        logger.info(TAG, "Delete task: ${task.title}")
        dbHelper.deleteTask(task.id)
        loadTasks()
    }

    override fun onTasksChanged() {
        logger.info(TAG, "Tasks changed - reloading")
        loadTasks()
    }
}

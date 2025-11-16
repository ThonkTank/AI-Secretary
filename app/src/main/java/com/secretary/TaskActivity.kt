package com.secretary

import com.secretary.core.logging.AppLogger
import com.secretary.features.tasks.data.TaskDao
import com.secretary.features.tasks.data.repository.TaskRepositoryImpl
import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel
import com.secretary.shared.database.TaskDatabase
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

/**
 * Task Activity - Main Task Management UI
 * Phase 4.5.3 Wave 9: Converted to Kotlin
 *
 * Displays task list with search, filtering, and sorting.
 * Delegates dialog management to TaskDialogHelper.
 * Implements TaskActionListener for adapter callbacks.
 */
class TaskActivity : AppCompatActivity(), TaskListAdapter.TaskActionListener {

    companion object {
        private const val TAG = "TaskActivity"
    }

    // Dependencies
    private lateinit var dbHelper: TaskDatabaseHelper // Legacy - for TaskDialogHelper (Step 6)
    private lateinit var repository: TaskRepository // NEW - for Task CRUD operations
    private lateinit var viewModel: TaskListViewModel // ViewModel for MVVM pattern
    private lateinit var dialogHelper: TaskDialogHelper
    private lateinit var filterManager: TaskFilterManager

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
            AppLogger.initialize(this)
            AppLogger.info(TAG, "TaskActivity started - setContentView successful")

            // Initialize components
            dbHelper = TaskDatabaseHelper(this) // Legacy - for TaskDialogHelper

            // Initialize Room database and repository
            val database = TaskDatabase.getDatabase(this)
            val taskDao = database.taskDao()
            repository = TaskRepositoryImpl(taskDao)

            // Initialize Services for domain logic
            val streakService = com.secretary.features.tasks.domain.service.StreakService()
            val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()

            // Initialize ViewModel with Factory (dependency injection)
            val viewModelFactory = com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory(
                repository,
                streakService,
                recurrenceService
            )
            viewModel = androidx.lifecycle.ViewModelProvider(this, viewModelFactory)
                .get(com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel::class.java)

            dialogHelper = TaskDialogHelper(this, dbHelper)
            setupDialogHelperListeners()

            // Setup ViewModel observers (MVVM pattern)
            setupViewModelObservers()

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

            AppLogger.info(TAG, "All views found successfully")

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
                AppLogger.info(TAG, "Add task button clicked")
                showAddTaskDialog()
            }

            // Initial load
            loadTasks()

            AppLogger.info(TAG, "TaskActivity onCreate completed successfully")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "FATAL ERROR in TaskActivity.onCreate()", e)
            e.printStackTrace() // Print full stack trace to logcat

            try {
                AppLogger.initialize(this) // Ensure logger is initialized
                AppLogger.error(TAG, "TaskActivity onCreate crashed: ${e.javaClass.name}: ${e.message}", e)
            } catch (logErr: Exception) {
                android.util.Log.e(TAG, "Could not log error", logErr)
            }

            // Show error dialog with full stack trace
            val stackTrace = e.stackTraceToString()
            AlertDialog.Builder(this)
                .setTitle("TaskActivity Crash")
                .setMessage("Error: ${e.javaClass.simpleName}\n${e.message}\n\nStack trace:\n${stackTrace.take(500)}")
                .setPositiveButton("Close App") { _, _ ->
                    finish()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                .setCancelable(false)
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
     * Setup LiveData observers for ViewModel (MVVM pattern)
     */
    private fun setupViewModelObservers() {
        // Observe tasks - update task list and apply filters
        viewModel.tasks.observe(this) { tasks ->
            taskList.clear()
            taskList.addAll(tasks)
            lifecycleScope.launch {
                updateCategoryFilter() // Update category filter with new categories
                updateStatistics() // Update statistics display (async, uses repository)
            }
            applyFilters() // Apply current filters to show filtered list
            AppLogger.info(TAG, "ViewModel: Loaded ${tasks.size} tasks")
        }

        // Observe error - show error Toast
        viewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, "Error: $it", Toast.LENGTH_LONG).show()
                AppLogger.error(TAG, "ViewModel Error: $it")
                viewModel.clearError() // Clear after showing
            }
        }

        // Observe operation success - show success Toast
        viewModel.operationSuccess.observe(this) { successMessage ->
            successMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                AppLogger.info(TAG, "ViewModel Success: $it")
                viewModel.clearOperationSuccess() // Clear after showing
            }
        }
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
        AppLogger.info(TAG, "Filters applied: ${filteredTaskList.size} tasks shown")
    }

    /**
     * Load all tasks from database via ViewModel
     * The ViewModel observer will handle updating the UI automatically
     */
    private fun loadTasks() {
        viewModel.loadTasks()
    }

    /**
     * Update statistics display at top of screen using Repository
     */
    private suspend fun updateStatistics() {
        try {
            val todayCount = repository.getTasksCompletedToday()
            val weekCount = repository.getTasksCompletedLast7Days()
            val overdueCount = repository.getOverdueTasksCount()

            val statsText = "Today: $todayCount | Week: $weekCount | Overdue: $overdueCount"

            statisticsText.text = statsText

            // Highlight if there are overdue tasks
            statisticsText.setTextColor(
                if (overdueCount > 0) 0xFFFFAA00.toInt() // Orange for warning
                else 0xFFFFFFFF.toInt() // White
            )
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to update statistics from Repository", e)
        }
    }

    /**
     * Update category filter spinner with all categories from database using Repository
     */
    private suspend fun updateCategoryFilter() {
        try {
            // Get all unique categories from database
            allCategories = repository.getAllCategories()

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
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to update category filter from Repository", e)
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
        AppLogger.info(TAG, "Task checkbox changed: ${task.title} -> $isChecked")

        if (isChecked) {
            // Show completion dialog
            showCompletionDialog(task)
        } else {
            // Unchecked - mark as incomplete
            task.isCompleted = false
            lifecycleScope.launch {
                try {
                    repository.updateTask(task)
                    loadTasks()
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Failed to update task via Repository", e)
                }
            }
        }
    }

    override fun onTaskEdit(task: Task) {
        AppLogger.info(TAG, "Edit task: ${task.title}")
        showEditTaskDialog(task)
    }

    override fun onTaskDelete(task: Task) {
        AppLogger.info(TAG, "Delete task: ${task.title}")
        lifecycleScope.launch {
            try {
                repository.deleteTask(task.id)
                loadTasks()
            } catch (e: Exception) {
                AppLogger.error(TAG, "Failed to delete task via Repository", e)
            }
        }
    }

    override fun onTasksChanged() {
        AppLogger.info(TAG, "Tasks changed - reloading")
        loadTasks()
    }
}

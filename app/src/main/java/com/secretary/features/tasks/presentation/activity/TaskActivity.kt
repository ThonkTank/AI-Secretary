package com.secretary.features.tasks.presentation.activity

import com.secretary.R
import com.secretary.features.tasks.domain.model.Task
import com.secretary.features.tasks.presentation.util.TaskFilterManager
import com.secretary.features.tasks.presentation.adapter.TaskListAdapter
import com.secretary.core.logging.AppLogger
import com.secretary.features.motivation.presentation.StreakColorUtil
import com.secretary.features.statistics.data.CompletionRepositoryImpl
import com.secretary.features.statistics.domain.model.TaskStatistics
import com.secretary.features.statistics.domain.repository.CompletionRepository
import com.secretary.features.tasks.data.TaskDao
import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel
import com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory
import com.secretary.shared.database.TaskDatabase
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.util.*

/**
 * Task Activity - Main Task Management UI
 * Phase 4.5.6: Dialog Extraction - Uses DialogFragments with MVVM
 * Phase 4.5.3 Wave 9: Converted to Kotlin
 *
 * Displays task list with search, filtering, and sorting.
 * Uses AddTaskDialog, EditTaskDialog, CompletionDialog (DialogFragments).
 * Implements TaskActionListener for adapter callbacks.
 */
class TaskActivity : AppCompatActivity(), TaskListAdapter.TaskActionListener {

    companion object {
        private const val TAG = "TaskActivity"
    }

    // Dependencies
    private lateinit var repository: TaskRepository // NEW - for Task CRUD operations
    private lateinit var viewModel: TaskListViewModel // ViewModel for MVVM pattern
    private lateinit var filterManager: TaskFilterManager
    private lateinit var viewModelFactory: TaskViewModelFactory // Factory for DialogFragments

    // Views
    private lateinit var taskListView: ListView
    private lateinit var emptyTasksText: TextView
    private lateinit var addTaskButton: Button
    private lateinit var searchEditText: EditText
    private lateinit var statusFilterSpinner: Spinner
    private lateinit var priorityFilterSpinner: Spinner
    private lateinit var categoryFilterSpinner: Spinner
    private lateinit var sortBySpinner: Spinner

    // Motivation UI components
    private lateinit var flameIcon: ImageView
    private lateinit var streakCurrentText: TextView
    private lateinit var streakBestText: TextView
    private lateinit var streakProgressIndicator: CircularProgressIndicator
    private lateinit var todayProgressIndicator: CircularProgressIndicator
    private lateinit var todayPercentText: TextView
    private lateinit var todayDetailText: TextView
    private lateinit var weekProgressIndicator: CircularProgressIndicator
    private lateinit var weekPercentText: TextView
    private lateinit var weekDetailText: TextView
    private lateinit var motivationalMessageText: TextView

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

            // Initialize Room database and repositories
            val database = TaskDatabase.getDatabase(this)
            val taskDao = database.taskDao()
            repository = com.secretary.features.tasks.data.repository.TaskRepositoryImpl(taskDao)

            // Phase 4: Initialize CompletionRepository for statistics
            val completionDao = database.completionDao()
            val completionRepository: CompletionRepository = CompletionRepositoryImpl(completionDao)

            // Initialize Services for domain logic
            val streakService = com.secretary.features.tasks.domain.service.StreakService()
            val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()

            // Initialize MotivationalMessageService for DI
            val motivationalMessageService = com.secretary.features.motivation.domain.MotivationalMessageService()

            // Initialize ViewModel with Factory (dependency injection)
            viewModelFactory = com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory(
                repository,
                completionRepository,
                streakService,
                recurrenceService,
                motivationalMessageService
            )
            viewModel = androidx.lifecycle.ViewModelProvider(this, viewModelFactory)
                .get(com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel::class.java)

            // Setup FragmentResult listeners for DialogFragments
            setupFragmentResultListeners()

            // Setup ViewModel observers (MVVM pattern)
            setupViewModelObservers()

            // Find views
            taskListView = findViewById(R.id.taskListView)
            emptyTasksText = findViewById(R.id.emptyTasksText)
            addTaskButton = findViewById(R.id.addTaskButton)
            searchEditText = findViewById(R.id.searchEditText)
            statusFilterSpinner = findViewById(R.id.statusFilterSpinner)
            priorityFilterSpinner = findViewById(R.id.priorityFilterSpinner)
            categoryFilterSpinner = findViewById(R.id.categoryFilterSpinner)
            sortBySpinner = findViewById(R.id.sortBySpinner)

            // Find motivation UI components
            flameIcon = findViewById(R.id.flameIcon)
            streakCurrentText = findViewById(R.id.streakCurrentText)
            streakBestText = findViewById(R.id.streakBestText)
            streakProgressIndicator = findViewById(R.id.streakProgressIndicator)
            todayProgressIndicator = findViewById(R.id.todayProgressIndicator)
            todayPercentText = findViewById(R.id.todayPercentText)
            todayDetailText = findViewById(R.id.todayDetailText)
            weekProgressIndicator = findViewById(R.id.weekProgressIndicator)
            weekPercentText = findViewById(R.id.weekPercentText)
            weekDetailText = findViewById(R.id.weekDetailText)
            motivationalMessageText = findViewById(R.id.motivationalMessageText)

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
    /**
     * Setup FragmentResult listeners for DialogFragments
     * Phase 4.5.6: Replaces setupDialogHelperListeners()
     */
    private fun setupFragmentResultListeners() {
        // Listen for AddTaskDialog results
        supportFragmentManager.setFragmentResultListener(
            com.secretary.features.tasks.presentation.dialog.AddTaskDialog.RESULT_KEY,
            this
        ) { _, _ ->
            // Task was saved - reload task list
            loadTasks()
        }

        // Listen for EditTaskDialog results
        supportFragmentManager.setFragmentResultListener(
            com.secretary.features.tasks.presentation.dialog.EditTaskDialog.RESULT_KEY,
            this
        ) { _, _ ->
            // Task was updated - reload task list
            loadTasks()
        }

        // Listen for CompletionDialog results
        supportFragmentManager.setFragmentResultListener(
            com.secretary.features.tasks.presentation.dialog.CompletionDialog.RESULT_KEY,
            this
        ) { _, bundle ->
            // Task was completed or cancelled - reload task list
            val cancelled = bundle.getBoolean("cancelled", false)
            if (cancelled) {
                // User cancelled - reload to reset UI
                loadTasks()
            } else {
                // Task completed - reload task list
                loadTasks()
            }
        }
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
            }
            applyFilters() // Apply current filters to show filtered list
            AppLogger.info(TAG, "ViewModel: Loaded ${tasks.size} tasks")
        }

        // Phase 4: Observe statistics - update statistics display
        viewModel.statistics.observe(this) { stats ->
            stats?.let {
                AppLogger.info(TAG, "Statistics updated: ${it.toDisplayString()}")
                updateStreakIndicator(it.highestActiveStreak, it.longestStreakEver)
                updateCompletionRates(it)
            }
        }

        // Observe motivational message - update motivational message display
        viewModel.motivationalMessage.observe(this) { message ->
            motivationalMessageText.text = message
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
     * TODO: Update to use new motivation UI components
     */
    private suspend fun updateStatistics() {
        try {
            val todayCount = repository.getTasksCompletedToday()
            val weekCount = repository.getTasksCompletedLast7Days()
            val overdueCount = repository.getOverdueTasksCount()

            // TODO: Update streak indicator, completion rates, and motivational message
            AppLogger.info(TAG, "Statistics: Today=$todayCount, Week=$weekCount, Overdue=$overdueCount")
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
     * Phase 4.5.6: Uses AddTaskDialog DialogFragment
     */
    private fun showAddTaskDialog() {
        val dialog = com.secretary.features.tasks.presentation.dialog.AddTaskDialog.newInstance(
            allCategories,
            viewModelFactory
        )
        dialog.show(supportFragmentManager, com.secretary.features.tasks.presentation.dialog.AddTaskDialog.TAG)
    }

    /**
     * Show dialog for editing an existing task
     * Phase 4.5.6: Uses EditTaskDialog DialogFragment
     */
    private fun showEditTaskDialog(existingTask: Task) {
        val dialog = com.secretary.features.tasks.presentation.dialog.EditTaskDialog.newInstance(
            existingTask.id,
            allCategories,
            viewModelFactory
        )
        dialog.show(supportFragmentManager, com.secretary.features.tasks.presentation.dialog.EditTaskDialog.TAG)
    }

    /**
     * Show completion dialog with time tracking
     * Phase 4.5.6: Uses CompletionDialog DialogFragment
     */
    private fun showCompletionDialog(task: Task) {
        val dialog = com.secretary.features.tasks.presentation.dialog.CompletionDialog.newInstance(
            task,
            viewModelFactory
        )
        dialog.show(supportFragmentManager, com.secretary.features.tasks.presentation.dialog.CompletionDialog.TAG)
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    // Note: Room database is managed automatically by TaskDatabase.getDatabase() singleton
    // No need to manually close database connections

    // ========== TaskActionListener Interface Implementation ==========

    override fun onTaskCheckChanged(task: Task, isChecked: Boolean) {
        AppLogger.info(TAG, "Task checkbox changed: ${task.title} -> $isChecked")

        if (isChecked) {
            // Show completion dialog
            showCompletionDialog(task)
        } else {
            // Unchecked - mark as incomplete
            task.isCompleted = false
            viewModel.updateTask(task)
        }
    }

    override fun onTaskEdit(task: Task) {
        AppLogger.info(TAG, "Edit task: ${task.title}")
        showEditTaskDialog(task)
    }

    override fun onTaskDelete(task: Task) {
        AppLogger.info(TAG, "Delete task: ${task.title}")
        viewModel.deleteTask(task.id)
    }

    override fun onTasksChanged() {
        AppLogger.info(TAG, "Tasks changed - reloading")
        loadTasks()
    }

    // ========== Helper Methods for Motivation UI ==========

    /**
     * Update the streak indicator with current and best streak values
     * Updates the flame icon color, progress indicator, and text displays
     *
     * @param current Current active streak in days
     * @param best Best (longest) streak ever achieved
     */
    private fun updateStreakIndicator(current: Int, best: Int) {
        streakCurrentText.text = "Current: $current days"
        streakBestText.text = "Best: $best days"

        val progress = if (best > 0) (current * 100 / best) else 0
        streakProgressIndicator.progress = progress

        val colorRes = StreakColorUtil.getStreakColor(current)
        flameIcon.setColorFilter(ContextCompat.getColor(this, colorRes))
        streakProgressIndicator.setIndicatorColor(ContextCompat.getColor(this, colorRes))
    }

    /**
     * Update completion rate displays for today and this week
     * Updates progress indicators, percentages, and detail text
     *
     * @param stats TaskStatistics containing completion data
     */
    private fun updateCompletionRates(stats: TaskStatistics) {
        // Today
        val todayPercent = stats.getTodayCompletionRate()
        todayProgressIndicator.progress = todayPercent
        todayPercentText.text = "$todayPercent%"
        todayDetailText.text = "${stats.completedToday} of ${stats.totalTasks} tasks"
        val todayColor = StreakColorUtil.getCompletionColor(todayPercent)
        todayProgressIndicator.setIndicatorColor(ContextCompat.getColor(this, todayColor))

        // Week
        val weekPercent = stats.getWeekCompletionRate()
        weekProgressIndicator.progress = weekPercent
        weekPercentText.text = "$weekPercent%"
        weekDetailText.text = "${stats.completedThisWeek} of ${stats.totalTasks} tasks"
        val weekColor = StreakColorUtil.getCompletionColor(weekPercent)
        weekProgressIndicator.setIndicatorColor(ContextCompat.getColor(this, weekColor))
    }
}

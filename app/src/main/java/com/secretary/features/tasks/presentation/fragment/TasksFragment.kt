package com.secretary.features.tasks.presentation.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.secretary.R
import com.secretary.core.logging.AppLogger
import com.secretary.features.motivation.domain.MotivationalMessageService
import com.secretary.features.motivation.presentation.StreakColorUtil
import com.secretary.features.statistics.data.CompletionRepositoryImpl
import com.secretary.features.statistics.domain.model.TaskStatistics
import com.secretary.features.statistics.domain.repository.CompletionRepository
import com.secretary.features.tasks.data.repository.TaskRepositoryImpl
import com.secretary.features.tasks.domain.model.Task
import com.secretary.features.tasks.domain.repository.TaskRepository
import com.secretary.features.tasks.domain.service.RecurrenceService
import com.secretary.features.tasks.domain.service.StreakService
import com.secretary.features.tasks.presentation.adapter.TaskListAdapter
import com.secretary.features.tasks.presentation.dialog.AddTaskDialog
import com.secretary.features.tasks.presentation.dialog.CompletionDialog
import com.secretary.features.tasks.presentation.dialog.EditTaskDialog
import com.secretary.features.tasks.presentation.util.TaskFilterManager
import com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel
import com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch
import java.util.*

/**
 * Tasks Fragment - Full task management UI embedded in BottomNavigation.
 * Replaces the need for separate TaskActivity.
 *
 * Features:
 * - Task list with search, filtering, and sorting
 * - Streak and completion rate indicators
 * - Motivational messages
 * - Add/Edit/Complete task dialogs
 */
class TasksFragment : Fragment(), TaskListAdapter.TaskActionListener {

    companion object {
        private const val TAG = "TasksFragment"
        fun newInstance() = TasksFragment()
    }

    // Dependencies
    private lateinit var repository: TaskRepository
    private lateinit var viewModel: TaskListViewModel
    private lateinit var filterManager: TaskFilterManager
    private lateinit var viewModelFactory: TaskViewModelFactory

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
    private var statusFilter = 0
    private var priorityFilter = -1
    private var categoryFilter: String? = null
    private var sortOption = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            AppLogger.info(TAG, "TasksFragment started")

            // Initialize Room database and repositories
            val database = TaskDatabase.getDatabase(requireContext())
            val taskDao = database.taskDao()
            repository = TaskRepositoryImpl(taskDao)

            val completionDao = database.completionDao()
            val completionRepository: CompletionRepository = CompletionRepositoryImpl(completionDao)

            // Initialize Services
            val streakService = StreakService()
            val recurrenceService = RecurrenceService()
            val motivationalMessageService = MotivationalMessageService()

            // Initialize ViewModel
            viewModelFactory = TaskViewModelFactory(
                repository,
                completionRepository,
                streakService,
                recurrenceService,
                motivationalMessageService
            )
            viewModel = ViewModelProvider(this, viewModelFactory)[TaskListViewModel::class.java]

            // Setup FragmentResult listeners
            setupFragmentResultListeners()

            // Setup ViewModel observers
            setupViewModelObservers()

            // Find views
            taskListView = view.findViewById(R.id.taskListView)
            emptyTasksText = view.findViewById(R.id.emptyTasksText)
            addTaskButton = view.findViewById(R.id.addTaskButton)
            searchEditText = view.findViewById(R.id.searchEditText)
            statusFilterSpinner = view.findViewById(R.id.statusFilterSpinner)
            priorityFilterSpinner = view.findViewById(R.id.priorityFilterSpinner)
            categoryFilterSpinner = view.findViewById(R.id.categoryFilterSpinner)
            sortBySpinner = view.findViewById(R.id.sortBySpinner)

            // Find motivation UI components
            flameIcon = view.findViewById(R.id.flameIcon)
            streakCurrentText = view.findViewById(R.id.streakCurrentText)
            streakBestText = view.findViewById(R.id.streakBestText)
            streakProgressIndicator = view.findViewById(R.id.streakProgressIndicator)
            todayProgressIndicator = view.findViewById(R.id.todayProgressIndicator)
            todayPercentText = view.findViewById(R.id.todayPercentText)
            todayDetailText = view.findViewById(R.id.todayDetailText)
            weekProgressIndicator = view.findViewById(R.id.weekProgressIndicator)
            weekPercentText = view.findViewById(R.id.weekPercentText)
            weekDetailText = view.findViewById(R.id.weekDetailText)
            motivationalMessageText = view.findViewById(R.id.motivationalMessageText)

            // Setup adapter
            adapter = TaskListAdapter(requireActivity(), filteredTaskList, this)
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

            AppLogger.info(TAG, "TasksFragment onViewCreated completed")

        } catch (e: Exception) {
            AppLogger.error(TAG, "TasksFragment initialization failed", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFragmentResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            AddTaskDialog.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            loadTasks()
        }

        parentFragmentManager.setFragmentResultListener(
            EditTaskDialog.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            loadTasks()
        }

        parentFragmentManager.setFragmentResultListener(
            CompletionDialog.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val cancelled = bundle.getBoolean("cancelled", false)
            loadTasks()
        }
    }

    private fun setupViewModelObservers() {
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            taskList.clear()
            taskList.addAll(tasks)
            lifecycleScope.launch {
                updateCategoryFilter()
            }
            applyFilters()
            AppLogger.info(TAG, "ViewModel: Loaded ${tasks.size} tasks")
        }

        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                updateStreakIndicator(it.highestActiveStreak, it.longestStreakEver)
                updateCompletionRates(it)
            }
        }

        viewModel.motivationalMessage.observe(viewLifecycleOwner) { message ->
            motivationalMessageText.text = message
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.operationSuccess.observe(viewLifecycleOwner) { successMessage ->
            successMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearOperationSuccess()
            }
        }
    }

    private fun setupFilterSpinners() {
        val statusOptions = arrayOf("All Tasks", "Active Only", "Completed Only")
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusOptions).apply {
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

        val priorityOptions = arrayOf("All Priorities", "Low", "Medium", "High")
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, priorityOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        priorityFilterSpinner.adapter = priorityAdapter
        priorityFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                priorityFilter = position - 1
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val sortOptions = arrayOf("Sort: Priority", "Sort: Due Date", "Sort: Category", "Sort: Created", "Sort: Title")
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sortBySpinner.adapter = sortAdapter
        sortBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortOption = position
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

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

    private fun applyFilters() {
        filterManager.searchQuery = searchQuery
        filterManager.categoryFilter = categoryFilter

        val completionFilter = when (statusFilter) {
            1 -> TaskFilterManager.CompletionFilter.ACTIVE_ONLY
            2 -> TaskFilterManager.CompletionFilter.COMPLETED_ONLY
            else -> TaskFilterManager.CompletionFilter.ALL
        }
        filterManager.completionFilter = completionFilter

        val sortOptions = TaskFilterManager.SortOption.values()
        if (sortOption in sortOptions.indices) {
            filterManager.sortOption = sortOptions[sortOption]
        }

        filteredTaskList.clear()
        filteredTaskList.addAll(filterManager.applyFilters(taskList))
        filterManager.sortTasks(filteredTaskList)

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
    }

    private fun loadTasks() {
        viewModel.loadTasks()
    }

    private suspend fun updateCategoryFilter() {
        try {
            allCategories = repository.getAllCategories()

            val categoryOptions = Array(allCategories.size + 1) { i ->
                if (i == 0) "All Categories" else allCategories[i - 1]
            }

            val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            categoryFilterSpinner.adapter = categoryAdapter

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
            AppLogger.error(TAG, "Failed to update category filter", e)
        }
    }

    private fun showAddTaskDialog() {
        val dialog = AddTaskDialog.newInstance(allCategories, viewModelFactory)
        dialog.show(parentFragmentManager, AddTaskDialog.TAG)
    }

    private fun showEditTaskDialog(existingTask: Task) {
        val dialog = EditTaskDialog.newInstance(existingTask.id, allCategories, viewModelFactory)
        dialog.show(parentFragmentManager, EditTaskDialog.TAG)
    }

    private fun showCompletionDialog(task: Task) {
        val dialog = CompletionDialog.newInstance(task, viewModelFactory)
        dialog.show(parentFragmentManager, CompletionDialog.TAG)
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }

    // TaskActionListener Implementation
    override fun onTaskCheckChanged(task: Task, isChecked: Boolean) {
        AppLogger.info(TAG, "Task checkbox changed: ${task.title} -> $isChecked")
        if (isChecked) {
            showCompletionDialog(task)
        } else {
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

    // Motivation UI Helpers
    private fun updateStreakIndicator(current: Int, best: Int) {
        streakCurrentText.text = "Current: $current days"
        streakBestText.text = "Best: $best days"

        val progress = if (best > 0) (current * 100 / best) else 0
        streakProgressIndicator.progress = progress

        val colorRes = StreakColorUtil.getStreakColor(current)
        flameIcon.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
        streakProgressIndicator.setIndicatorColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun updateCompletionRates(stats: TaskStatistics) {
        val todayPercent = stats.getTodayCompletionRate()
        todayProgressIndicator.progress = todayPercent
        todayPercentText.text = "$todayPercent%"
        todayDetailText.text = "${stats.completedToday} of ${stats.totalTasks} tasks"
        val todayColor = StreakColorUtil.getCompletionColor(todayPercent)
        todayProgressIndicator.setIndicatorColor(ContextCompat.getColor(requireContext(), todayColor))

        val weekPercent = stats.getWeekCompletionRate()
        weekProgressIndicator.progress = weekPercent
        weekPercentText.text = "$weekPercent%"
        weekDetailText.text = "${stats.completedThisWeek} of ${stats.totalTasks} tasks"
        val weekColor = StreakColorUtil.getCompletionColor(weekPercent)
        weekProgressIndicator.setIndicatorColor(ContextCompat.getColor(requireContext(), weekColor))
    }
}

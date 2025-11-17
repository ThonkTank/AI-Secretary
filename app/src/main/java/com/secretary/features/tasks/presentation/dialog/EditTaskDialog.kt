package com.secretary.features.tasks.presentation.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import com.secretary.R
import com.secretary.Task
import com.secretary.features.tasks.presentation.viewmodel.TaskDetailViewModel
import com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * DialogFragment for editing an existing task
 * Phase 4.5.6: Dialog Extraction
 *
 * Replaces TaskDialogHelper.showEditTaskDialog()
 * Uses TaskDetailViewModel for MVVM pattern
 */
class EditTaskDialog : DialogFragment() {

    private lateinit var viewModel: TaskDetailViewModel
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private var selectedDueDate = 0L
    private var taskId: Long = 0

    // UI Views (lateinit to avoid nullable overhead)
    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var categoryInput: AutoCompleteTextView
    private lateinit var dueDateText: TextView
    private lateinit var prioritySpinner: Spinner
    private lateinit var recurrenceCheckBox: CheckBox
    private lateinit var recurrenceOptionsLayout: LinearLayout
    private lateinit var recurrenceTypeSpinner: Spinner
    private lateinit var recurrenceAmountInput: EditText
    private lateinit var recurrenceUnitSpinner: Spinner

    companion object {
        const val TAG = "EditTaskDialog"
        const val RESULT_KEY = "task_updated"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_CATEGORIES = "categories"

        fun newInstance(taskId: Long, categories: List<String>, factory: TaskViewModelFactory): EditTaskDialog {
            return EditTaskDialog().apply {
                arguments = bundleOf(
                    EXTRA_TASK_ID to taskId,
                    EXTRA_CATEGORIES to ArrayList(categories)
                )
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        taskId = arguments?.getLong(EXTRA_TASK_ID) ?: 0

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        setupViewModel()
        setupViews(dialogView)

        // Load existing task
        if (taskId > 0) {
            viewModel.loadTask(taskId)
        }

        return dialog
    }

    private fun setupViewModel() {
        // Recreate dependencies (same as AddTaskDialog)
        val database = com.secretary.shared.database.TaskDatabase.getDatabase(requireContext())
        val taskDao = database.taskDao()
        val repository = com.secretary.features.tasks.data.repository.TaskRepositoryImpl(taskDao)
        val streakService = com.secretary.features.tasks.domain.service.StreakService()
        val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()

        val factory = TaskViewModelFactory(repository, streakService, recurrenceService)
        viewModel = ViewModelProvider(this, factory)[TaskDetailViewModel::class.java]

        // Observe task data (for pre-populating fields)
        viewModel.task.observe(this) { task ->
            task?.let { populateFields(it) }
        }

        // Observe save result
        viewModel.saveResult.observe(this) { result ->
            result?.let {
                it.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Task updated successfully", Toast.LENGTH_SHORT).show()
                        setFragmentResult(RESULT_KEY, bundleOf("success" to true))
                        dismiss()
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            requireContext(),
                            exception.message ?: "Failed to update task",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // Observe validation errors
        viewModel.validationError.observe(this) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews(dialogView: View) {
        // Find views
        titleInput = dialogView.findViewById(R.id.taskTitleInput)
        descriptionInput = dialogView.findViewById(R.id.taskDescriptionInput)
        categoryInput = dialogView.findViewById(R.id.taskCategoryInput)
        dueDateText = dialogView.findViewById(R.id.taskDueDateText)
        val selectDueDateButton = dialogView.findViewById<Button>(R.id.selectDueDateButton)
        val clearDueDateButton = dialogView.findViewById<Button>(R.id.clearDueDateButton)
        prioritySpinner = dialogView.findViewById(R.id.taskPrioritySpinner)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveTaskButton)

        // Recurrence views
        recurrenceCheckBox = dialogView.findViewById(R.id.recurrenceCheckBox)
        recurrenceOptionsLayout = dialogView.findViewById(R.id.recurrenceOptionsLayout)
        recurrenceTypeSpinner = dialogView.findViewById(R.id.recurrenceTypeSpinner)
        recurrenceAmountInput = dialogView.findViewById(R.id.recurrenceAmountInput)
        recurrenceUnitSpinner = dialogView.findViewById(R.id.recurrenceUnitSpinner)

        // Setup category autocomplete
        val categories = arguments?.getStringArrayList(EXTRA_CATEGORIES) ?: listOf("General")
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        categoryInput.setAdapter(categoryAdapter)

        // Setup due date picker
        selectDueDateButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            if (selectedDueDate > 0) {
                calendar.timeInMillis = selectedDueDate
            }

            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDueDate = calendar.timeInMillis
                    dueDateText.text = dateFormat.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Clear date button
        clearDueDateButton.setOnClickListener {
            selectedDueDate = 0
            dueDateText.text = "No due date"
        }

        // Setup priority spinner
        val priorities = arrayOf("Low", "Medium", "High")
        val priorityAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            priorities
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        prioritySpinner.adapter = priorityAdapter

        // Setup recurrence
        setupRecurrenceOptions(
            recurrenceCheckBox,
            recurrenceOptionsLayout,
            recurrenceTypeSpinner,
            recurrenceUnitSpinner
        )

        // Cancel button
        cancelButton.setOnClickListener { dismiss() }

        // Save button
        saveButton.text = "Update Task" // Change button text for edit
        saveButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val category = categoryInput.text.toString().trim()
            val priority = prioritySpinner.selectedItemPosition

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create updated task (preserve original ID and createdAt)
            val updatedTask = Task().apply {
                this.id = taskId
                this.title = title
                this.description = description
                this.category = if (category.isEmpty()) "General" else category
                this.priority = priority
                this.dueDate = selectedDueDate

                // Handle recurrence
                if (recurrenceCheckBox.isChecked) {
                    setupTaskRecurrence(this, recurrenceTypeSpinner, recurrenceAmountInput, recurrenceUnitSpinner)
                } else {
                    // Clear recurrence if checkbox unchecked
                    this.recurrenceType = Task.RECURRENCE_NONE
                    this.recurrenceAmount = 0
                    this.recurrenceUnit = 0
                }
            }

            // Update via ViewModel
            viewModel.saveTask(updatedTask)
        }
    }

    private fun populateFields(task: Task) {
        titleInput.setText(task.title)
        descriptionInput.setText(task.description)
        categoryInput.setText(task.category)
        prioritySpinner.setSelection(task.priority)

        // Set due date
        selectedDueDate = task.dueDate
        if (task.dueDate > 0) {
            dueDateText.text = dateFormat.format(task.dueDate)
        } else {
            dueDateText.text = "No due date"
        }

        // Set recurrence
        if (task.recurrenceType != Task.RECURRENCE_NONE) {
            recurrenceCheckBox.isChecked = true
            recurrenceTypeSpinner.setSelection(if (task.recurrenceType == Task.RECURRENCE_INTERVAL) 0 else 1)
            recurrenceAmountInput.setText(task.recurrenceAmount.toString())
            recurrenceUnitSpinner.setSelection(task.recurrenceUnit)
        }
    }

    private fun setupRecurrenceOptions(
        checkBox: CheckBox,
        layout: LinearLayout,
        typeSpinner: Spinner,
        unitSpinner: Spinner
    ) {
        // Setup recurrence type spinner
        val recurrenceTypes = arrayOf(
            "Interval (e.g., every 3 days)",
            "Frequency (e.g., 3 times per week)"
        )
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            recurrenceTypes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        typeSpinner.adapter = typeAdapter

        // Setup unit spinner
        val units = arrayOf("Day(s)", "Week(s)", "Month(s)")
        val unitAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            units
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        unitSpinner.adapter = unitAdapter

        // Toggle recurrence options visibility
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            layout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupTaskRecurrence(
        task: Task,
        typeSpinner: Spinner,
        amountInput: EditText,
        unitSpinner: Spinner
    ) {
        val recurrenceType = if (typeSpinner.selectedItemPosition == 0) {
            Task.RECURRENCE_INTERVAL
        } else {
            Task.RECURRENCE_FREQUENCY
        }

        val amount = try {
            val parsed = amountInput.text.toString().toInt()
            if (parsed < 1) 1 else parsed
        } catch (e: NumberFormatException) {
            1
        }

        task.recurrenceType = recurrenceType
        task.recurrenceAmount = amount
        task.recurrenceUnit = unitSpinner.selectedItemPosition

        // Initialize period for frequency tasks
        if (recurrenceType == Task.RECURRENCE_FREQUENCY) {
            task.currentPeriodStart = System.currentTimeMillis()
            task.completionsThisPeriod = 0
        }
    }
}

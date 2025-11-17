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
 * DialogFragment for adding a new task
 * Phase 4.5.6: Dialog Extraction
 *
 * Replaces TaskDialogHelper.showAddTaskDialog()
 * Uses TaskDetailViewModel for MVVM pattern
 */
class AddTaskDialog : DialogFragment() {

    private lateinit var viewModel: TaskDetailViewModel
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private var selectedDueDate = 0L

    companion object {
        const val TAG = "AddTaskDialog"
        const val RESULT_KEY = "task_saved"
        const val EXTRA_CATEGORIES = "categories"
        private const val KEY_FACTORY = "factory_key"

        fun newInstance(categories: List<String>, factory: TaskViewModelFactory): AddTaskDialog {
            return AddTaskDialog().apply {
                arguments = bundleOf(EXTRA_CATEGORIES to ArrayList(categories))
                // Note: Can't pass factory via Bundle, will get from Activity
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_task, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        setupViewModel()
        setupViews(dialogView)

        return dialog
    }

    private fun setupViewModel() {
        // Get factory from parent activity
        val activity = requireActivity() as? androidx.lifecycle.ViewModelStoreOwner
            ?: throw IllegalStateException("Parent activity must implement ViewModelStoreOwner")

        // Get the factory from the activity - it should have a public property or method
        // For now, recreate the dependencies (will be refactored later with proper DI)
        val database = com.secretary.shared.database.TaskDatabase.getDatabase(requireContext())
        val taskDao = database.taskDao()
        val repository = com.secretary.features.tasks.data.repository.TaskRepositoryImpl(taskDao)

        // Phase 4: Initialize CompletionRepository for statistics
        val completionDao = database.completionDao()
        val completionRepository = com.secretary.features.statistics.data.CompletionRepositoryImpl(completionDao)

        val streakService = com.secretary.features.tasks.domain.service.StreakService()
        val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()

        val factory = TaskViewModelFactory(repository, completionRepository, streakService, recurrenceService)
        viewModel = ViewModelProvider(this, factory)[TaskDetailViewModel::class.java]

        // Observe save result
        viewModel.saveResult.observe(this) { result ->
            result?.let {
                it.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Task added successfully", Toast.LENGTH_SHORT).show()
                        setFragmentResult(RESULT_KEY, bundleOf("success" to true))
                        dismiss()
                    },
                    onFailure = { exception ->
                        Toast.makeText(
                            requireContext(),
                            exception.message ?: "Failed to save task",
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
        val titleInput = dialogView.findViewById<EditText>(R.id.taskTitleInput)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.taskDescriptionInput)
        val categoryInput = dialogView.findViewById<AutoCompleteTextView>(R.id.taskCategoryInput)
        val dueDateText = dialogView.findViewById<TextView>(R.id.taskDueDateText)
        val selectDueDateButton = dialogView.findViewById<Button>(R.id.selectDueDateButton)
        val clearDueDateButton = dialogView.findViewById<Button>(R.id.clearDueDateButton)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.taskPrioritySpinner)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveTaskButton)

        // Recurrence views
        val recurrenceCheckBox = dialogView.findViewById<CheckBox>(R.id.recurrenceCheckBox)
        val recurrenceOptionsLayout = dialogView.findViewById<LinearLayout>(R.id.recurrenceOptionsLayout)
        val recurrenceTypeSpinner = dialogView.findViewById<Spinner>(R.id.recurrenceTypeSpinner)
        val recurrenceAmountInput = dialogView.findViewById<EditText>(R.id.recurrenceAmountInput)
        val recurrenceUnitSpinner = dialogView.findViewById<Spinner>(R.id.recurrenceUnitSpinner)

        // Setup category autocomplete
        val categories = arguments?.getStringArrayList(EXTRA_CATEGORIES) ?: listOf("General")
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        categoryInput.setAdapter(categoryAdapter)
        categoryInput.setText("General") // Default

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
        prioritySpinner.setSelection(1) // Default to Medium

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
        saveButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val category = categoryInput.text.toString().trim()
            val priority = prioritySpinner.selectedItemPosition

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a task title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create new task
            val newTask = Task().apply {
                this.title = title
                this.description = description
                this.category = if (category.isEmpty()) "General" else category
                this.priority = priority
                createdAt = System.currentTimeMillis()
                dueDate = selectedDueDate
            }

            // Handle recurrence
            if (recurrenceCheckBox.isChecked) {
                setupTaskRecurrence(newTask, recurrenceTypeSpinner, recurrenceAmountInput, recurrenceUnitSpinner)
            }

            // Save via ViewModel
            viewModel.saveTask(newTask)
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

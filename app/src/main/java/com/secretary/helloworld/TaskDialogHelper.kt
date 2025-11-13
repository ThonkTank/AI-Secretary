package com.secretary.helloworld

import com.secretary.helloworld.core.logging.AppLogger
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Helper class for managing task-related dialogs.
 * Phase 4.5.3 Wave 8: Converted to Kotlin
 *
 * Extracted from TaskActivity to reduce its size and improve organization.
 * Provides dialogs for task creation, editing, and completion tracking.
 */
class TaskDialogHelper(
    private val context: Activity,
    private val dbHelper: TaskDatabaseHelper
) {
    private val logger = AppLogger.getInstance(context)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // Listener interfaces
    interface OnTaskSavedListener {
        fun onTaskSaved(task: Task)
        fun onTasksNeedReload()
    }

    interface OnTaskCompletedListener {
        fun onTaskCompleted(task: Task)
        fun onCompletionCancelled(task: Task)
    }

    private var taskSavedListener: OnTaskSavedListener? = null
    private var taskCompletedListener: OnTaskCompletedListener? = null

    fun setOnTaskSavedListener(listener: OnTaskSavedListener?) {
        this.taskSavedListener = listener
    }

    fun setOnTaskCompletedListener(listener: OnTaskCompletedListener?) {
        this.taskCompletedListener = listener
    }

    /**
     * Show dialog for adding a new task
     */
    fun showAddTaskDialog(categories: List<String>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

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

        // Variable to track selected due date
        var selectedDueDate = 0L

        // Setup category autocomplete
        val categoryAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        categoryInput.setAdapter(categoryAdapter)
        categoryInput.setText("General") // Default

        // Setup due date picker
        setupDatePicker(selectDueDateButton, dueDateText) { date ->
            selectedDueDate = date
        }

        // Clear date button
        clearDueDateButton.setOnClickListener {
            selectedDueDate = 0
            dueDateText.text = "No due date"
        }

        // Setup priority spinner
        val priorities = arrayOf("Low", "Medium", "High")
        val priorityAdapter = ArrayAdapter(
            context,
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
        cancelButton.setOnClickListener { dialog.dismiss() }

        // Save button
        saveButton.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val category = categoryInput.text.toString().trim()
            val priority = prioritySpinner.selectedItemPosition

            if (title.isEmpty()) {
                Toast.makeText(context, "Please enter a task title", Toast.LENGTH_SHORT).show()
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

            // Save to database
            val taskId = dbHelper.insertTask(newTask)
            newTask.id = taskId

            logger.info("TaskDialogHelper", "New task created: $title")
            Toast.makeText(context, "Task added successfully", Toast.LENGTH_SHORT).show()

            dialog.dismiss()

            taskSavedListener?.onTaskSaved(newTask)
        }

        dialog.show()
    }

    /**
     * Show dialog for editing an existing task
     */
    fun showEditTaskDialog(existingTask: Task, categories: List<String>) {
        // This will be a simplified version for now
        // The full implementation would be similar to showAddTaskDialog
        // but with pre-populated fields

        AlertDialog.Builder(context)
            .setTitle("Edit Task")
            .setMessage("Edit functionality will be implemented here")
            .setPositiveButton("OK", null)
            .show()

        // TODO: Implement full edit dialog
    }

    /**
     * Show completion dialog with time tracking
     */
    fun showCompletionDialog(task: Task) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_completion, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Find views
        val taskTitleText = dialogView.findViewById<TextView>(R.id.completionTaskTitle)
        val timeSpentInput = dialogView.findViewById<EditText>(R.id.timeSpentInput)
        val averageTimeText = dialogView.findViewById<TextView>(R.id.averageTimeText)
        val difficultySeekBar = dialogView.findViewById<SeekBar>(R.id.difficultySeekBar)
        val difficultyValueText = dialogView.findViewById<TextView>(R.id.difficultyValueText)
        val notesInput = dialogView.findViewById<EditText>(R.id.completionNotesInput)
        val quickCompleteCheckBox = dialogView.findViewById<CheckBox>(R.id.quickCompleteCheckBox)
        val skipButton = dialogView.findViewById<Button>(R.id.skipDetailsButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveCompletionButton)

        // Set task title
        taskTitleText.text = task.title

        // Show average time if available
        val avgTime = dbHelper.getAverageCompletionTime(task.id)
        if (avgTime > 0) {
            averageTimeText.text = "(Avg: $avgTime min)"
            averageTimeText.visibility = android.view.View.VISIBLE
        }

        // Setup difficulty seekbar
        difficultySeekBar.max = 10
        difficultySeekBar.progress = 5 // Default to medium
        difficultyValueText.text = "5"

        difficultySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                difficultyValueText.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Quick complete checkbox
        quickCompleteCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val visibility = if (isChecked) android.view.View.GONE else android.view.View.VISIBLE
            timeSpentInput.visibility = visibility
            averageTimeText.visibility = visibility
            difficultySeekBar.visibility = visibility
            difficultyValueText.visibility = visibility
            notesInput.visibility = visibility
        }

        // Skip button - complete without tracking
        skipButton.setOnClickListener {
            completeTaskBasic(task)
            dialog.dismiss()
        }

        // Save button - complete with tracking
        saveButton.setOnClickListener {
            if (quickCompleteCheckBox.isChecked) {
                completeTaskBasic(task)
            } else {
                val timeSpent = try {
                    timeSpentInput.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    0
                }

                val difficulty = difficultySeekBar.progress
                val notes = notesInput.text.toString().trim()

                completeTaskWithTracking(task, timeSpent, difficulty, notes)
            }
            dialog.dismiss()
        }

        // Handle dialog cancel
        dialog.setOnCancelListener {
            taskCompletedListener?.onCompletionCancelled(task)
        }

        dialog.show()
    }

    // ========== Helper Methods ==========

    /**
     * Setup date picker dialog
     */
    private fun setupDatePicker(button: Button, display: TextView, onDateSelected: (Long) -> Unit) {
        var selectedDate = 0L

        button.setOnClickListener {
            val calendar = Calendar.getInstance()
            if (selectedDate > 0) {
                calendar.timeInMillis = selectedDate
            }

            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.timeInMillis
                    display.text = dateFormat.format(calendar.time)
                    onDateSelected(selectedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    /**
     * Setup recurrence options UI
     */
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
            context,
            android.R.layout.simple_spinner_item,
            recurrenceTypes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        typeSpinner.adapter = typeAdapter

        // Setup unit spinner
        val units = arrayOf("Day(s)", "Week(s)", "Month(s)")
        val unitAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            units
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        unitSpinner.adapter = unitAdapter

        // Toggle recurrence options visibility
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            layout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    /**
     * Configure task recurrence settings
     */
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

    /**
     * Complete task without detailed tracking
     */
    private fun completeTaskBasic(task: Task) {
        task.isCompleted = true
        dbHelper.markTaskCompleted(task.id, true)

        logger.info("TaskDialogHelper", "Task completed: ${task.title}")
        Toast.makeText(context, "Task completed!", Toast.LENGTH_SHORT).show()

        taskCompletedListener?.onTaskCompleted(task)
    }

    /**
     * Complete task with time and difficulty tracking
     */
    private fun completeTaskWithTracking(task: Task, timeSpent: Int, difficulty: Int, notes: String) {
        // Save completion details
        dbHelper.saveCompletion(task.id, timeSpent, difficulty, notes)

        // Mark task as completed
        task.isCompleted = true
        dbHelper.markTaskCompleted(task.id, true)

        logger.info(
            "TaskDialogHelper",
            "Task completed with tracking: ${task.title} (Time: $timeSpent min, Difficulty: $difficulty)"
        )

        Toast.makeText(context, "Task completed with tracking!", Toast.LENGTH_SHORT).show()

        taskCompletedListener?.onTaskCompleted(task)
    }
}

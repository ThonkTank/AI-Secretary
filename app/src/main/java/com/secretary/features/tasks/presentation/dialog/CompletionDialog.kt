package com.secretary.features.tasks.presentation.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.secretary.R
import com.secretary.Task
import com.secretary.features.statistics.data.CompletionRepositoryImpl
import com.secretary.features.statistics.domain.repository.CompletionRepository
import com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel
import com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory
import kotlinx.coroutines.launch

/**
 * DialogFragment for completing a task with optional tracking
 * Phase 4.5.6: Dialog Extraction
 *
 * Replaces TaskDialogHelper.showCompletionDialog()
 * Uses TaskListViewModel for task completion + CompletionRepository for tracking
 */
class CompletionDialog : DialogFragment() {

    private lateinit var viewModel: TaskListViewModel
    private lateinit var completionRepository: CompletionRepository
    private var task: Task? = null

    companion object {
        const val TAG = "CompletionDialog"
        const val RESULT_KEY = "task_completed"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"

        fun newInstance(task: Task, factory: TaskViewModelFactory): CompletionDialog {
            return CompletionDialog().apply {
                arguments = bundleOf(
                    EXTRA_TASK_ID to task.id,
                    EXTRA_TASK_TITLE to task.title
                )
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val taskId = arguments?.getLong(EXTRA_TASK_ID) ?: 0
        val taskTitle = arguments?.getString(EXTRA_TASK_TITLE) ?: "Task"

        // Create a dummy task object (we only need id and title for completion)
        task = Task().apply {
            this.id = taskId
            this.title = taskTitle
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_completion, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        setupViewModel()
        setupViews(dialogView)

        // Load average completion time
        loadAverageTime(dialogView, taskId)

        return dialog
    }

    private fun setupViewModel() {
        // Setup TaskListViewModel for task completion
        val database = com.secretary.shared.database.TaskDatabase.getDatabase(requireContext())
        val taskDao = database.taskDao()
        val taskRepository = com.secretary.features.tasks.data.repository.TaskRepositoryImpl(taskDao)
        val streakService = com.secretary.features.tasks.domain.service.StreakService()
        val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()

        val factory = TaskViewModelFactory(taskRepository, streakService, recurrenceService)
        viewModel = ViewModelProvider(this, factory)[TaskListViewModel::class.java]

        // Setup CompletionRepository for tracking data
        val completionDao = database.completionDao()
        completionRepository = CompletionRepositoryImpl(completionDao)

        // Observe completion result
        viewModel.operationSuccess.observe(this) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                setFragmentResult(RESULT_KEY, bundleOf("success" to true))
                dismiss()
            }
        }

        // Observe errors
        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews(dialogView: View) {
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
        taskTitleText.text = task?.title ?: "Task"

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
            val visibility = if (isChecked) View.GONE else View.VISIBLE
            timeSpentInput.visibility = visibility
            averageTimeText.visibility = visibility
            difficultySeekBar.visibility = visibility
            difficultyValueText.visibility = visibility
            notesInput.visibility = visibility
        }

        // Skip button - complete without tracking
        skipButton.setOnClickListener {
            completeTaskBasic()
        }

        // Save button - complete with tracking
        saveButton.setOnClickListener {
            if (quickCompleteCheckBox.isChecked) {
                completeTaskBasic()
            } else {
                val timeSpent = try {
                    timeSpentInput.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    0
                }

                val difficulty = difficultySeekBar.progress
                val notes = notesInput.text.toString().trim()

                completeTaskWithTracking(timeSpent, difficulty, notes)
            }
        }

        // Handle dialog cancel
        dialog?.setOnCancelListener {
            setFragmentResult(RESULT_KEY, bundleOf("cancelled" to true))
        }
    }

    private fun loadAverageTime(dialogView: View, taskId: Long) {
        val averageTimeText = dialogView.findViewById<TextView>(R.id.averageTimeText)

        lifecycleScope.launch {
            try {
                val avgTime = completionRepository.getAverageCompletionTime(taskId)
                if (avgTime > 0) {
                    averageTimeText.text = "(Avg: $avgTime min)"
                    averageTimeText.visibility = View.VISIBLE
                } else {
                    averageTimeText.visibility = View.GONE
                }
            } catch (e: Exception) {
                // Failed to load average time - not critical, just hide
                averageTimeText.visibility = View.GONE
            }
        }
    }

    /**
     * Complete task without detailed tracking
     */
    private fun completeTaskBasic() {
        val taskId = task?.id ?: return

        lifecycleScope.launch {
            viewModel.completeTask(taskId)
        }
    }

    /**
     * Complete task with time and difficulty tracking
     */
    private fun completeTaskWithTracking(timeSpent: Int, difficulty: Int, notes: String) {
        val taskId = task?.id ?: return

        lifecycleScope.launch {
            try {
                // Save completion tracking data first
                completionRepository.saveCompletion(
                    taskId = taskId,
                    timeSpentMinutes = timeSpent,
                    difficulty = difficulty,
                    notes = notes.ifEmpty { null }
                )

                // Then complete the task (updates streaks, handles recurrence)
                viewModel.completeTask(taskId)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Failed to save completion data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

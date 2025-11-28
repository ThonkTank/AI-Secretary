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
import com.secretary.features.tasks.domain.model.Task
import com.secretary.features.statistics.data.CompletionRepositoryImpl
import com.secretary.features.statistics.domain.repository.CompletionRepository
import com.secretary.features.tasks.presentation.viewmodel.TaskListViewModel
import com.secretary.features.tasks.presentation.viewmodel.TaskViewModelFactory
import com.secretary.features.dreams.domain.usecase.CompleteTaskWithXPUseCase
import com.secretary.features.dreams.data.repository.TaskMilestoneLinkRepositoryImpl
import com.secretary.features.dreams.data.repository.MilestoneRepositoryImpl
import com.secretary.features.dreams.data.repository.DreamRepositoryImpl
import com.secretary.features.dreams.domain.service.XPCalculationService
import com.secretary.features.dreams.domain.service.LevelProgressionService
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
        const val EXTRA_TASK_PRIORITY = "task_priority"

        fun newInstance(task: Task, factory: TaskViewModelFactory): CompletionDialog {
            return CompletionDialog().apply {
                arguments = bundleOf(
                    EXTRA_TASK_ID to task.id,
                    EXTRA_TASK_TITLE to task.title,
                    EXTRA_TASK_PRIORITY to task.priority
                )
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val taskId = arguments?.getLong(EXTRA_TASK_ID) ?: 0
        val taskTitle = arguments?.getString(EXTRA_TASK_TITLE) ?: "Task"
        val taskPriority = arguments?.getInt(EXTRA_TASK_PRIORITY) ?: 1

        // Create a dummy task object (we only need id, title, and priority)
        task = Task().apply {
            this.id = taskId
            this.title = taskTitle
            this.priority = taskPriority
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_completion, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        setupViewModel()
        setupViews(dialogView)

        // Load average completion time
        loadAverageTime(dialogView, taskId)

        // Load XP preview
        loadXPPreview(dialogView, taskId, task?.priority ?: 1)

        return dialog
    }

    private fun setupViewModel() {
        // Setup TaskListViewModel for task completion
        val database = com.secretary.shared.database.TaskDatabase.getDatabase(requireContext())
        val taskDao = database.taskDao()
        val taskRepository = com.secretary.features.tasks.data.repository.TaskRepositoryImpl(taskDao)
        val streakService = com.secretary.features.tasks.domain.service.StreakService()
        val recurrenceService = com.secretary.features.tasks.domain.service.RecurrenceService()
        val motivationalMessageService = com.secretary.features.motivation.domain.MotivationalMessageService()

        // Setup CompletionRepository for tracking data
        val completionDao = database.completionDao()
        completionRepository = CompletionRepositoryImpl(completionDao)

        val factory = TaskViewModelFactory(taskRepository, completionRepository, streakService, recurrenceService, motivationalMessageService)
        viewModel = ViewModelProvider(this, factory)[TaskListViewModel::class.java]

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
     * Load XP preview for the task.
     * Shows how much XP will be earned from completing this task.
     */
    private fun loadXPPreview(dialogView: View, taskId: Long, taskPriority: Int) {
        val xpPreviewSection = dialogView.findViewById<LinearLayout>(R.id.xpPreviewSection)
        val xpPreviewText = dialogView.findViewById<TextView>(R.id.xpPreviewText)
        val milestonePreviewText = dialogView.findViewById<TextView>(R.id.milestonePreviewText)

        lifecycleScope.launch {
            try {
                val database = com.secretary.shared.database.TaskDatabase.getDatabase(requireContext())

                // Create repositories
                val linkRepo = TaskMilestoneLinkRepositoryImpl(database.taskMilestoneJunctionDao())
                val milestoneRepo = MilestoneRepositoryImpl(database.milestoneDao())
                val dreamRepo = DreamRepositoryImpl(database.dreamDao())

                // Create services
                val xpService = XPCalculationService()
                val levelService = LevelProgressionService()

                // Create use case
                val useCase = CompleteTaskWithXPUseCase(
                    linkRepo, milestoneRepo, dreamRepo, xpService, levelService
                )

                // Get preview
                val preview = useCase.previewXP(taskId, taskPriority)

                // Update UI on main thread
                activity?.runOnUiThread {
                    if (preview.milestoneCount > 0) {
                        xpPreviewSection.visibility = View.VISIBLE

                        // Format XP preview text
                        if (preview.milestoneCount == 1) {
                            xpPreviewText.text = "+${preview.xpPerMilestone} XP"
                        } else {
                            xpPreviewText.text = "+${preview.xpPerMilestone} XP × ${preview.milestoneCount} = ${preview.totalXp} XP total"
                        }

                        // Format milestone list
                        if (preview.linkedMilestones.isNotEmpty()) {
                            milestonePreviewText.text = preview.linkedMilestones.joinToString("\n") { "• $it" }
                            milestonePreviewText.visibility = View.VISIBLE
                        } else {
                            milestonePreviewText.visibility = View.GONE
                        }
                    } else {
                        // No milestones linked - hide the section
                        xpPreviewSection.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                // Failed to load XP preview - hide section
                // This is not critical, so we just silently hide it
                activity?.runOnUiThread {
                    xpPreviewSection?.visibility = View.GONE
                }
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

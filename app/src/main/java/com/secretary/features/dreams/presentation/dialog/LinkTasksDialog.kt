package com.secretary.features.dreams.presentation.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.secretary.R
import com.secretary.features.dreams.data.repository.TaskMilestoneLinkRepositoryImpl
import com.secretary.features.dreams.domain.usecase.LinkTaskToMilestonesUseCase
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch

/**
 * Dialog for linking a task to milestones.
 * Shows all milestones from all active dreams.
 * Tasks can be linked to multiple milestones (cross-dream allowed).
 */
class LinkTasksDialog : DialogFragment() {

    companion object {
        private const val ARG_TASK_ID = "task_id"
        private const val ARG_TASK_TITLE = "task_title"

        fun newInstance(taskId: Long, taskTitle: String): LinkTasksDialog {
            return LinkTasksDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TASK_ID, taskId)
                    putString(ARG_TASK_TITLE, taskTitle)
                }
            }
        }
    }

    private var taskId: Long = 0
    private var onLinksUpdated: (() -> Unit)? = null
    private val selectedMilestoneIds = mutableSetOf<Long>()

    fun setOnLinksUpdatedListener(listener: () -> Unit) {
        onLinksUpdated = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskId = arguments?.getLong(ARG_TASK_ID) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val taskTitle = arguments?.getString(ARG_TASK_TITLE) ?: "Task"

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_link_tasks, null)
        val headerText = view.findViewById<TextView>(R.id.linkTasksHeader)
        val milestonesContainer = view.findViewById<LinearLayout>(R.id.milestonesContainer)

        headerText.text = "Link \"$taskTitle\" to milestones:"

        // Load milestones and existing links
        lifecycleScope.launch {
            loadMilestonesAndLinks(milestonesContainer)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Link to Milestones")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                saveMilestoneLinks()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private suspend fun loadMilestonesAndLinks(container: LinearLayout) {
        try {
            val database = TaskDatabase.getDatabase(requireContext())

            // Get existing links
            val linkRepo = TaskMilestoneLinkRepositoryImpl(database.taskMilestoneJunctionDao())
            val existingLinks = linkRepo.getLinksForTask(taskId)
            selectedMilestoneIds.addAll(existingLinks.map { it.milestoneId })

            // Get all active dreams with milestones
            val dreams = database.dreamDao().getActiveDreams()

            activity?.runOnUiThread {
                container.removeAllViews()

                if (dreams.isEmpty()) {
                    val emptyText = TextView(context).apply {
                        text = "No dreams available.\nCreate a dream first!"
                        setPadding(16, 16, 16, 16)
                    }
                    container.addView(emptyText)
                    return@runOnUiThread
                }

                dreams.forEach { dreamEntity ->
                    // Dream header
                    val dreamHeader = TextView(context).apply {
                        text = dreamEntity.title
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, 16, 0, 8)
                    }
                    container.addView(dreamHeader)

                    // Load milestones for this dream
                    lifecycleScope.launch {
                        val milestones = database.milestoneDao().getMilestonesByDream(dreamEntity.dreamId)

                        activity?.runOnUiThread {
                            if (milestones.isEmpty()) {
                                val noMilestonesText = TextView(context).apply {
                                    text = "  (no milestones)"
                                    textSize = 14f
                                    setTextColor(android.graphics.Color.GRAY)
                                }
                                container.addView(noMilestonesText)
                            } else {
                                milestones.forEach { milestoneEntity ->
                                    val checkbox = CheckBox(context).apply {
                                        text = milestoneEntity.title
                                        isChecked = selectedMilestoneIds.contains(milestoneEntity.milestoneId)
                                        setOnCheckedChangeListener { _, checked ->
                                            if (checked) {
                                                selectedMilestoneIds.add(milestoneEntity.milestoneId)
                                            } else {
                                                selectedMilestoneIds.remove(milestoneEntity.milestoneId)
                                            }
                                        }
                                    }
                                    container.addView(checkbox)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            activity?.runOnUiThread {
                Toast.makeText(context, "Error loading milestones", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveMilestoneLinks() {
        lifecycleScope.launch {
            try {
                val database = TaskDatabase.getDatabase(requireContext())
                val repository = TaskMilestoneLinkRepositoryImpl(database.taskMilestoneJunctionDao())
                val useCase = LinkTaskToMilestonesUseCase(repository)

                useCase(taskId, selectedMilestoneIds.toList()).onSuccess {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Links updated!", Toast.LENGTH_SHORT).show()
                        onLinksUpdated?.invoke()
                    }
                }.onFailure { error ->
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

package com.secretary.features.dreams.presentation.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.secretary.R
import com.secretary.features.dreams.data.repository.MilestoneRepositoryImpl
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch

/**
 * Dialog for editing an existing milestone.
 */
class EditMilestoneDialog : DialogFragment() {

    companion object {
        private const val ARG_MILESTONE_ID = "milestone_id"
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_TARGET_XP = "target_xp"

        fun newInstance(milestone: Milestone): EditMilestoneDialog {
            return EditMilestoneDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_MILESTONE_ID, milestone.id)
                    putString(ARG_TITLE, milestone.title)
                    putString(ARG_DESCRIPTION, milestone.description)
                    putLong(ARG_TARGET_XP, milestone.targetXp)
                }
            }
        }
    }

    private var milestoneId: Long = 0
    private var onMilestoneUpdated: (() -> Unit)? = null

    fun setOnMilestoneUpdatedListener(listener: () -> Unit) {
        onMilestoneUpdated = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        milestoneId = arguments?.getLong(ARG_MILESTONE_ID) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_milestone, null)

        val titleInput = view.findViewById<EditText>(R.id.milestoneTitle)
        val descriptionInput = view.findViewById<EditText>(R.id.milestoneDescription)
        val targetXpInput = view.findViewById<EditText>(R.id.milestoneTargetXp)

        // Populate existing data
        titleInput.setText(arguments?.getString(ARG_TITLE) ?: "")
        descriptionInput.setText(arguments?.getString(ARG_DESCRIPTION) ?: "")
        targetXpInput.setText((arguments?.getLong(ARG_TARGET_XP) ?: 1000).toString())

        return AlertDialog.Builder(requireContext())
            .setTitle("Edit Milestone")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim().takeIf { it.isNotBlank() }
                val targetXp = targetXpInput.text.toString().toLongOrNull() ?: 1000

                if (title.isBlank()) {
                    Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateMilestone(title, description, targetXp)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                showDeleteConfirmation()
            }
            .create()
    }

    private fun updateMilestone(title: String, description: String?, targetXp: Long) {
        lifecycleScope.launch {
            try {
                val database = TaskDatabase.getDatabase(requireContext())
                val milestoneDao = database.milestoneDao()

                val existing = milestoneDao.getMilestoneById(milestoneId)
                if (existing != null) {
                    val updated = existing.copy(
                        title = title,
                        description = description,
                        targetXp = targetXp
                    )
                    milestoneDao.updateMilestone(updated)
                    Toast.makeText(context, "Milestone updated!", Toast.LENGTH_SHORT).show()
                    onMilestoneUpdated?.invoke()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Milestone?")
            .setMessage("This will permanently delete this milestone and its progress.")
            .setPositiveButton("Delete") { _, _ ->
                deleteMilestone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMilestone() {
        lifecycleScope.launch {
            try {
                val database = TaskDatabase.getDatabase(requireContext())
                val milestoneDao = database.milestoneDao()
                val milestone = milestoneDao.getMilestoneById(milestoneId)
                if (milestone != null) {
                    milestoneDao.deleteMilestone(milestone)
                    Toast.makeText(context, "Milestone deleted", Toast.LENGTH_SHORT).show()
                    onMilestoneUpdated?.invoke()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

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
import com.secretary.features.dreams.domain.usecase.CreateMilestoneUseCase
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch

/**
 * Dialog for adding a new milestone to a dream.
 * Fields: title (required), description (optional), target XP.
 */
class AddMilestoneDialog : DialogFragment() {

    companion object {
        private const val ARG_DREAM_ID = "dream_id"

        fun newInstance(dreamId: Long): AddMilestoneDialog {
            return AddMilestoneDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DREAM_ID, dreamId)
                }
            }
        }
    }

    private var dreamId: Long = 0
    private var onMilestoneAdded: (() -> Unit)? = null

    fun setOnMilestoneAddedListener(listener: () -> Unit) {
        onMilestoneAdded = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dreamId = arguments?.getLong(ARG_DREAM_ID) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_milestone, null)

        val titleInput = view.findViewById<EditText>(R.id.milestoneTitle)
        val descriptionInput = view.findViewById<EditText>(R.id.milestoneDescription)
        val targetXpInput = view.findViewById<EditText>(R.id.milestoneTargetXp)

        // Default XP
        targetXpInput.setText("1000")

        return AlertDialog.Builder(requireContext())
            .setTitle("Add Milestone")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim().takeIf { it.isNotBlank() }
                val targetXp = targetXpInput.text.toString().toLongOrNull() ?: 1000

                if (title.isBlank()) {
                    Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createMilestone(title, description, targetXp)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun createMilestone(title: String, description: String?, targetXp: Long) {
        lifecycleScope.launch {
            try {
                val database = TaskDatabase.getDatabase(requireContext())
                val milestoneRepository = MilestoneRepositoryImpl(database.milestoneDao())
                val dreamRepository = com.secretary.features.dreams.data.repository.DreamRepositoryImpl(database.dreamDao())
                val useCase = CreateMilestoneUseCase(milestoneRepository, dreamRepository)

                useCase(dreamId, title, description, targetXp).onSuccess {
                    Toast.makeText(context, "Milestone added!", Toast.LENGTH_SHORT).show()
                    onMilestoneAdded?.invoke()
                }.onFailure { error ->
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

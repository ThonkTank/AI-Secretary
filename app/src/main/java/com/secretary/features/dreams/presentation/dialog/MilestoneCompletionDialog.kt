package com.secretary.features.dreams.presentation.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.secretary.R
import com.secretary.features.dreams.data.repository.DreamRepositoryImpl
import com.secretary.features.dreams.data.repository.MilestoneRepositoryImpl
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.service.LevelProgressionService
import com.secretary.features.dreams.domain.usecase.CompleteMilestoneUseCase
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch

/**
 * Dialog for completing a milestone.
 * Shows XP to be transferred to dream and potential level up.
 *
 * Key feature: When milestone is completed manually via this dialog,
 * all accumulated XP flows to the parent dream.
 */
class MilestoneCompletionDialog : DialogFragment() {

    companion object {
        private const val ARG_MILESTONE_ID = "milestone_id"
        private const val ARG_MILESTONE_TITLE = "milestone_title"
        private const val ARG_CURRENT_XP = "current_xp"
        private const val ARG_DREAM_ID = "dream_id"

        fun newInstance(milestone: Milestone): MilestoneCompletionDialog {
            return MilestoneCompletionDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_MILESTONE_ID, milestone.id)
                    putString(ARG_MILESTONE_TITLE, milestone.title)
                    putLong(ARG_CURRENT_XP, milestone.currentXp)
                    putLong(ARG_DREAM_ID, milestone.dreamId)
                }
            }
        }
    }

    private var onMilestoneCompleted: ((leveledUp: Boolean, newLevel: Int) -> Unit)? = null

    fun setOnMilestoneCompletedListener(listener: (leveledUp: Boolean, newLevel: Int) -> Unit) {
        onMilestoneCompleted = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val milestoneId = arguments?.getLong(ARG_MILESTONE_ID) ?: 0
        val milestoneTitle = arguments?.getString(ARG_MILESTONE_TITLE) ?: "Milestone"
        val currentXp = arguments?.getLong(ARG_CURRENT_XP) ?: 0
        val dreamId = arguments?.getLong(ARG_DREAM_ID) ?: 0

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_milestone_completion, null)

        val titleText = view.findViewById<TextView>(R.id.completionTitle)
        val xpText = view.findViewById<TextView>(R.id.completionXpText)
        val messageText = view.findViewById<TextView>(R.id.completionMessage)

        titleText.text = milestoneTitle
        xpText.text = "+$currentXp XP"
        messageText.text = "Complete this milestone?\n\nAll accumulated XP ($currentXp) will be added to your dream!"

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Complete") { _, _ ->
                completeMilestone(milestoneId)
            }
            .setNegativeButton("Not Yet", null)
            .create()
    }

    private fun completeMilestone(milestoneId: Long) {
        lifecycleScope.launch {
            try {
                val database = TaskDatabase.getDatabase(requireContext())
                val milestoneRepository = MilestoneRepositoryImpl(database.milestoneDao())
                val dreamRepository = DreamRepositoryImpl(database.dreamDao())
                val levelService = LevelProgressionService()

                val useCase = CompleteMilestoneUseCase(
                    milestoneRepository, dreamRepository, levelService
                )

                useCase(milestoneId).onSuccess { result ->
                    val leveledUp = result.levelUpResult?.leveledUp ?: false
                    val newLevel = result.levelUpResult?.newLevel ?: 1

                    if (leveledUp) {
                        Toast.makeText(context, "LEVEL UP! Now Level $newLevel!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Milestone completed! +${result.xpTransferred} XP", Toast.LENGTH_SHORT).show()
                    }

                    onMilestoneCompleted?.invoke(leveledUp, newLevel)
                }.onFailure { error ->
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

package com.secretary.features.dreams.presentation.dialog

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.secretary.R

/**
 * Celebration dialog for level up events.
 * Shows:
 * - "LEVEL UP!" title with animation
 * - Level transition (e.g., "Lvl 4 → 5")
 * - Dream name
 * - Confetti-style visual effects (simplified)
 */
class LevelUpDialog : DialogFragment() {

    companion object {
        private const val ARG_DREAM_TITLE = "dream_title"
        private const val ARG_OLD_LEVEL = "old_level"
        private const val ARG_NEW_LEVEL = "new_level"

        fun newInstance(dreamTitle: String, oldLevel: Int, newLevel: Int): LevelUpDialog {
            return LevelUpDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DREAM_TITLE, dreamTitle)
                    putInt(ARG_OLD_LEVEL, oldLevel)
                    putInt(ARG_NEW_LEVEL, newLevel)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_level_up, null)
        dialog.setContentView(view)

        val dreamTitle = arguments?.getString(ARG_DREAM_TITLE) ?: ""
        val oldLevel = arguments?.getInt(ARG_OLD_LEVEL) ?: 1
        val newLevel = arguments?.getInt(ARG_NEW_LEVEL) ?: 2

        // Setup views
        val titleText = view.findViewById<TextView>(R.id.levelUpTitle)
        val levelTransition = view.findViewById<TextView>(R.id.levelTransition)
        val dreamNameText = view.findViewById<TextView>(R.id.dreamNameText)
        val celebrationEmoji = view.findViewById<TextView>(R.id.celebrationEmoji)
        val okButton = view.findViewById<Button>(R.id.levelUpOkButton)

        levelTransition.text = "Lvl $oldLevel → $newLevel"
        dreamNameText.text = dreamTitle

        // Animations
        startCelebrationAnimation(titleText, levelTransition, celebrationEmoji)

        okButton.setOnClickListener {
            dismiss()
        }

        return dialog
    }

    private fun startCelebrationAnimation(
        titleText: TextView,
        levelTransition: TextView,
        celebrationEmoji: TextView
    ) {
        // Scale animation for title
        titleText.scaleX = 0f
        titleText.scaleY = 0f

        val scaleX = ObjectAnimator.ofFloat(titleText, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(titleText, "scaleY", 0f, 1.2f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 600
            interpolator = OvershootInterpolator()
            start()
        }

        // Pulse animation for level
        levelTransition.alpha = 0f
        ObjectAnimator.ofFloat(levelTransition, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 300
            start()
        }

        // Rotate emoji
        ObjectAnimator.ofFloat(celebrationEmoji, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = 1
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        // Make dialog wider
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

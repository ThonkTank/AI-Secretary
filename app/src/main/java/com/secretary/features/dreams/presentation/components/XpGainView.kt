package com.secretary.features.dreams.presentation.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.secretary.R

/**
 * Custom view for displaying XP gain animations.
 * Shows "+X XP" text that slides up and fades out.
 *
 * Usage:
 * ```kotlin
 * xpGainView.showXpGain(50) // Shows "+50 XP" animation
 * ```
 */
class XpGainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val xpText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_xp_gain, this, true)
        xpText = findViewById(R.id.xpGainText)
        visibility = View.INVISIBLE
    }

    /**
     * Show XP gain animation.
     * @param xpAmount The amount of XP gained
     * @param color Optional color for the text (default green)
     */
    fun showXpGain(xpAmount: Long, color: Int = 0xFF4CAF50.toInt()) {
        xpText.text = "+$xpAmount XP"
        xpText.setTextColor(color)

        // Reset position
        translationY = 0f
        alpha = 1f
        visibility = View.VISIBLE

        // Create animations
        val slideUp = ObjectAnimator.ofFloat(this, "translationY", 0f, -100f).apply {
            duration = 1000
        }

        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            duration = 500
            startDelay = 500
        }

        AnimatorSet().apply {
            playTogether(slideUp, fadeOut)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Hide after animation
        postDelayed({
            visibility = View.INVISIBLE
            translationY = 0f
            alpha = 1f
        }, 1500)
    }

    /**
     * Show multiple XP gains (for multi-milestone tasks).
     * @param milestoneXpList List of (milestoneName, xpAmount) pairs
     */
    fun showMultipleXpGains(milestoneXpList: List<Pair<String, Long>>) {
        if (milestoneXpList.isEmpty()) return

        var delay = 0L
        milestoneXpList.forEach { (_, xp) ->
            postDelayed({
                showXpGain(xp)
            }, delay)
            delay += 800
        }
    }
}

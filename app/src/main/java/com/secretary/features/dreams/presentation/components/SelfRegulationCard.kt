package com.secretary.features.dreams.presentation.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.secretary.R

/**
 * Custom view displaying Self-Regulation analysis for dreams.
 * Shows SOLL-IST gap analysis with motivational messages.
 *
 * Dream-to-Task Feature - Presentation Layer
 * Clean Architecture: UI component with no business logic
 *
 * Usage:
 * ```kotlin
 * selfRegulationCard.setState(SelfRegulationState(
 *     gapPercentage = 5.0f,
 *     gapLevel = GapLevel.ON_TRACK,
 *     motivationalMessage = "Gut dabei! Weiter so."
 * ))
 * ```
 */
class SelfRegulationCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val gapIndicatorBar: View
    private val gapPercentageText: TextView
    private val motivationalMessageText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_self_regulation_card, this, true)
        gapIndicatorBar = findViewById(R.id.gapIndicatorBar)
        gapPercentageText = findViewById(R.id.gapPercentageText)
        motivationalMessageText = findViewById(R.id.motivationalMessageText)
    }

    /**
     * Update the card with new self-regulation state.
     * Changes colors and text based on gap level.
     *
     * @param state The current self-regulation state to display
     */
    fun setState(state: SelfRegulationState) {
        // Update gap percentage text
        val gapText = when (state.gapLevel) {
            GapLevel.AHEAD -> context.getString(R.string.gap_ahead)
            GapLevel.ON_TRACK -> context.getString(R.string.gap_on_track)
            GapLevel.SLIGHTLY_BEHIND -> context.getString(R.string.gap_slightly_behind)
            GapLevel.BEHIND -> context.getString(R.string.gap_behind)
            GapLevel.CRITICAL -> context.getString(R.string.gap_critical)
        }

        val percentageSign = if (state.gapPercentage >= 0) "+" else ""
        gapPercentageText.text = "$gapText ($percentageSign${String.format("%.1f", state.gapPercentage)}%)"

        // Update colors based on gap level
        val color = when (state.gapLevel) {
            GapLevel.AHEAD -> 0xFF4CAF50.toInt()        // Green
            GapLevel.ON_TRACK -> 0xFF8BC34A.toInt()    // Light Green
            GapLevel.SLIGHTLY_BEHIND -> 0xFFFFEB3B.toInt() // Yellow
            GapLevel.BEHIND -> 0xFFFF9800.toInt()       // Orange
            GapLevel.CRITICAL -> 0xFFF44336.toInt()     // Red
        }

        gapIndicatorBar.setBackgroundColor(color)
        gapPercentageText.setTextColor(color)

        // Update motivational message
        motivationalMessageText.text = state.motivationalMessage
    }
}

/**
 * Self-regulation state for a dream.
 * Combines gap analysis with motivational messaging.
 *
 * @property gapPercentage Progress gap (-100 to +100)
 *                         Negative = behind schedule, Positive = ahead
 * @property gapLevel Categorized gap level for color coding
 * @property motivationalMessage Context-appropriate motivational message
 */
data class SelfRegulationState(
    val gapPercentage: Float,
    val gapLevel: GapLevel,
    val motivationalMessage: String
)

/**
 * Gap level categories for self-regulation feedback.
 * Determines color coding and messaging tone.
 */
enum class GapLevel {
    AHEAD,              // ≥10% ahead of schedule (green)
    ON_TRACK,           // 0-10% ahead (light green)
    SLIGHTLY_BEHIND,    // -10 to 0% behind (yellow)
    BEHIND,             // -30 to -10% behind (orange)
    CRITICAL            // <-30% behind (red)
}

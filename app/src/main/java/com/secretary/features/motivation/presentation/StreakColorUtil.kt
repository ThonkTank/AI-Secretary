package com.secretary.features.motivation.presentation

import com.secretary.R

/**
 * StreakColorUtil - Utility for Streak and Completion Colors
 * Phase 4: Motivation & Statistics
 *
 * Maps streak counts and completion percentages to appropriate color resources.
 */
object StreakColorUtil {

    /**
     * Get color resource ID based on streak count.
     *
     * Streak levels:
     * - 0: none (gray)
     * - 1-2: starting (amber)
     * - 3-6: building (orange)
     * - 7-13: strong (deep orange)
     * - 14-29: blazing (red)
     * - 30+: legendary (pink)
     *
     * @param streak Current streak count
     * @return Color resource ID
     */
    fun getStreakColor(streak: Int): Int = when {
        streak == 0 -> R.color.streak_none
        streak <= 2 -> R.color.streak_starting
        streak <= 6 -> R.color.streak_building
        streak <= 13 -> R.color.streak_strong
        streak <= 29 -> R.color.streak_blazing
        else -> R.color.streak_legendary
    }

    /**
     * Get color resource ID based on completion percentage.
     *
     * Completion levels:
     * - 0-24%: low (red)
     * - 25-49%: medium (orange)
     * - 50-74%: good (yellow)
     * - 75-99%: great (light green)
     * - 100%: perfect (green)
     *
     * @param percent Completion percentage (0-100)
     * @return Color resource ID
     */
    fun getCompletionColor(percent: Int): Int = when {
        percent < 25 -> R.color.completion_low
        percent < 50 -> R.color.completion_medium
        percent < 75 -> R.color.completion_good
        percent < 100 -> R.color.completion_great
        else -> R.color.completion_perfect
    }
}

package com.secretary.features.dreams.domain.service

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone

/**
 * Aggregates and analyzes progress across dreams and milestones.
 * Provides SOLL-IST gap analysis for self-regulation.
 *
 * Dream-to-Task Feature - Domain Layer
 * Clean Architecture: Pure Kotlin service with no Android dependencies
 */
class DreamProgressService {

    /**
     * Calculate overall dream progress (0.0 to 1.0)
     * Based on completed milestones vs total milestones
     *
     * @param milestones List of all milestones for a dream
     * @return Progress ratio between 0.0 (no progress) and 1.0 (all complete)
     */
    fun calculateDreamProgress(milestones: List<Milestone>): Float {
        if (milestones.isEmpty()) return 0f

        val completedCount = milestones.count { it.isComplete() }
        return completedCount.toFloat() / milestones.size.toFloat()
    }

    /**
     * Calculate milestone XP progress (0.0 to 1.0)
     *
     * @param milestone The milestone to analyze
     * @return Progress ratio between 0.0 (no XP) and 1.0 (target reached)
     */
    fun calculateMilestoneProgress(milestone: Milestone): Float {
        if (milestone.targetXp <= 0) return 0f
        return (milestone.currentXp.toFloat() / milestone.targetXp.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * SOLL-IST Analysis for self-regulation
     * Compares expected progress (SOLL) with actual progress (IST)
     *
     * @param expectedProgress SOLL value (planned/expected progress)
     * @param actualProgress IST value (actual achieved progress)
     * @return Gap percentage (-100 to +100)
     *         - Negative = behind schedule (IST < SOLL)
     *         - Positive = ahead of schedule (IST > SOLL)
     *         - Zero = on track
     */
    fun calculateProgressGap(
        expectedProgress: Float,
        actualProgress: Float
    ): Float {
        val gap = actualProgress - expectedProgress
        return (gap * 100f).coerceIn(-100f, 100f)
    }

    /**
     * Get aggregate statistics for a dream
     *
     * @param dream The dream to analyze
     * @param milestones All milestones associated with this dream
     * @return Comprehensive statistics including counts, XP, and progress
     */
    fun getDreamStatistics(dream: Dream, milestones: List<Milestone>): DreamStatistics {
        val totalMilestones = milestones.size
        val completedMilestones = milestones.count { it.isComplete() }

        // Calculate total XP earned from completed milestones
        val totalXpEarned = milestones
            .filter { it.isComplete() }
            .sumOf { it.targetXp }

        // Calculate average progress across all milestones
        val averageProgress = if (milestones.isEmpty()) {
            0f
        } else {
            milestones.map { calculateMilestoneProgress(it) }.average().toFloat()
        }

        // Overall dream progress based on milestone completion
        val progressPercentage = calculateDreamProgress(milestones) * 100f

        return DreamStatistics(
            totalMilestones = totalMilestones,
            completedMilestones = completedMilestones,
            totalXpEarned = totalXpEarned,
            averageMilestoneProgress = averageProgress,
            progressPercentage = progressPercentage
        )
    }
}

/**
 * Aggregate statistics for a dream
 *
 * @property totalMilestones Total number of milestones
 * @property completedMilestones Number of completed milestones
 * @property totalXpEarned Total XP earned from completed milestones
 * @property averageMilestoneProgress Average progress across all milestones (0.0 to 1.0)
 * @property progressPercentage Overall dream progress percentage (0.0 to 100.0)
 */
data class DreamStatistics(
    val totalMilestones: Int,
    val completedMilestones: Int,
    val totalXpEarned: Long,
    val averageMilestoneProgress: Float,
    val progressPercentage: Float
)

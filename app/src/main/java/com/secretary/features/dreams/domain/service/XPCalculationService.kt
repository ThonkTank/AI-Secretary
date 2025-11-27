package com.secretary.features.dreams.domain.service

/**
 * Calculates XP for task completions.
 * Dream-to-Task Feature - Domain Layer Service
 *
 * This service determines how much XP is awarded when tasks are completed.
 * IMPORTANT: Full XP goes to EACH linked milestone (no splitting!)
 *
 * Example: A High priority task (50 XP) linked to 3 milestones
 * awards 50 XP to each milestone, totaling 150 XP distributed.
 */
class XPCalculationService {

    /**
     * Calculate base XP based on task priority.
     *
     * XP rewards by priority level:
     * - Priority 0 (Low): 10 XP
     * - Priority 1 (Medium): 25 XP
     * - Priority 2 (High): 50 XP
     * - Priority 3 (Urgent): 100 XP
     *
     * @param priority Task priority level (0-3)
     * @return Base XP amount for the priority level
     */
    fun calculateBaseXP(priority: Int): Long {
        return when (priority) {
            0 -> 10L      // Low priority
            1 -> 25L      // Medium priority
            2 -> 50L      // High priority
            3 -> 100L     // Urgent priority
            else -> 10L   // Default to low if invalid priority
        }
    }

    /**
     * Calculate total XP distributed across all linked milestones.
     *
     * Each milestone receives the full base XP amount (no splitting).
     * Formula: totalXP = baseXP * milestoneCount
     *
     * Example scenarios:
     * - Task with priority 2 (High = 50 XP) linked to 3 milestones:
     *   Total distributed = 50 * 3 = 150 XP
     * - Task with priority 1 (Medium = 25 XP) linked to 1 milestone:
     *   Total distributed = 25 * 1 = 25 XP
     *
     * @param priority Task priority level (0-3)
     * @param milestoneCount Number of milestones linked to the task
     * @return Total XP distributed across all milestones
     */
    fun calculateTotalXP(priority: Int, milestoneCount: Int): Long {
        val baseXP = calculateBaseXP(priority)
        return baseXP * milestoneCount.coerceAtLeast(0)
    }

    /**
     * Calculate XP awarded to each individual milestone.
     *
     * Each milestone receives the FULL base XP amount (no splitting).
     * This is the same as calculateBaseXP but semantically distinct.
     *
     * @param priority Task priority level (0-3)
     * @return XP amount awarded to each milestone
     */
    fun calculateXPPerMilestone(priority: Int): Long {
        return calculateBaseXP(priority)
    }
}

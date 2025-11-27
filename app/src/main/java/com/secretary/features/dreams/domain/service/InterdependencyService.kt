package com.secretary.features.dreams.domain.service

import com.secretary.features.dreams.domain.model.MilestoneInterdependency

/**
 * Manages milestone interdependencies (relationships between milestones).
 * Rating scale: -5 (strongly negative) to +5 (strongly positive)
 *
 * Dream-to-Task Feature - Domain Layer
 * Clean Architecture: Pure Kotlin service with no Android dependencies
 */
class InterdependencyService {

    companion object {
        private const val MIN_RATING = MilestoneInterdependency.MIN_IMPACT
        private const val MAX_RATING = MilestoneInterdependency.MAX_IMPACT
        private const val SYNERGY_THRESHOLD = 3
        private const val CONFLICT_THRESHOLD = -3
    }

    /**
     * Validate interdependency rating
     *
     * @param rating The impact rating to validate
     * @return true if valid (within -5 to +5 range)
     * @throws IllegalArgumentException if rating not in valid range
     */
    fun validateRating(rating: Int): Boolean {
        if (rating !in MIN_RATING..MAX_RATING) {
            throw IllegalArgumentException(
                "Invalid rating: $rating. Must be between $MIN_RATING and $MAX_RATING"
            )
        }
        return true
    }

    /**
     * Calculate net impact on a milestone from all dependencies
     * Aggregates all impact ratings to determine overall effect
     *
     * @param dependencies List of interdependencies affecting a milestone
     * @return Aggregate impact score (sum of all ratings)
     */
    fun calculateNetImpact(dependencies: List<MilestoneInterdependency>): Int {
        return dependencies.sumOf { it.impactRating }
    }

    /**
     * Get human-readable description for impact rating
     *
     * @param rating Impact rating (-5 to +5)
     * @return Description string explaining the impact level
     */
    fun getImpactDescription(rating: Int): String {
        return when {
            rating >= 5 -> "Extremely Positive - Essential synergy"
            rating >= 3 -> "Strongly Positive - Major synergy"
            rating >= 1 -> "Positive - Helpful connection"
            rating == 0 -> "Neutral - No significant impact"
            rating >= -2 -> "Negative - Minor conflict"
            rating >= -4 -> "Strongly Negative - Major conflict"
            else -> "Extremely Negative - Critical conflict"
        }
    }

    /**
     * Find synergistic milestones (positive interdependencies)
     * Synergy threshold: rating >= 3
     *
     * @param dependencies List of interdependencies to analyze
     * @return List of interdependencies with strong positive impact
     */
    fun findSynergies(dependencies: List<MilestoneInterdependency>): List<MilestoneInterdependency> {
        return dependencies.filter { it.impactRating >= SYNERGY_THRESHOLD }
    }

    /**
     * Find conflicting milestones (negative interdependencies)
     * Conflict threshold: rating <= -3
     *
     * @param dependencies List of interdependencies to analyze
     * @return List of interdependencies with strong negative impact
     */
    fun findConflicts(dependencies: List<MilestoneInterdependency>): List<MilestoneInterdependency> {
        return dependencies.filter { it.impactRating <= CONFLICT_THRESHOLD }
    }

    /**
     * Analyze impact distribution across dependencies
     * Useful for understanding balance between positive and negative impacts
     *
     * @param dependencies List of interdependencies
     * @return ImpactDistribution with counts and percentages
     */
    fun analyzeImpactDistribution(dependencies: List<MilestoneInterdependency>): ImpactDistribution {
        if (dependencies.isEmpty()) {
            return ImpactDistribution(0, 0, 0, 0f, 0f, 0f)
        }

        val positive = dependencies.count { it.impactRating > 0 }
        val negative = dependencies.count { it.impactRating < 0 }
        val neutral = dependencies.count { it.impactRating == 0 }
        val total = dependencies.size.toFloat()

        return ImpactDistribution(
            positiveCount = positive,
            negativeCount = negative,
            neutralCount = neutral,
            positivePercentage = (positive / total) * 100f,
            negativePercentage = (negative / total) * 100f,
            neutralPercentage = (neutral / total) * 100f)
    }

    /**
     * Get milestones that are prerequisites for the given milestone.
     * Prerequisites are milestones that the target milestone depends on (source milestones in interdependencies).
     *
     * @param milestoneId The milestone to check prerequisites for
     * @param allMilestones All milestones in the system
     * @param interdependencies All interdependency relationships
     * @return List of milestone objects that are prerequisites (sources where this milestone is target)
     */
    fun getPrerequisites(
        milestoneId: Long,
        allMilestones: List<com.secretary.features.dreams.domain.model.Milestone>,
        interdependencies: List<MilestoneInterdependency>
    ): List<com.secretary.features.dreams.domain.model.Milestone> {
        // Find interdependencies where this milestone is the target
        val prerequisiteIds = interdependencies
            .filter { it.targetMilestoneId == milestoneId }
            .map { it.sourceMilestoneId }

        // Return the actual milestone objects
        return allMilestones.filter { it.id in prerequisiteIds }
    }

    /**
     * Get milestones that are prerequisites for the given milestone but not yet completed.
     *
     * @param milestoneId The milestone to check prerequisites for
     * @param allMilestones All milestones in the system
     * @param interdependencies All interdependency relationships
     * @return List of incomplete prerequisite milestones
     */
    fun getUnmetPrerequisites(
        milestoneId: Long,
        allMilestones: List<com.secretary.features.dreams.domain.model.Milestone>,
        interdependencies: List<MilestoneInterdependency>
    ): List<com.secretary.features.dreams.domain.model.Milestone> {
        return getPrerequisites(milestoneId, allMilestones, interdependencies)
            .filter { it.status != com.secretary.features.dreams.domain.model.Milestone.Status.COMPLETED }
    }

    /**
     * Check if milestone has any unmet prerequisites.
     *
     * @param milestoneId The milestone to check
     * @param allMilestones All milestones in the system
     * @param interdependencies All interdependency relationships
     * @return true if there are incomplete prerequisites
     */
    fun hasUnmetPrerequisites(
        milestoneId: Long,
        allMilestones: List<com.secretary.features.dreams.domain.model.Milestone>,
        interdependencies: List<MilestoneInterdependency>
    ): Boolean {
        return getUnmetPrerequisites(milestoneId, allMilestones, interdependencies).isNotEmpty()
    }
}

/**
 * Impact distribution analysis result
 *
 * @property positiveCount Number of positive interdependencies
 * @property negativeCount Number of negative interdependencies
 * @property neutralCount Number of neutral interdependencies
 * @property positivePercentage Percentage of positive (0-100)
 * @property negativePercentage Percentage of negative (0-100)
 * @property neutralPercentage Percentage of neutral (0-100)
 */
data class ImpactDistribution(
    val positiveCount: Int,
    val negativeCount: Int,
    val neutralCount: Int,
    val positivePercentage: Float,
    val negativePercentage: Float,
    val neutralPercentage: Float
)

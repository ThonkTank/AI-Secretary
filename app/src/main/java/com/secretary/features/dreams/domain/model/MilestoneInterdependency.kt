package com.secretary.features.dreams.domain.model

/**
 * MilestoneInterdependency domain model.
 * Dream-to-Task Feature - Domain Layer
 *
 * Represents a relationship between two milestones.
 * Impact rating indicates strength and direction of influence.
 */
data class MilestoneInterdependency(
    val sourceMilestoneId: Long,
    val targetMilestoneId: Long,
    val impactRating: Int, // -5 (negative) to +5 (positive)
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_IMPACT = -5
        const val MAX_IMPACT = 5
    }

    /**
     * Get human-readable impact type
     */
    fun getImpactType(): ImpactType = when {
        impactRating > 0 -> ImpactType.POSITIVE
        impactRating < 0 -> ImpactType.NEGATIVE
        else -> ImpactType.NEUTRAL
    }

    /**
     * Get impact strength description
     */
    fun getImpactStrength(): String {
        val absRating = kotlin.math.abs(impactRating)
        return when {
            absRating >= 4 -> "Strong"
            absRating >= 2 -> "Moderate"
            absRating >= 1 -> "Weak"
            else -> "None"
        }
    }

    /**
     * Get full impact description (e.g., "Strong Positive")
     */
    fun getFullImpactDescription(): String {
        val strength = getImpactStrength()
        val type = getImpactType().displayName
        return if (strength == "None") "None" else "$strength $type"
    }

    /**
     * Check if impact is valid (within range)
     */
    fun isValidImpact(): Boolean = impactRating in MIN_IMPACT..MAX_IMPACT

    /**
     * String representation
     */
    override fun toString(): String {
        val arrow = when {
            impactRating > 0 -> "→+"
            impactRating < 0 -> "→-"
            else -> "→"
        }
        return "Milestone $sourceMilestoneId $arrow$impactRating Milestone $targetMilestoneId"
    }

    /**
     * Impact type enum
     */
    enum class ImpactType(val displayName: String) {
        POSITIVE("Positive"),
        NEGATIVE("Negative"),
        NEUTRAL("Neutral")
    }
}

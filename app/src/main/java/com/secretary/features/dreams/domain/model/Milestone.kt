package com.secretary.features.dreams.domain.model

/**
 * Milestone domain model.
 * Dream-to-Task Feature - Domain Layer
 *
 * Represents a specific milestone within a dream's progression.
 * Milestones track XP progress towards completion.
 */
data class Milestone(
    val id: Long = 0,
    val dreamId: Long,
    val title: String,
    val description: String? = null,
    val targetXp: Long = 1000,
    val currentXp: Long = 0,
    val status: Status = Status.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val orderIndex: Int = 0
) {
    /**
     * Status enum for milestone
     */
    enum class Status(val value: Int) {
        ACTIVE(0),
        COMPLETED(1);

        companion object {
            fun fromInt(value: Int): Status = when (value) {
                1 -> COMPLETED
                else -> ACTIVE
            }
        }
    }

    /**
     * Calculate progress percentage towards completion
     */
    fun getProgressPercentage(): Float {
        if (targetXp <= 0) return 0f
        return ((currentXp.toFloat() / targetXp.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Get progress string (e.g., "750/1000 XP")
     */
    fun getProgressString(): String = "$currentXp/$targetXp XP"

    /**
     * Check if milestone is complete (reached target XP)
     */
    fun isComplete(): Boolean = currentXp >= targetXp || status == Status.COMPLETED

    /**
     * Get remaining XP to complete milestone
     */
    fun getRemainingXp(): Long = (targetXp - currentXp).coerceAtLeast(0)

    /**
     * Get human-readable status string
     */
    fun getStatusString(): String = when (status) {
        Status.ACTIVE -> "Active"
        Status.COMPLETED -> "Completed"
    }

    /**
     * String representation showing title and progress
     */
    override fun toString(): String {
        val checkmark = if (isComplete()) " ✓" else ""
        return "$title ($currentXp/$targetXp XP)$checkmark"
    }
}

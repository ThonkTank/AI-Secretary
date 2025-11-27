package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository

/**
 * Use case for creating a new milestone within a dream.
 * Dream-to-Task Feature - Domain Layer
 *
 * Handles business logic for milestone creation with validation.
 * Returns Result<Long> for error handling (ID of created milestone on success).
 */
class CreateMilestoneUseCase(
    private val milestoneRepository: MilestoneRepository,
    private val dreamRepository: DreamRepository
) {
    /**
     * Create a new milestone for a dream.
     *
     * @param dreamId ID of the parent dream
     * @param title Milestone title (required, non-empty)
     * @param description Optional description
     * @param targetXp Target XP to complete this milestone (default 1000)
     * @return Result<Long> containing milestone ID on success, or error
     */
    suspend operator fun invoke(
        dreamId: Long,
        title: String,
        description: String? = null,
        targetXp: Long = 1000
    ): Result<Long> {
        return try {
            // Validate dream exists
            val dream = dreamRepository.getById(dreamId)
                ?: return Result.failure(IllegalArgumentException("Dream not found: $dreamId"))

            // Validate title
            if (title.isBlank()) {
                return Result.failure(IllegalArgumentException("Milestone title cannot be empty"))
            }

            // Validate target XP
            if (targetXp <= 0) {
                return Result.failure(IllegalArgumentException("Target XP must be positive"))
            }

            // Get current milestone count to determine order index
            val existingMilestones = milestoneRepository.getByDreamId(dreamId)
            val orderIndex = existingMilestones.size

            // Create milestone domain model
            val milestone = Milestone(
                dreamId = dreamId,
                title = title.trim(),
                description = description?.trim(),
                targetXp = targetXp,
                currentXp = 0,
                status = Milestone.Status.ACTIVE,
                createdAt = System.currentTimeMillis(),
                completedAt = null,
                orderIndex = orderIndex
            )

            // Insert into repository
            val milestoneId = milestoneRepository.insert(milestone)

            Result.success(milestoneId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

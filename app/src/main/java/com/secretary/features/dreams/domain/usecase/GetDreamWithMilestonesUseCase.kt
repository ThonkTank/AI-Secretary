package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository

/**
 * Use case for retrieving a dream with all its milestones.
 * Dream-to-Task Feature - Domain Layer
 *
 * Aggregates dream and milestone data for display.
 * Returns Result<DreamWithMilestones> for error handling.
 */
class GetDreamWithMilestonesUseCase(
    private val dreamRepository: DreamRepository,
    private val milestoneRepository: MilestoneRepository
) {
    /**
     * Get a dream and all its associated milestones.
     *
     * @param dreamId ID of the dream to retrieve
     * @return Result<DreamWithMilestones> containing aggregated data or error
     */
    suspend operator fun invoke(dreamId: Long): Result<DreamWithMilestones> {
        return try {
            // Get dream
            val dream = dreamRepository.getById(dreamId)
                ?: return Result.failure(IllegalArgumentException("Dream not found: $dreamId"))

            // Get all milestones for this dream
            val milestones = milestoneRepository.getByDreamId(dreamId)

            Result.success(
                DreamWithMilestones(
                    dream = dream,
                    milestones = milestones
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Aggregated data for a dream with its milestones.
 */
data class DreamWithMilestones(
    val dream: Dream,
    val milestones: List<Milestone>
)

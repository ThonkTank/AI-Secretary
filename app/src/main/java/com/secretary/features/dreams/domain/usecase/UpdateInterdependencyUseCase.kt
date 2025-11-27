package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.MilestoneInterdependency
import com.secretary.features.dreams.domain.repository.InterdependencyRepository
import com.secretary.features.dreams.domain.service.InterdependencyService

/**
 * Use case for creating/updating milestone interdependencies.
 * Dream-to-Task Feature - Domain Layer
 *
 * Manages relationships between milestones with impact ratings.
 * Validates rating range (-5 to +5) before creation.
 *
 * Returns Result<Unit> for error handling.
 */
class UpdateInterdependencyUseCase(
    private val repository: InterdependencyRepository,
    private val interdependencyService: InterdependencyService
) {
    /**
     * Create or update an interdependency between two milestones.
     *
     * @param sourceId Source milestone ID
     * @param targetId Target milestone ID
     * @param rating Impact rating (-5 to +5)
     * @param description Optional description of the relationship
     * @return Result<Unit> indicating success or error
     */
    suspend operator fun invoke(
        sourceId: Long,
        targetId: Long,
        rating: Int,
        description: String? = null
    ): Result<Unit> {
        return try {
            // Validate IDs
            if (sourceId <= 0 || targetId <= 0) {
                return Result.failure(IllegalArgumentException("Invalid milestone IDs"))
            }

            // Prevent self-dependency
            if (sourceId == targetId) {
                return Result.failure(IllegalArgumentException("Milestone cannot depend on itself"))
            }

            // Validate rating using service
            interdependencyService.validateRating(rating)

            // Create interdependency
            val interdependency = MilestoneInterdependency(
                sourceMilestoneId = sourceId,
                targetMilestoneId = targetId,
                impactRating = rating,
                description = description?.trim(),
                createdAt = System.currentTimeMillis()
            )

            // Upsert (insert or update if exists)
            repository.upsert(interdependency)

            Result.success(Unit)
        } catch (e: IllegalArgumentException) {
            // Validation errors from service
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

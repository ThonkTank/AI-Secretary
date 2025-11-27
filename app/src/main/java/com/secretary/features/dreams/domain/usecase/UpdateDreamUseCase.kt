package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.repository.DreamRepository

/**
 * Use case for updating an existing dream.
 * Dream-to-Task Feature - Domain Layer
 *
 * Handles business logic for dream updates with validation.
 * Returns Result<Unit> for error handling.
 */
class UpdateDreamUseCase(
    private val repository: DreamRepository
) {
    /**
     * Update an existing dream.
     *
     * @param dream The dream with updated values
     * @return Result<Unit> indicating success or error
     */
    suspend operator fun invoke(dream: Dream): Result<Unit> {
        return try {
            // Validate dream exists
            val existingDream = repository.getById(dream.id)
                ?: return Result.failure(IllegalArgumentException("Dream not found: ${dream.id}"))

            // Validate title
            if (dream.title.isBlank()) {
                return Result.failure(IllegalArgumentException("Dream title cannot be empty"))
            }

            // Update dream
            repository.update(dream)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

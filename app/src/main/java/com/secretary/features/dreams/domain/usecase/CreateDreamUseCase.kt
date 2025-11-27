package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.repository.DreamRepository

/**
 * Use case for creating a new dream.
 * Dream-to-Task Feature - Domain Layer
 *
 * Handles business logic for dream creation with validation.
 * Returns Result<Long> for error handling (ID of created dream on success).
 */
class CreateDreamUseCase(
    private val repository: DreamRepository
) {
    /**
     * Create a new dream with the provided details.
     *
     * @param title Dream title (required, non-empty)
     * @param description Optional description
     * @param iconName Optional icon identifier
     * @param color Color code for UI (defaults to Material Design primary)
     * @return Result<Long> containing dream ID on success, or error
     */
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        iconName: String? = null,
        color: Int = 0xFF6200EE.toInt()
    ): Result<Long> {
        return try {
            // Validate title
            if (title.isBlank()) {
                return Result.failure(IllegalArgumentException("Dream title cannot be empty"))
            }

            // Create dream domain model
            val dream = Dream(
                title = title.trim(),
                description = description?.trim(),
                iconName = iconName,
                color = color,
                totalXp = 0,
                currentLevel = 1,
                createdAt = System.currentTimeMillis(),
                isArchived = false
            )

            // Insert into repository
            val dreamId = repository.insert(dream)

            Result.success(dreamId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

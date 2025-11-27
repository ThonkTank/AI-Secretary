package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.service.LevelProgressionService
import com.secretary.features.dreams.domain.service.LevelUpResult

/**
 * Use case for completing a milestone.
 * Dream-to-Task Feature - Domain Layer
 *
 * Handles milestone completion logic:
 * 1. Mark milestone as completed
 * 2. Transfer accumulated XP to parent dream
 * 3. Check for dream level-up
 *
 * Returns Result<MilestoneCompletionResult> for error handling.
 */
class CompleteMilestoneUseCase(
    private val milestoneRepository: MilestoneRepository,
    private val dreamRepository: DreamRepository,
    private val levelService: LevelProgressionService
) {
    /**
     * Complete a milestone and transfer XP to its dream.
     *
     * @param milestoneId ID of the milestone to complete
     * @return Result<MilestoneCompletionResult> with XP transferred and level-up info
     */
    suspend operator fun invoke(milestoneId: Long): Result<MilestoneCompletionResult> {
        return try {
            // Get milestone
            val milestone = milestoneRepository.getById(milestoneId)
                ?: return Result.failure(IllegalArgumentException("Milestone not found: $milestoneId"))

            // Check if already completed
            if (milestone.isComplete()) {
                return Result.failure(IllegalStateException("Milestone already completed: $milestoneId"))
            }

            // Get parent dream
            val dream = dreamRepository.getById(milestone.dreamId)
                ?: return Result.failure(IllegalArgumentException("Dream not found: ${milestone.dreamId}"))

            // Transfer milestone XP to dream
            val xpToTransfer = milestone.currentXp
            val previousDreamXp = dream.totalXp
            val newDreamXp = previousDreamXp + xpToTransfer

            // Check for level-up
            val levelUpResult = levelService.checkLevelUp(previousDreamXp, newDreamXp)

            // Update dream with new XP and level
            val updatedDream = dream.copy(
                totalXp = newDreamXp,
                currentLevel = levelUpResult.newLevel
            )
            dreamRepository.update(updatedDream)

            // Mark milestone as completed
            milestoneRepository.complete(milestoneId, System.currentTimeMillis())

            Result.success(
                MilestoneCompletionResult(
                    xpTransferred = xpToTransfer,
                    levelUpResult = if (levelUpResult.leveledUp) levelUpResult else null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Result of completing a milestone.
 *
 * @property xpTransferred Amount of XP transferred from milestone to dream
 * @property levelUpResult Level-up information if dream leveled up, null otherwise
 */
data class MilestoneCompletionResult(
    val xpTransferred: Long,
    val levelUpResult: LevelUpResult?
) {
    /**
     * Check if dream leveled up from this completion.
     */
    fun hasLeveledUp(): Boolean = levelUpResult?.leveledUp == true
}

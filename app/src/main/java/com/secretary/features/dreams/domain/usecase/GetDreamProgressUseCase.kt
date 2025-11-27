package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.service.DreamProgressService
import com.secretary.features.dreams.domain.service.DreamStatistics
import com.secretary.features.dreams.domain.service.LevelProgressionService

/**
 * Use case for retrieving comprehensive dream progress data.
 * Dream-to-Task Feature - Domain Layer
 *
 * Aggregates dream, milestones, statistics, and level progression data.
 * Provides all information needed for dream progress display.
 *
 * Returns Result<DreamProgressData> for error handling.
 */
class GetDreamProgressUseCase(
    private val dreamRepository: DreamRepository,
    private val milestoneRepository: MilestoneRepository,
    private val progressService: DreamProgressService,
    private val levelService: LevelProgressionService
) {
    /**
     * Get comprehensive progress data for a dream.
     *
     * @param dreamId ID of the dream
     * @return Result<DreamProgressData> containing all progress information
     */
    suspend operator fun invoke(dreamId: Long): Result<DreamProgressData> {
        return try {
            // Get dream
            val dream = dreamRepository.getById(dreamId)
                ?: return Result.failure(IllegalArgumentException("Dream not found: $dreamId"))

            // Get all milestones
            val milestones = milestoneRepository.getByDreamId(dreamId)

            // Calculate statistics
            val statistics = progressService.getDreamStatistics(dream, milestones)

            // Calculate level progress
            val levelProgress = levelService.getLevelProgress(dream.totalXp)
            val xpToNextLevel = levelService.getXPToNextLevel(dream.totalXp)

            Result.success(
                DreamProgressData(
                    dream = dream,
                    milestones = milestones,
                    statistics = statistics,
                    levelProgress = levelProgress,
                    xpToNextLevel = xpToNextLevel
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Comprehensive progress data for a dream.
 *
 * @property dream The dream entity
 * @property milestones All milestones for this dream
 * @property statistics Aggregated statistics
 * @property levelProgress Progress towards next level (0.0 to 1.0)
 * @property xpToNextLevel XP needed to reach next level
 */
data class DreamProgressData(
    val dream: Dream,
    val milestones: List<Milestone>,
    val statistics: DreamStatistics,
    val levelProgress: Float,
    val xpToNextLevel: Long
) {
    /**
     * Get the next milestone to work on.
     */
    fun getNextMilestone(): Milestone? {
        return milestones
            .filter { it.status == Milestone.Status.ACTIVE }
            .minByOrNull { it.orderIndex }
    }

    /**
     * Get progress summary string.
     */
    fun getProgressSummary(): String {
        val completed = statistics.completedMilestones
        val total = statistics.totalMilestones
        val percentage = statistics.progressPercentage.toInt()
        return "$completed/$total milestones ($percentage%)"
    }

    /**
     * Check if dream is complete.
     */
    fun isComplete(): Boolean {
        return statistics.totalMilestones > 0 &&
                statistics.completedMilestones == statistics.totalMilestones
    }
}

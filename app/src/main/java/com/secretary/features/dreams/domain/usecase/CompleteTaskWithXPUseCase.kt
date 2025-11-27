package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository
import com.secretary.features.dreams.domain.service.LevelProgressionService
import com.secretary.features.dreams.domain.service.XPCalculationService

/**
 * Use Case for completing a task and distributing XP to linked milestones.
 * Dream-to-Task Feature - Wave 5: XP Integration
 *
 * Flow:
 * 1. Get linked milestones for the task
 * 2. Calculate XP based on task priority
 * 3. Add FULL XP to each linked milestone (no splitting!)
 * 4. Check for level ups on associated dreams
 * 5. Return result with XP details
 *
 * IMPORTANT: XP is NOT split - each milestone gets the full amount!
 *
 * Example:
 * - Task with priority 2 (High = 50 XP) linked to 3 milestones
 * - Each milestone gets 50 XP
 * - Total distributed = 150 XP
 */
class CompleteTaskWithXPUseCase(
    private val taskMilestoneLinkRepository: TaskMilestoneLinkRepository,
    private val milestoneRepository: MilestoneRepository,
    private val dreamRepository: DreamRepository,
    private val xpService: XPCalculationService,
    private val levelService: LevelProgressionService
) {

    /**
     * Complete a task and distribute XP to linked milestones.
     *
     * @param taskId The ID of the completed task
     * @param taskPriority The task's priority (0-3)
     * @return Result containing XP distribution details
     */
    suspend operator fun invoke(taskId: Long, taskPriority: Int): Result<TaskCompletionXPResult> {
        return try {
            // 1. Get linked milestones
            val links = taskMilestoneLinkRepository.getLinksForTask(taskId)

            if (links.isEmpty()) {
                // No linked milestones - task completed but no XP distributed
                return Result.success(
                    TaskCompletionXPResult(
                        xpPerMilestone = 0,
                        milestonesUpdated = emptyList(),
                        levelUps = emptyList(),
                        totalXpDistributed = 0
                    )
                )
            }

            // 2. Calculate XP (full amount for each milestone)
            val xpPerMilestone = xpService.calculateXPPerMilestone(taskPriority)

            // 3. Distribute XP and track level ups
            val milestonesUpdated = mutableListOf<MilestoneXPUpdate>()
            val levelUps = mutableListOf<DreamLevelUp>()
            val dreamsToCheck = mutableSetOf<Long>()

            for (link in links) {
                // Update milestone XP
                milestoneRepository.updateXP(link.milestoneId, xpPerMilestone)

                // Get milestone details for response
                val milestone = milestoneRepository.getById(link.milestoneId)
                if (milestone != null) {
                    milestonesUpdated.add(
                        MilestoneXPUpdate(
                            milestoneId = milestone.id,
                            milestoneName = milestone.title,
                            xpAdded = xpPerMilestone,
                            newTotalXp = milestone.currentXp + xpPerMilestone,
                            targetXp = milestone.targetXp
                        )
                    )
                    dreamsToCheck.add(milestone.dreamId)
                }
            }

            // Note: Level ups only happen on milestone completion, not task completion
            // This is because XP flows: Task → Milestone → Dream (on milestone completion)
            // We keep the levelUps list for future use when we might want to preview
            // potential level ups after task completion

            Result.success(
                TaskCompletionXPResult(
                    xpPerMilestone = xpPerMilestone,
                    milestonesUpdated = milestonesUpdated,
                    levelUps = levelUps,
                    totalXpDistributed = xpPerMilestone * links.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Preview XP that would be gained without actually completing.
     * Useful for showing in completion dialog.
     *
     * @param taskId The ID of the task to preview
     * @param taskPriority The task's priority (0-3)
     * @return XPPreview with details of XP that would be gained
     */
    suspend fun previewXP(taskId: Long, taskPriority: Int): XPPreview {
        val links = taskMilestoneLinkRepository.getLinksForTask(taskId)
        val xpPerMilestone = xpService.calculateXPPerMilestone(taskPriority)

        val milestoneNames = mutableListOf<String>()
        for (link in links) {
            val milestone = milestoneRepository.getById(link.milestoneId)
            milestone?.let { milestoneNames.add(it.title) }
        }

        return XPPreview(
            xpPerMilestone = xpPerMilestone,
            milestoneCount = links.size,
            totalXp = xpPerMilestone * links.size,
            linkedMilestones = milestoneNames
        )
    }
}

/**
 * Result of completing a task with XP distribution.
 *
 * @property xpPerMilestone Amount of XP each milestone received
 * @property milestonesUpdated List of milestones that were updated with XP
 * @property levelUps List of dreams that leveled up (currently unused, reserved for future)
 * @property totalXpDistributed Total amount of XP distributed across all milestones
 */
data class TaskCompletionXPResult(
    val xpPerMilestone: Long,
    val milestonesUpdated: List<MilestoneXPUpdate>,
    val levelUps: List<DreamLevelUp>,
    val totalXpDistributed: Long
)

/**
 * Details of XP added to a milestone.
 *
 * @property milestoneId The ID of the milestone
 * @property milestoneName The name of the milestone
 * @property xpAdded Amount of XP added in this update
 * @property newTotalXp New total XP after the update
 * @property targetXp Target XP required to complete the milestone
 */
data class MilestoneXPUpdate(
    val milestoneId: Long,
    val milestoneName: String,
    val xpAdded: Long,
    val newTotalXp: Long,
    val targetXp: Long
)

/**
 * Details of a dream level up.
 * Reserved for future use when we want to show level up notifications.
 *
 * @property dreamId The ID of the dream
 * @property dreamTitle The title of the dream
 * @property oldLevel Previous level
 * @property newLevel New level after level up
 */
data class DreamLevelUp(
    val dreamId: Long,
    val dreamTitle: String,
    val oldLevel: Int,
    val newLevel: Int
)

/**
 * Preview of XP that would be gained from completing a task.
 *
 * @property xpPerMilestone Amount of XP each milestone would receive
 * @property milestoneCount Number of milestones linked to the task
 * @property totalXp Total XP that would be distributed
 * @property linkedMilestones Names of milestones that would receive XP
 */
data class XPPreview(
    val xpPerMilestone: Long,
    val milestoneCount: Int,
    val totalXp: Long,
    val linkedMilestones: List<String>
)

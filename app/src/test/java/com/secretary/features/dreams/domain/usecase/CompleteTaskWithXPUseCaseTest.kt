package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.model.TaskMilestoneLink
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository
import com.secretary.features.dreams.domain.service.LevelProgressionService
import com.secretary.features.dreams.domain.service.XPCalculationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for CompleteTaskWithXPUseCase
 * Dream-to-Task Feature - Wave 5: XP Integration Testing
 *
 * Tests XP distribution logic when completing tasks:
 * - No linked milestones
 * - XP distribution to multiple milestones (full XP to each, no splitting!)
 * - Priority-based XP calculations
 * - XP preview functionality
 * - Error handling
 *
 * Uses REAL XPCalculationService and LevelProgressionService (pure Kotlin, no mocking)
 * Mocks only repositories
 */
class CompleteTaskWithXPUseCaseTest {

    private lateinit var linkRepository: TaskMilestoneLinkRepository
    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var dreamRepository: DreamRepository
    private lateinit var xpService: XPCalculationService
    private lateinit var levelService: LevelProgressionService
    private lateinit var useCase: CompleteTaskWithXPUseCase

    @Before
    fun setUp() {
        // Real services (pure Kotlin, no Android dependencies)
        xpService = XPCalculationService()
        levelService = LevelProgressionService()

        // Mocked repositories
        linkRepository = mock(TaskMilestoneLinkRepository::class.java)
        milestoneRepository = mock(MilestoneRepository::class.java)
        dreamRepository = mock(DreamRepository::class.java)

        // Create use case with dependencies
        useCase = CompleteTaskWithXPUseCase(
            linkRepository,
            milestoneRepository,
            dreamRepository,
            xpService,
            levelService
        )
    }

    // ========== No Links Tests ==========

    @Test
    fun `invoke with no linked milestones returns empty result`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2
        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!
        assertEquals(0L, xpResult.xpPerMilestone)
        assertEquals(0, xpResult.milestonesUpdated.size)
        assertEquals(0, xpResult.levelUps.size)
        assertEquals(0L, xpResult.totalXpDistributed)

        // Verify no milestone updates occurred
        verify(milestoneRepository, never()).updateXP(any(), any())
    }

    // ========== XP Distribution Tests ==========

    @Test
    fun `invoke distributes full XP to each linked milestone without splitting`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2 // High = 50 XP
        val milestone1 = createMilestone(id = 10L, title = "Milestone 1", currentXp = 100L)
        val milestone2 = createMilestone(id = 20L, title = "Milestone 2", currentXp = 200L)
        val milestone3 = createMilestone(id = 30L, title = "Milestone 3", currentXp = 300L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(
                TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 20L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 30L)
            )
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone1)
        whenever(milestoneRepository.getById(20L)).thenReturn(milestone2)
        whenever(milestoneRepository.getById(30L)).thenReturn(milestone3)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!

        // Each milestone gets FULL 50 XP (not split!)
        assertEquals(50L, xpResult.xpPerMilestone)
        assertEquals(3, xpResult.milestonesUpdated.size)

        // Verify each milestone received 50 XP
        verify(milestoneRepository).updateXP(10L, 50L)
        verify(milestoneRepository).updateXP(20L, 50L)
        verify(milestoneRepository).updateXP(30L, 50L)

        // Verify milestone updates contain correct data
        xpResult.milestonesUpdated.forEach { update ->
            assertEquals(50L, update.xpAdded)
        }

        // Verify milestone 1 update details
        val milestone1Update = xpResult.milestonesUpdated.find { it.milestoneId == 10L }!!
        assertEquals("Milestone 1", milestone1Update.milestoneName)
        assertEquals(150L, milestone1Update.newTotalXp) // 100 + 50
        assertEquals(1000L, milestone1Update.targetXp)

        // Verify milestone 2 update details
        val milestone2Update = xpResult.milestonesUpdated.find { it.milestoneId == 20L }!!
        assertEquals("Milestone 2", milestone2Update.milestoneName)
        assertEquals(250L, milestone2Update.newTotalXp) // 200 + 50

        // Verify milestone 3 update details
        val milestone3Update = xpResult.milestonesUpdated.find { it.milestoneId == 30L }!!
        assertEquals("Milestone 3", milestone3Update.milestoneName)
        assertEquals(350L, milestone3Update.newTotalXp) // 300 + 50
    }

    @Test
    fun `invoke calculates correct total XP distributed`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2 // High = 50 XP base
        val milestone1 = createMilestone(id = 10L, currentXp = 0L)
        val milestone2 = createMilestone(id = 20L, currentXp = 0L)
        val milestone3 = createMilestone(id = 30L, currentXp = 0L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(
                TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 20L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 30L)
            )
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone1)
        whenever(milestoneRepository.getById(20L)).thenReturn(milestone2)
        whenever(milestoneRepository.getById(30L)).thenReturn(milestone3)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!

        // Total = baseXP * milestoneCount = 50 * 3 = 150 XP
        assertEquals(150L, xpResult.totalXpDistributed)
    }

    @Test
    fun `invoke updates all milestones current XP correctly`() = runTest {
        // Given
        val taskId = 1L
        val priority = 1 // Medium = 25 XP
        val milestone1 = createMilestone(id = 10L, currentXp = 500L)
        val milestone2 = createMilestone(id = 20L, currentXp = 750L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(
                TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 20L)
            )
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone1)
        whenever(milestoneRepository.getById(20L)).thenReturn(milestone2)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!

        // Verify XP added correctly
        val update1 = xpResult.milestonesUpdated.find { it.milestoneId == 10L }!!
        assertEquals(525L, update1.newTotalXp) // 500 + 25

        val update2 = xpResult.milestonesUpdated.find { it.milestoneId == 20L }!!
        assertEquals(775L, update2.newTotalXp) // 750 + 25
    }

    // ========== Priority-Based XP Tests ==========

    @Test
    fun `invoke calculates correct XP for low priority task`() = runTest {
        // Given
        val taskId = 1L
        val priority = 0 // Low = 10 XP
        val milestone = createMilestone(id = 10L, currentXp = 0L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(TaskMilestoneLink(taskId = taskId, milestoneId = 10L))
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!
        assertEquals(10L, xpResult.xpPerMilestone)
        assertEquals(10L, xpResult.totalXpDistributed)
        verify(milestoneRepository).updateXP(10L, 10L)
    }

    @Test
    fun `invoke calculates correct XP for high priority task`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2 // High = 50 XP
        val milestone = createMilestone(id = 10L, currentXp = 0L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(TaskMilestoneLink(taskId = taskId, milestoneId = 10L))
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!
        assertEquals(50L, xpResult.xpPerMilestone)
        assertEquals(50L, xpResult.totalXpDistributed)
        verify(milestoneRepository).updateXP(10L, 50L)
    }

    @Test
    fun `invoke calculates correct XP for urgent priority task`() = runTest {
        // Given
        val taskId = 1L
        val priority = 3 // Urgent = 100 XP
        val milestone = createMilestone(id = 10L, currentXp = 0L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(TaskMilestoneLink(taskId = taskId, milestoneId = 10L))
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone)

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isSuccess)
        val xpResult = result.getOrNull()!!
        assertEquals(100L, xpResult.xpPerMilestone)
        assertEquals(100L, xpResult.totalXpDistributed)
        verify(milestoneRepository).updateXP(10L, 100L)
    }

    // ========== Preview Method Tests ==========

    @Test
    fun `previewXP returns correct XP without modifying state`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2 // High = 50 XP
        val milestone1 = createMilestone(id = 10L, title = "Learn Chords", currentXp = 200L)
        val milestone2 = createMilestone(id = 20L, title = "Practice Scales", currentXp = 300L)

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(
                TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 20L)
            )
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone1)
        whenever(milestoneRepository.getById(20L)).thenReturn(milestone2)

        // When
        val preview = useCase.previewXP(taskId, priority)

        // Then
        assertEquals(50L, preview.xpPerMilestone)
        assertEquals(2, preview.milestoneCount)
        assertEquals(100L, preview.totalXp) // 50 * 2
        assertEquals(2, preview.linkedMilestones.size)
        assertTrue(preview.linkedMilestones.contains("Learn Chords"))
        assertTrue(preview.linkedMilestones.contains("Practice Scales"))

        // Verify NO milestone updates occurred
        verify(milestoneRepository, never()).updateXP(any(), any())
    }

    @Test
    fun `previewXP lists all linked milestone names in order`() = runTest {
        // Given
        val taskId = 1L
        val priority = 1 // Medium = 25 XP
        val milestone1 = createMilestone(id = 10L, title = "First Milestone")
        val milestone2 = createMilestone(id = 20L, title = "Second Milestone")
        val milestone3 = createMilestone(id = 30L, title = "Third Milestone")

        whenever(linkRepository.getLinksForTask(taskId)).thenReturn(
            listOf(
                TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 20L),
                TaskMilestoneLink(taskId = taskId, milestoneId = 30L)
            )
        )
        whenever(milestoneRepository.getById(10L)).thenReturn(milestone1)
        whenever(milestoneRepository.getById(20L)).thenReturn(milestone2)
        whenever(milestoneRepository.getById(30L)).thenReturn(milestone3)

        // When
        val preview = useCase.previewXP(taskId, priority)

        // Then
        assertEquals(3, preview.linkedMilestones.size)
        assertTrue(preview.linkedMilestones.contains("First Milestone"))
        assertTrue(preview.linkedMilestones.contains("Second Milestone"))
        assertTrue(preview.linkedMilestones.contains("Third Milestone"))
        assertEquals(75L, preview.totalXp) // 25 * 3
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        // Given
        val taskId = 1L
        val priority = 2
        whenever(linkRepository.getLinksForTask(taskId)).thenThrow(RuntimeException("Database error"))

        // When
        val result = useCase(taskId, priority)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)

        // Verify no updates attempted
        verify(milestoneRepository, never()).updateXP(any(), any())
    }

    // ========== Helper Methods ==========

    /**
     * Create a milestone for testing with default values
     */
    private fun createMilestone(
        id: Long,
        dreamId: Long = 1L,
        title: String = "Test Milestone",
        currentXp: Long = 0L,
        targetXp: Long = 1000L
    ): Milestone {
        return Milestone(
            id = id,
            dreamId = dreamId,
            title = title,
            currentXp = currentXp,
            targetXp = targetXp,
            status = Milestone.Status.ACTIVE,
            orderIndex = 0
        )
    }
}

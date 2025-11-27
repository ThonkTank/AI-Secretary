package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.service.DreamProgressService
import com.secretary.features.dreams.domain.service.LevelProgressionService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GetDreamProgressUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests comprehensive dream progress retrieval with statistics and level progression.
 * Uses real DreamProgressService and LevelProgressionService for accurate calculations.
 */
class GetDreamProgressUseCaseTest {

    private lateinit var dreamRepository: DreamRepository
    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var progressService: DreamProgressService
    private lateinit var levelService: LevelProgressionService
    private lateinit var getDreamProgressUseCase: GetDreamProgressUseCase

    @Before
    fun setUp() {
        dreamRepository = mock(DreamRepository::class.java)
        milestoneRepository = mock(MilestoneRepository::class.java)
        progressService = DreamProgressService() // Real instance
        levelService = LevelProgressionService() // Real instance
        getDreamProgressUseCase = GetDreamProgressUseCase(
            dreamRepository,
            milestoneRepository,
            progressService,
            levelService
        )
    }

    @Test
    fun `invoke returns dream with progress data`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(
            id = dreamId,
            title = "Learn Guitar",
            description = "Master guitar skills",
            totalXp = 2500L,
            currentLevel = 3
        )
        val milestones = listOf(
            Milestone(id = 1L, dreamId = dreamId, title = "M1", targetXp = 1000, currentXp = 1000, status = Milestone.Status.COMPLETED),
            Milestone(id = 2L, dreamId = dreamId, title = "M2", targetXp = 1000, currentXp = 500, status = Milestone.Status.ACTIVE),
            Milestone(id = 3L, dreamId = dreamId, title = "M3", targetXp = 1000, currentXp = 0, status = Milestone.Status.ACTIVE)
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamProgressUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals(dream, data!!.dream)
        assertEquals(milestones, data.milestones)
        assertNotNull(data.statistics)
        assertTrue(data.levelProgress >= 0f && data.levelProgress <= 1f)
        assertTrue(data.xpToNextLevel >= 0L)
    }

    @Test
    fun `invoke calculates correct milestone progress`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream", totalXp = 1000L)
        val milestones = listOf(
            Milestone(id = 1L, dreamId = dreamId, title = "M1", targetXp = 1000, currentXp = 1000, status = Milestone.Status.COMPLETED),
            Milestone(id = 2L, dreamId = dreamId, title = "M2", targetXp = 1000, currentXp = 500, status = Milestone.Status.ACTIVE),
            Milestone(id = 3L, dreamId = dreamId, title = "M3", targetXp = 1000, currentXp = 250, status = Milestone.Status.ACTIVE),
            Milestone(id = 4L, dreamId = dreamId, title = "M4", targetXp = 1000, currentXp = 0, status = Milestone.Status.ACTIVE)
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamProgressUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        val stats = data.statistics

        // 1 completed out of 4 = 25% progress
        assertEquals(4, stats.totalMilestones)
        assertEquals(1, stats.completedMilestones)
        assertEquals(25f, stats.progressPercentage, 0.1f)
    }

    @Test
    fun `invoke includes dream statistics`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream", totalXp = 5000L)
        val milestones = listOf(
            Milestone(id = 1L, dreamId = dreamId, title = "M1", targetXp = 1000, currentXp = 1000, status = Milestone.Status.COMPLETED),
            Milestone(id = 2L, dreamId = dreamId, title = "M2", targetXp = 2000, currentXp = 2000, status = Milestone.Status.COMPLETED),
            Milestone(id = 3L, dreamId = dreamId, title = "M3", targetXp = 1500, currentXp = 750, status = Milestone.Status.ACTIVE)
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamProgressUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        val stats = data.statistics

        assertEquals(3, stats.totalMilestones)
        assertEquals(2, stats.completedMilestones)
        // XP earned = 1000 + 2000 = 3000 (only completed milestones)
        assertEquals(3000L, stats.totalXpEarned)
        // Average milestone progress: (1.0 + 1.0 + 0.5) / 3 = 0.833...
        assertEquals(0.833f, stats.averageMilestoneProgress, 0.01f)
        // 2 out of 3 completed = 66.67%
        assertEquals(66.67f, stats.progressPercentage, 0.1f)
    }

    @Test
    fun `invoke includes level progress`() = runTest {
        // Given
        val dreamId = 1L
        // Level 4 requires 4500 XP (level 5 requires 8000 XP)
        // With 6000 XP: in level 4, progress = (6000 - 4500) / (8000 - 4500) = 1500 / 3500 = 0.4286
        val dream = Dream(id = dreamId, title = "Test Dream", totalXp = 6000L, currentLevel = 4)
        val milestones = listOf(
            Milestone(id = 1L, dreamId = dreamId, title = "M1", targetXp = 1000, currentXp = 1000, status = Milestone.Status.COMPLETED)
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamProgressUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!

        // Level progress should be around 0.4286 (43%)
        assertEquals(0.4286f, data.levelProgress, 0.01f)
        // XP to next level: 8000 - 6000 = 2000
        assertEquals(2000L, data.xpToNextLevel)
    }

    @Test
    fun `invoke with no milestones returns empty statistics`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "New Dream", totalXp = 0L)
        val milestones = emptyList<Milestone>()
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamProgressUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!

        assertEquals(0, data.statistics.totalMilestones)
        assertEquals(0, data.statistics.completedMilestones)
        assertEquals(0L, data.statistics.totalXpEarned)
        assertEquals(0f, data.statistics.averageMilestoneProgress, 0.001f)
        assertEquals(0f, data.statistics.progressPercentage, 0.001f)
        assertTrue(data.milestones.isEmpty())
    }

    @Test
    fun `invoke with non-existent dream returns failure`() = runTest {
        // Given
        val nonExistentDreamId = 999L
        whenever(dreamRepository.getById(nonExistentDreamId)).thenReturn(null)

        // When
        val result = getDreamProgressUseCase(nonExistentDreamId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream not found: $nonExistentDreamId", result.exceptionOrNull()?.message)
        verify(dreamRepository).getById(nonExistentDreamId)
        verify(milestoneRepository, never()).getByDreamId(any())
    }
}

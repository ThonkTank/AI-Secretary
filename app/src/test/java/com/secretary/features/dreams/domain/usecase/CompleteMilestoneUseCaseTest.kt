package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import com.secretary.features.dreams.domain.service.LevelProgressionService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for CompleteMilestoneUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests milestone completion with XP transfer and level-up detection.
 */
class CompleteMilestoneUseCaseTest {

    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var dreamRepository: DreamRepository
    private lateinit var levelProgressionService: LevelProgressionService
    private lateinit var completeMilestoneUseCase: CompleteMilestoneUseCase

    @Before
    fun setUp() {
        milestoneRepository = mock(MilestoneRepository::class.java)
        dreamRepository = mock(DreamRepository::class.java)
        levelProgressionService = LevelProgressionService() // Real instance - pure Kotlin
        completeMilestoneUseCase = CompleteMilestoneUseCase(
            milestoneRepository,
            dreamRepository,
            levelProgressionService
        )
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `invoke with non-existent milestone fails`() = runTest {
        // Given
        val milestoneId = 999L
        whenever(milestoneRepository.getById(milestoneId)).thenReturn(null)

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Milestone not found: $milestoneId", result.exceptionOrNull()?.message)
        verify(milestoneRepository).getById(milestoneId)
        verify(dreamRepository, never()).update(any())
        verify(milestoneRepository, never()).complete(any(), any())
    }

    @Test
    fun `invoke with already completed milestone fails`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val completedMilestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "Completed Milestone",
            status = Milestone.Status.COMPLETED,
            currentXp = 1000,
            targetXp = 1000,
            completedAt = System.currentTimeMillis()
        )
        whenever(milestoneRepository.getById(milestoneId)).thenReturn(completedMilestone)

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Milestone already completed: $milestoneId", result.exceptionOrNull()?.message)
        verify(milestoneRepository).getById(milestoneId)
        verify(dreamRepository, never()).getById(any())
        verify(dreamRepository, never()).update(any())
        verify(milestoneRepository, never()).complete(any(), any())
    }

    // ========== XP TRANSFER TESTS ==========

    @Test
    fun `invoke transfers milestone XP to dream`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val milestoneXp = 500L
        val dreamXp = 1000L

        val milestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "Test Milestone",
            currentXp = milestoneXp,
            targetXp = 1000,
            status = Milestone.Status.ACTIVE
        )
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            totalXp = dreamXp,
            currentLevel = 2
        )

        whenever(milestoneRepository.getById(milestoneId)).thenReturn(milestone)
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        val captor = argumentCaptor<Dream>()

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        val completionResult = result.getOrNull()!!
        assertEquals(milestoneXp, completionResult.xpTransferred)

        verify(dreamRepository).update(captor.capture())
        val updatedDream = captor.firstValue
        assertEquals(dreamXp + milestoneXp, updatedDream.totalXp)
    }

    @Test
    fun `invoke updates dream total XP correctly`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val milestoneXp = 750L
        val initialDreamXp = 250L
        val expectedTotalXp = initialDreamXp + milestoneXp // 1000L

        val milestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "Test Milestone",
            currentXp = milestoneXp,
            targetXp = 1000,
            status = Milestone.Status.ACTIVE
        )
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            totalXp = initialDreamXp,
            currentLevel = 1
        )

        whenever(milestoneRepository.getById(milestoneId)).thenReturn(milestone)
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        val captor = argumentCaptor<Dream>()

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).update(captor.capture())
        val updatedDream = captor.firstValue
        assertEquals(expectedTotalXp, updatedDream.totalXp)
        verify(milestoneRepository).complete(eq(milestoneId), any())
    }

    // ========== LEVEL UP DETECTION TESTS ==========

    @Test
    fun `invoke detects level up when threshold crossed`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val milestoneXp = 300L
        val initialDreamXp = 250L // Total will be 550, crosses level 2 threshold (500 XP)

        val milestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "Level Up Milestone",
            currentXp = milestoneXp,
            targetXp = 1000,
            status = Milestone.Status.ACTIVE
        )
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            totalXp = initialDreamXp,
            currentLevel = 1
        )

        whenever(milestoneRepository.getById(milestoneId)).thenReturn(milestone)
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        val completionResult = result.getOrNull()!!
        assertTrue(completionResult.hasLeveledUp())
        assertNotNull(completionResult.levelUpResult)

        val levelUpResult = completionResult.levelUpResult!!
        assertTrue(levelUpResult.leveledUp)
        assertEquals(1, levelUpResult.previousLevel)
        assertEquals(2, levelUpResult.newLevel)
        assertEquals(1, levelUpResult.levelsGained)
    }

    @Test
    fun `invoke returns null level up result when no level up`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val milestoneXp = 100L
        val initialDreamXp = 200L // Total will be 300, not enough for level 2 (500 XP)

        val milestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "No Level Up Milestone",
            currentXp = milestoneXp,
            targetXp = 1000,
            status = Milestone.Status.ACTIVE
        )
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            totalXp = initialDreamXp,
            currentLevel = 1
        )

        whenever(milestoneRepository.getById(milestoneId)).thenReturn(milestone)
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        val completionResult = result.getOrNull()!!
        assertFalse(completionResult.hasLeveledUp())
        assertNull(completionResult.levelUpResult)
    }

    @Test
    fun `invoke updates dream current level on level up`() = runTest {
        // Given
        val milestoneId = 1L
        val dreamId = 1L
        val milestoneXp = 1500L
        val initialDreamXp = 1000L // Total 2500, reaches level 3 (2000 XP threshold)

        val milestone = Milestone(
            id = milestoneId,
            dreamId = dreamId,
            title = "Big Milestone",
            currentXp = milestoneXp,
            targetXp = 2000,
            status = Milestone.Status.ACTIVE
        )
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            totalXp = initialDreamXp,
            currentLevel = 2
        )

        whenever(milestoneRepository.getById(milestoneId)).thenReturn(milestone)
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        val captor = argumentCaptor<Dream>()

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).update(captor.capture())
        val updatedDream = captor.firstValue
        assertEquals(3, updatedDream.currentLevel)
        assertEquals(2500L, updatedDream.totalXp)
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        // Given
        val milestoneId = 1L
        whenever(milestoneRepository.getById(milestoneId))
            .thenThrow(RuntimeException("Database connection failed"))

        // When
        val result = completeMilestoneUseCase(milestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database connection failed", result.exceptionOrNull()?.message)
        verify(dreamRepository, never()).update(any())
        verify(milestoneRepository, never()).complete(any(), any())
    }
}

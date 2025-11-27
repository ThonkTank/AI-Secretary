package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.repository.DreamRepository
import com.secretary.features.dreams.domain.repository.MilestoneRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for CreateMilestoneUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests milestone creation with validation and error handling.
 */
class CreateMilestoneUseCaseTest {

    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var dreamRepository: DreamRepository
    private lateinit var createMilestoneUseCase: CreateMilestoneUseCase

    @Before
    fun setUp() {
        milestoneRepository = mock(MilestoneRepository::class.java)
        dreamRepository = mock(DreamRepository::class.java)
        createMilestoneUseCase = CreateMilestoneUseCase(milestoneRepository, dreamRepository)
    }

    @Test
    fun `invoke with valid data succeeds`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Learn Guitar")
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(emptyList())
        whenever(milestoneRepository.insert(any())).thenReturn(1L)

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Master C chord",
            description = "Practice 30 minutes daily"
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        verify(dreamRepository).getById(dreamId)
        verify(milestoneRepository).getByDreamId(dreamId)
        verify(milestoneRepository).insert(any())
    }

    @Test
    fun `invoke with blank title fails`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "   "
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Milestone title cannot be empty", result.exceptionOrNull()?.message)
        verify(milestoneRepository, never()).insert(any())
    }

    @Test
    fun `invoke with invalid dream ID fails`() = runTest {
        // Given
        val invalidDreamId = 999L
        whenever(dreamRepository.getById(invalidDreamId)).thenReturn(null)

        // When
        val result = createMilestoneUseCase(
            dreamId = invalidDreamId,
            title = "Test Milestone"
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream not found: $invalidDreamId", result.exceptionOrNull()?.message)
        verify(dreamRepository).getById(invalidDreamId)
        verify(milestoneRepository, never()).insert(any())
    }

    @Test
    fun `invoke sets default target XP`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(emptyList())
        whenever(milestoneRepository.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<Milestone>()

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Test Milestone"
            // targetXp not specified, should default to 1000
        )

        // Then
        assertTrue(result.isSuccess)
        verify(milestoneRepository).insert(captor.capture())
        assertEquals(1000L, captor.firstValue.targetXp)
    }

    @Test
    fun `invoke calculates correct order index`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        val existingMilestones = listOf(
            Milestone(id = 1L, dreamId = dreamId, title = "First", orderIndex = 0),
            Milestone(id = 2L, dreamId = dreamId, title = "Second", orderIndex = 1),
            Milestone(id = 3L, dreamId = dreamId, title = "Third", orderIndex = 2)
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(existingMilestones)
        whenever(milestoneRepository.insert(any())).thenReturn(4L)
        val captor = argumentCaptor<Milestone>()

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Fourth Milestone"
        )

        // Then
        assertTrue(result.isSuccess)
        verify(milestoneRepository).insert(captor.capture())
        assertEquals(3, captor.firstValue.orderIndex) // Should be size of existing list
    }

    @Test
    fun `invoke sets status to active`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(emptyList())
        whenever(milestoneRepository.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<Milestone>()

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Test Milestone"
        )

        // Then
        assertTrue(result.isSuccess)
        verify(milestoneRepository).insert(captor.capture())
        val capturedMilestone = captor.firstValue
        assertEquals(Milestone.Status.ACTIVE, capturedMilestone.status)
        assertEquals(0L, capturedMilestone.currentXp)
    }

    @Test
    fun `invoke sets created at timestamp`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        val beforeTimestamp = System.currentTimeMillis()
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(emptyList())
        whenever(milestoneRepository.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<Milestone>()

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Test Milestone"
        )
        val afterTimestamp = System.currentTimeMillis()

        // Then
        assertTrue(result.isSuccess)
        verify(milestoneRepository).insert(captor.capture())
        val capturedMilestone = captor.firstValue
        assertTrue(capturedMilestone.createdAt >= beforeTimestamp)
        assertTrue(capturedMilestone.createdAt <= afterTimestamp)
        assertNull(capturedMilestone.completedAt)
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(id = dreamId, title = "Test Dream")
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenThrow(RuntimeException("Database error"))

        // When
        val result = createMilestoneUseCase(
            dreamId = dreamId,
            title = "Test Milestone"
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }
}

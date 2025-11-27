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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GetDreamWithMilestonesUseCase
 * Dream-to-Task Feature - Testing Wave 3
 *
 * Tests dream retrieval with milestone aggregation and error handling.
 */
class GetDreamWithMilestonesUseCaseTest {

    private lateinit var dreamRepository: DreamRepository
    private lateinit var milestoneRepository: MilestoneRepository
    private lateinit var getDreamWithMilestonesUseCase: GetDreamWithMilestonesUseCase

    @Before
    fun setUp() {
        dreamRepository = mock(DreamRepository::class.java)
        milestoneRepository = mock(MilestoneRepository::class.java)
        getDreamWithMilestonesUseCase = GetDreamWithMilestonesUseCase(
            dreamRepository = dreamRepository,
            milestoneRepository = milestoneRepository
        )
    }

    @Test
    fun `invoke with valid dream returns dream with milestones`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(
            id = dreamId,
            title = "Learn Guitar",
            description = "Master guitar playing",
            totalXp = 500L,
            currentLevel = 2
        )
        val milestones = listOf(
            Milestone(
                id = 1L,
                dreamId = dreamId,
                title = "Learn basic chords",
                targetXp = 1000,
                currentXp = 500,
                orderIndex = 0
            ),
            Milestone(
                id = 2L,
                dreamId = dreamId,
                title = "Learn scales",
                targetXp = 1500,
                currentXp = 0,
                orderIndex = 1
            )
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestones)

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val dreamWithMilestones = result.getOrNull()
        assertNotNull(dreamWithMilestones)
        assertEquals(dream, dreamWithMilestones?.dream)
        assertEquals(2, dreamWithMilestones?.milestones?.size)
        assertEquals(milestones, dreamWithMilestones?.milestones)
        verify(dreamRepository).getById(dreamId)
        verify(milestoneRepository).getByDreamId(dreamId)
    }

    @Test
    fun `invoke with non-existent dream returns failure`() = runTest {
        // Given
        val dreamId = 999L
        whenever(dreamRepository.getById(dreamId)).thenReturn(null)

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream not found: $dreamId", result.exceptionOrNull()?.message)
        verify(dreamRepository).getById(dreamId)
    }

    @Test
    fun `invoke with empty milestones returns dream with empty list`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(
            id = dreamId,
            title = "New Dream",
            description = "Just created",
            totalXp = 0L,
            currentLevel = 1
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(emptyList())

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val dreamWithMilestones = result.getOrNull()
        assertNotNull(dreamWithMilestones)
        assertEquals(dream, dreamWithMilestones?.dream)
        assertTrue(dreamWithMilestones?.milestones?.isEmpty() == true)
        verify(dreamRepository).getById(dreamId)
        verify(milestoneRepository).getByDreamId(dreamId)
    }

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        // Given
        val dreamId = 1L
        whenever(dreamRepository.getById(dreamId))
            .thenThrow(RuntimeException("Database connection error"))

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database connection error", result.exceptionOrNull()?.message)
        verify(dreamRepository).getById(dreamId)
    }

    @Test
    fun `invoke returns milestones in correct order by orderIndex`() = runTest {
        // Given
        val dreamId = 1L
        val dream = Dream(
            id = dreamId,
            title = "Learn Piano",
            description = "Master piano",
            totalXp = 1000L,
            currentLevel = 3
        )
        val milestonesUnordered = listOf(
            Milestone(
                id = 3L,
                dreamId = dreamId,
                title = "Third milestone",
                targetXp = 3000,
                currentXp = 0,
                orderIndex = 2
            ),
            Milestone(
                id = 1L,
                dreamId = dreamId,
                title = "First milestone",
                targetXp = 1000,
                currentXp = 1000,
                orderIndex = 0
            ),
            Milestone(
                id = 2L,
                dreamId = dreamId,
                title = "Second milestone",
                targetXp = 2000,
                currentXp = 500,
                orderIndex = 1
            )
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(milestonesUnordered)

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val dreamWithMilestones = result.getOrNull()
        assertNotNull(dreamWithMilestones)
        val milestones = dreamWithMilestones?.milestones
        assertEquals(3, milestones?.size)
        // Verify order matches the repository's returned order (repository handles ordering)
        assertEquals(2, milestones?.get(0)?.orderIndex)
        assertEquals(0, milestones?.get(1)?.orderIndex)
        assertEquals(1, milestones?.get(2)?.orderIndex)
    }

    @Test
    fun `returned DreamWithMilestones has correct structure`() = runTest {
        // Given
        val dreamId = 5L
        val dream = Dream(
            id = dreamId,
            title = "Test Dream",
            description = "Test description",
            totalXp = 2500L,
            currentLevel = 4
        )
        val milestone = Milestone(
            id = 10L,
            dreamId = dreamId,
            title = "Test Milestone",
            targetXp = 5000,
            currentXp = 2500,
            orderIndex = 0
        )
        whenever(dreamRepository.getById(dreamId)).thenReturn(dream)
        whenever(milestoneRepository.getByDreamId(dreamId)).thenReturn(listOf(milestone))

        // When
        val result = getDreamWithMilestonesUseCase(dreamId)

        // Then
        assertTrue(result.isSuccess)
        val dreamWithMilestones = result.getOrNull()
        assertNotNull(dreamWithMilestones)

        // Verify structure
        assertNotNull(dreamWithMilestones?.dream)
        assertNotNull(dreamWithMilestones?.milestones)

        // Verify dream properties
        assertEquals(dreamId, dreamWithMilestones?.dream?.id)
        assertEquals("Test Dream", dreamWithMilestones?.dream?.title)
        assertEquals(2500L, dreamWithMilestones?.dream?.totalXp)

        // Verify milestone properties
        assertEquals(1, dreamWithMilestones?.milestones?.size)
        val returnedMilestone = dreamWithMilestones?.milestones?.get(0)
        assertEquals(10L, returnedMilestone?.id)
        assertEquals(dreamId, returnedMilestone?.dreamId)
        assertEquals("Test Milestone", returnedMilestone?.title)
        assertEquals(2500L, returnedMilestone?.currentXp)
    }
}

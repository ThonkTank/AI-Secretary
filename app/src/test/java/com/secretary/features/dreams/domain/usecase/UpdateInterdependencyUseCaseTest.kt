package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.MilestoneInterdependency
import com.secretary.features.dreams.domain.repository.InterdependencyRepository
import com.secretary.features.dreams.domain.service.InterdependencyService
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
 * Unit tests for UpdateInterdependencyUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests interdependency creation/update with validation.
 */
class UpdateInterdependencyUseCaseTest {

    private lateinit var interdependencyRepository: InterdependencyRepository
    private lateinit var interdependencyService: InterdependencyService
    private lateinit var updateInterdependencyUseCase: UpdateInterdependencyUseCase

    @Before
    fun setUp() {
        interdependencyRepository = mock(InterdependencyRepository::class.java)
        interdependencyService = InterdependencyService() // Real instance - pure Kotlin
        updateInterdependencyUseCase = UpdateInterdependencyUseCase(
            interdependencyRepository,
            interdependencyService
        )
    }

    // ========== VALIDATION TESTS ==========

    @Test
    fun `invoke with rating above 5 fails`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val invalidRating = 6

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = invalidRating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid rating") == true)
        verify(interdependencyRepository, never()).upsert(any())
    }

    @Test
    fun `invoke with rating below -5 fails`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val invalidRating = -6

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = invalidRating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid rating") == true)
        verify(interdependencyRepository, never()).upsert(any())
    }

    @Test
    fun `invoke with invalid source milestone ID fails`() = runTest {
        // Given
        val invalidSourceId = 0L
        val targetId = 2L
        val rating = 3

        // When
        val result = updateInterdependencyUseCase(
            sourceId = invalidSourceId,
            targetId = targetId,
            rating = rating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid milestone IDs", result.exceptionOrNull()?.message)
        verify(interdependencyRepository, never()).upsert(any())
    }

    @Test
    fun `invoke with invalid target milestone ID fails`() = runTest {
        // Given
        val sourceId = 1L
        val invalidTargetId = -1L
        val rating = 3

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = invalidTargetId,
            rating = rating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid milestone IDs", result.exceptionOrNull()?.message)
        verify(interdependencyRepository, never()).upsert(any())
    }

    @Test
    fun `invoke with same source and target fails`() = runTest {
        // Given
        val milestoneId = 1L
        val rating = 3

        // When
        val result = updateInterdependencyUseCase(
            sourceId = milestoneId,
            targetId = milestoneId,
            rating = rating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Milestone cannot depend on itself", result.exceptionOrNull()?.message)
        verify(interdependencyRepository, never()).upsert(any())
    }

    // ========== SUCCESS CASES ==========

    @Test
    fun `invoke creates new interdependency`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val rating = 4
        val description = "Strong positive dependency"
        val captor = argumentCaptor<MilestoneInterdependency>()

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = rating,
            description = description
        )

        // Then
        assertTrue(result.isSuccess)
        verify(interdependencyRepository).upsert(captor.capture())

        val captured = captor.firstValue
        assertEquals(sourceId, captured.sourceMilestoneId)
        assertEquals(targetId, captured.targetMilestoneId)
        assertEquals(rating, captured.impactRating)
        assertEquals(description, captured.description)
        assertTrue(captured.createdAt > 0)
    }

    @Test
    fun `invoke updates existing interdependency`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val newRating = -3
        val newDescription = "Updated to negative impact"
        val captor = argumentCaptor<MilestoneInterdependency>()

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = newRating,
            description = newDescription
        )

        // Then
        assertTrue(result.isSuccess)
        verify(interdependencyRepository).upsert(captor.capture())

        val captured = captor.firstValue
        assertEquals(sourceId, captured.sourceMilestoneId)
        assertEquals(targetId, captured.targetMilestoneId)
        assertEquals(newRating, captured.impactRating)
        assertEquals(newDescription, captured.description)
    }

    @Test
    fun `invoke handles null description`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val rating = 2
        val captor = argumentCaptor<MilestoneInterdependency>()

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = rating,
            description = null
        )

        // Then
        assertTrue(result.isSuccess)
        verify(interdependencyRepository).upsert(captor.capture())

        val captured = captor.firstValue
        assertNull(captured.description)
    }

    @Test
    fun `invoke trims description whitespace`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val rating = 3
        val descriptionWithSpaces = "  Test description  "
        val captor = argumentCaptor<MilestoneInterdependency>()

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = rating,
            description = descriptionWithSpaces
        )

        // Then
        assertTrue(result.isSuccess)
        verify(interdependencyRepository).upsert(captor.capture())

        val captured = captor.firstValue
        assertEquals("Test description", captured.description)
    }

    @Test
    fun `invoke accepts valid boundary ratings`() = runTest {
        // Test minimum rating (-5)
        val result1 = updateInterdependencyUseCase(
            sourceId = 1L,
            targetId = 2L,
            rating = -5
        )
        assertTrue(result1.isSuccess)

        // Test maximum rating (+5)
        val result2 = updateInterdependencyUseCase(
            sourceId = 1L,
            targetId = 3L,
            rating = 5
        )
        assertTrue(result2.isSuccess)

        // Test zero rating
        val result3 = updateInterdependencyUseCase(
            sourceId = 1L,
            targetId = 4L,
            rating = 0
        )
        assertTrue(result3.isSuccess)

        verify(interdependencyRepository, times(3)).upsert(any())
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        // Given
        val sourceId = 1L
        val targetId = 2L
        val rating = 3
        whenever(interdependencyRepository.upsert(any()))
            .thenThrow(RuntimeException("Database connection failed"))

        // When
        val result = updateInterdependencyUseCase(
            sourceId = sourceId,
            targetId = targetId,
            rating = rating
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database connection failed", result.exceptionOrNull()?.message)
    }
}

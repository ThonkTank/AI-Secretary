package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.repository.DreamRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for CreateDreamUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests dream creation with validation and error handling.
 */
class CreateDreamUseCaseTest {

    private lateinit var dreamRepository: DreamRepository
    private lateinit var createDreamUseCase: CreateDreamUseCase

    @Before
    fun setUp() {
        dreamRepository = mock(DreamRepository::class.java)
        createDreamUseCase = CreateDreamUseCase(dreamRepository)
    }

    @Test
    fun `invoke with valid title succeeds`() = runTest {
        // Given
        whenever(dreamRepository.insert(any())).thenReturn(1L)

        // When
        val result = createDreamUseCase(
            title = "Learn Guitar",
            description = "Master basic chords"
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1L, result.getOrNull())
        verify(dreamRepository).insert(any())
    }

    @Test
    fun `invoke with blank title fails with exception`() = runTest {
        // Given
        val blankTitle = ""

        // When
        val result = createDreamUseCase(title = blankTitle)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream title cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with whitespace only title fails`() = runTest {
        // Given
        val whitespaceTitle = "   \t\n  "

        // When
        val result = createDreamUseCase(title = whitespaceTitle)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream title cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke trims input strings`() = runTest {
        // Given
        whenever(dreamRepository.insert(any())).thenReturn(1L)
        val titleWithSpaces = "  Learn Guitar  "
        val descriptionWithSpaces = "  Master basic chords  "
        val captor = argumentCaptor<Dream>()

        // When
        val result = createDreamUseCase(
            title = titleWithSpaces,
            description = descriptionWithSpaces
        )

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).insert(captor.capture())
        val capturedDream = captor.firstValue
        assertEquals("Learn Guitar", capturedDream.title)
        assertEquals("Master basic chords", capturedDream.description)
    }

    @Test
    fun `invoke sets default values correctly`() = runTest {
        // Given
        whenever(dreamRepository.insert(any())).thenReturn(1L)
        val captor = argumentCaptor<Dream>()

        // When
        val result = createDreamUseCase(title = "Test Dream")

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).insert(captor.capture())
        val capturedDream = captor.firstValue
        assertEquals(0L, capturedDream.totalXp)
        assertEquals(1, capturedDream.currentLevel)
        assertEquals(false, capturedDream.isArchived)
        assertTrue(capturedDream.createdAt > 0)
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        // Given
        whenever(dreamRepository.insert(any())).thenThrow(RuntimeException("Database error"))

        // When
        val result = createDreamUseCase(title = "Test Dream")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }
}

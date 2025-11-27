package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.repository.DreamRepository
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
 * Unit tests for UpdateDreamUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests dream updates with validation and error handling.
 */
class UpdateDreamUseCaseTest {

    private lateinit var dreamRepository: DreamRepository
    private lateinit var updateDreamUseCase: UpdateDreamUseCase

    @Before
    fun setUp() {
        dreamRepository = mock(DreamRepository::class.java)
        updateDreamUseCase = UpdateDreamUseCase(dreamRepository)
    }

    @Test
    fun `invoke with valid data succeeds`() = runTest {
        // Given
        val existingDream = Dream(id = 1L, title = "Original Title", totalXp = 500, currentLevel = 2)
        val updatedDream = existingDream.copy(title = "Updated Title", description = "New description")
        whenever(dreamRepository.getById(1L)).thenReturn(existingDream)

        // When
        val result = updateDreamUseCase(updatedDream)

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).getById(1L)
        verify(dreamRepository).update(updatedDream)
    }

    @Test
    fun `invoke with non-existent dream fails`() = runTest {
        // Given
        val dream = Dream(id = 999L, title = "Test Dream")
        whenever(dreamRepository.getById(999L)).thenReturn(null)

        // When
        val result = updateDreamUseCase(dream)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream not found: 999", result.exceptionOrNull()?.message)
        verify(dreamRepository).getById(999L)
        verify(dreamRepository, never()).update(any())
    }

    @Test
    fun `invoke with blank title fails`() = runTest {
        // Given
        val existingDream = Dream(id = 1L, title = "Original Title")
        val updatedDream = existingDream.copy(title = "")
        whenever(dreamRepository.getById(1L)).thenReturn(existingDream)

        // When
        val result = updateDreamUseCase(updatedDream)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Dream title cannot be empty", result.exceptionOrNull()?.message)
        verify(dreamRepository, never()).update(any())
    }

    @Test
    fun `invoke preserves existing XP and level`() = runTest {
        // Given
        val existingDream = Dream(
            id = 1L,
            title = "Original",
            totalXp = 1500,
            currentLevel = 3
        )
        val updatedDream = existingDream.copy(
            title = "Updated Title",
            description = "New description"
        )
        whenever(dreamRepository.getById(1L)).thenReturn(existingDream)
        val captor = argumentCaptor<Dream>()

        // When
        val result = updateDreamUseCase(updatedDream)

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).update(captor.capture())
        val capturedDream = captor.firstValue
        assertEquals(1500L, capturedDream.totalXp)
        assertEquals(3, capturedDream.currentLevel)
        assertEquals("Updated Title", capturedDream.title)
    }

    @Test
    fun `invoke updates only modified fields`() = runTest {
        // Given
        val existingDream = Dream(
            id = 1L,
            title = "Original",
            description = "Old description",
            iconName = "icon_guitar",
            color = 0xFF6200EE.toInt(),
            totalXp = 500,
            currentLevel = 2,
            createdAt = 123456789L,
            isArchived = false
        )
        val updatedDream = existingDream.copy(
            description = "Updated description",
            iconName = "icon_piano"
        )
        whenever(dreamRepository.getById(1L)).thenReturn(existingDream)
        val captor = argumentCaptor<Dream>()

        // When
        val result = updateDreamUseCase(updatedDream)

        // Then
        assertTrue(result.isSuccess)
        verify(dreamRepository).update(captor.capture())
        val capturedDream = captor.firstValue
        assertEquals(1L, capturedDream.id)
        assertEquals("Original", capturedDream.title)
        assertEquals("Updated description", capturedDream.description)
        assertEquals("icon_piano", capturedDream.iconName)
        assertEquals(0xFF6200EE.toInt(), capturedDream.color)
        assertEquals(500L, capturedDream.totalXp)
        assertEquals(2, capturedDream.currentLevel)
        assertEquals(123456789L, capturedDream.createdAt)
        assertEquals(false, capturedDream.isArchived)
    }

    @Test
    fun `invoke handles repository exception`() = runTest {
        // Given
        val existingDream = Dream(id = 1L, title = "Test")
        val updatedDream = existingDream.copy(title = "Updated")
        whenever(dreamRepository.getById(1L)).thenReturn(existingDream)
        whenever(dreamRepository.update(any())).thenThrow(RuntimeException("Database error"))

        // When
        val result = updateDreamUseCase(updatedDream)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }
}

package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.TaskMilestoneLink
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GetTaskMilestoneLinksUseCase
 * Dream-to-Task Feature - Testing Wave 3
 *
 * Tests retrieval of task-milestone links with validation and error handling.
 */
class GetTaskMilestoneLinksUseCaseTest {

    private lateinit var repository: TaskMilestoneLinkRepository
    private lateinit var getTaskMilestoneLinksUseCase: GetTaskMilestoneLinksUseCase

    @Before
    fun setUp() {
        repository = mock(TaskMilestoneLinkRepository::class.java)
        getTaskMilestoneLinksUseCase = GetTaskMilestoneLinksUseCase(repository)
    }

    // Tests for getLinksForTask
    @Test
    fun `getLinksForTask with valid task returns links`() = runTest {
        // Given
        val taskId = 1L
        val links = listOf(
            TaskMilestoneLink(taskId = taskId, milestoneId = 1L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 2L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 3L)
        )
        whenever(repository.getLinksForTask(taskId)).thenReturn(links)

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForTask(taskId)

        // Then
        assertTrue(result.isSuccess)
        val returnedLinks = result.getOrNull()
        assertNotNull(returnedLinks)
        assertEquals(3, returnedLinks?.size)
        assertEquals(links, returnedLinks)
        verify(repository).getLinksForTask(taskId)
    }

    @Test
    fun `getLinksForTask with invalid taskId returns failure`() = runTest {
        // Given
        val invalidTaskId = 0L

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForTask(invalidTaskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $invalidTaskId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getLinksForTask with negative taskId returns failure`() = runTest {
        // Given
        val negativeTaskId = -5L

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForTask(negativeTaskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $negativeTaskId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getLinksForTask with empty result returns empty list`() = runTest {
        // Given
        val taskId = 99L
        whenever(repository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForTask(taskId)

        // Then
        assertTrue(result.isSuccess)
        val returnedLinks = result.getOrNull()
        assertNotNull(returnedLinks)
        assertTrue(returnedLinks?.isEmpty() == true)
        verify(repository).getLinksForTask(taskId)
    }

    @Test
    fun `getLinksForTask handles repository exception gracefully`() = runTest {
        // Given
        val taskId = 1L
        whenever(repository.getLinksForTask(taskId))
            .thenThrow(RuntimeException("Database error"))

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForTask(taskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)
        verify(repository).getLinksForTask(taskId)
    }

    // Tests for getLinksForMilestone
    @Test
    fun `getLinksForMilestone with valid milestone returns links`() = runTest {
        // Given
        val milestoneId = 5L
        val links = listOf(
            TaskMilestoneLink(taskId = 1L, milestoneId = milestoneId),
            TaskMilestoneLink(taskId = 2L, milestoneId = milestoneId)
        )
        whenever(repository.getLinksForMilestone(milestoneId)).thenReturn(links)

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForMilestone(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        val returnedLinks = result.getOrNull()
        assertNotNull(returnedLinks)
        assertEquals(2, returnedLinks?.size)
        assertEquals(links, returnedLinks)
        verify(repository).getLinksForMilestone(milestoneId)
    }

    @Test
    fun `getLinksForMilestone with invalid milestoneId returns failure`() = runTest {
        // Given
        val invalidMilestoneId = 0L

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForMilestone(invalidMilestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid milestone ID: $invalidMilestoneId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getLinksForMilestone with negative milestoneId returns failure`() = runTest {
        // Given
        val negativeMilestoneId = -10L

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForMilestone(negativeMilestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid milestone ID: $negativeMilestoneId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getLinksForMilestone with empty result returns empty list`() = runTest {
        // Given
        val milestoneId = 42L
        whenever(repository.getLinksForMilestone(milestoneId)).thenReturn(emptyList())

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForMilestone(milestoneId)

        // Then
        assertTrue(result.isSuccess)
        val returnedLinks = result.getOrNull()
        assertNotNull(returnedLinks)
        assertTrue(returnedLinks?.isEmpty() == true)
        verify(repository).getLinksForMilestone(milestoneId)
    }

    @Test
    fun `getLinksForMilestone handles repository exception gracefully`() = runTest {
        // Given
        val milestoneId = 5L
        whenever(repository.getLinksForMilestone(milestoneId))
            .thenThrow(RuntimeException("Connection timeout"))

        // When
        val result = getTaskMilestoneLinksUseCase.getLinksForMilestone(milestoneId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
        verify(repository).getLinksForMilestone(milestoneId)
    }

    // Tests for getMilestoneCountForTask
    @Test
    fun `getMilestoneCountForTask returns correct count`() = runTest {
        // Given
        val taskId = 10L
        val expectedCount = 4
        whenever(repository.getMilestoneCountForTask(taskId)).thenReturn(expectedCount)

        // When
        val result = getTaskMilestoneLinksUseCase.getMilestoneCountForTask(taskId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedCount, result.getOrNull())
        verify(repository).getMilestoneCountForTask(taskId)
    }

    @Test
    fun `getMilestoneCountForTask with invalid taskId returns failure`() = runTest {
        // Given
        val invalidTaskId = 0L

        // When
        val result = getTaskMilestoneLinksUseCase.getMilestoneCountForTask(invalidTaskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $invalidTaskId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getMilestoneCountForTask with negative taskId returns failure`() = runTest {
        // Given
        val negativeTaskId = -3L

        // When
        val result = getTaskMilestoneLinksUseCase.getMilestoneCountForTask(negativeTaskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $negativeTaskId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getMilestoneCountForTask with no links returns 0`() = runTest {
        // Given
        val taskId = 25L
        whenever(repository.getMilestoneCountForTask(taskId)).thenReturn(0)

        // When
        val result = getTaskMilestoneLinksUseCase.getMilestoneCountForTask(taskId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        verify(repository).getMilestoneCountForTask(taskId)
    }

    @Test
    fun `getMilestoneCountForTask handles repository exception gracefully`() = runTest {
        // Given
        val taskId = 15L
        whenever(repository.getMilestoneCountForTask(taskId))
            .thenThrow(RuntimeException("Query failed"))

        // When
        val result = getTaskMilestoneLinksUseCase.getMilestoneCountForTask(taskId)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Query failed", result.exceptionOrNull()?.message)
        verify(repository).getMilestoneCountForTask(taskId)
    }
}

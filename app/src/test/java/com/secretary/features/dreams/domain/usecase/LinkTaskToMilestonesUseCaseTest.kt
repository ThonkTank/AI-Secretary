package com.secretary.features.dreams.domain.usecase

import com.secretary.features.dreams.domain.model.TaskMilestoneLink
import com.secretary.features.dreams.domain.repository.TaskMilestoneLinkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for LinkTaskToMilestonesUseCase
 * Dream-to-Task Feature - Testing
 *
 * Tests diff-based linking algorithm for task-milestone relationships.
 * Verifies that links are added, removed, and kept correctly based on changes.
 */
class LinkTaskToMilestonesUseCaseTest {

    private lateinit var repository: TaskMilestoneLinkRepository
    private lateinit var linkTaskToMilestonesUseCase: LinkTaskToMilestonesUseCase

    @Before
    fun setUp() {
        repository = mock(TaskMilestoneLinkRepository::class.java)
        linkTaskToMilestonesUseCase = LinkTaskToMilestonesUseCase(repository)
    }

    @Test
    fun `invoke links task to single milestone`() = runTest {
        // Given
        val taskId = 1L
        val milestoneId = 10L
        whenever(repository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = linkTaskToMilestonesUseCase(taskId, listOf(milestoneId))

        // Then
        assertTrue(result.isSuccess)
        verify(repository).getLinksForTask(taskId)
        verify(repository).link(taskId, milestoneId)
        verify(repository, never()).unlink(any(), any())
    }

    @Test
    fun `invoke links task to multiple milestones`() = runTest {
        // Given
        val taskId = 1L
        val milestoneIds = listOf(10L, 20L, 30L)
        whenever(repository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = linkTaskToMilestonesUseCase(taskId, milestoneIds)

        // Then
        assertTrue(result.isSuccess)
        verify(repository).getLinksForTask(taskId)
        verify(repository).link(taskId, 10L)
        verify(repository).link(taskId, 20L)
        verify(repository).link(taskId, 30L)
        verify(repository, never()).unlink(any(), any())
    }

    @Test
    fun `invoke unlinks removed milestones using diff-based algorithm`() = runTest {
        // Given
        val taskId = 1L
        // Old links: M1, M2, M3
        val existingLinks = listOf(
            TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 20L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 30L)
        )
        // New links: M2, M4
        val newMilestoneIds = listOf(20L, 40L)
        whenever(repository.getLinksForTask(taskId)).thenReturn(existingLinks)

        // When
        val result = linkTaskToMilestonesUseCase(taskId, newMilestoneIds)

        // Then
        assertTrue(result.isSuccess)
        // Should remove M1 and M3 (no longer in the list)
        verify(repository).unlink(taskId, 10L)
        verify(repository).unlink(taskId, 30L)
        // Should add M4 (new in the list)
        verify(repository).link(taskId, 40L)
        // Should NOT touch M2 (already exists)
        verify(repository, times(1)).link(eq(taskId), eq(40L)) // Only new link
    }

    @Test
    fun `invoke with invalid task ID fails`() = runTest {
        // Given
        val invalidTaskId = 0L
        val milestoneIds = listOf(10L)

        // When
        val result = linkTaskToMilestonesUseCase(invalidTaskId, milestoneIds)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $invalidTaskId", result.exceptionOrNull()?.message)
        verify(repository, never()).getLinksForTask(any())
        verify(repository, never()).link(any(), any())
    }

    @Test
    fun `invoke with negative task ID fails`() = runTest {
        // Given
        val negativeTaskId = -5L
        val milestoneIds = listOf(10L)

        // When
        val result = linkTaskToMilestonesUseCase(negativeTaskId, milestoneIds)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid task ID: $negativeTaskId", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with invalid milestone ID in list fails`() = runTest {
        // Given
        val taskId = 1L
        val milestoneIdsWithInvalid = listOf(10L, 0L, 20L) // 0 is invalid

        // When
        val result = linkTaskToMilestonesUseCase(taskId, milestoneIdsWithInvalid)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid milestone IDs in list", result.exceptionOrNull()?.message)
        verify(repository, never()).getLinksForTask(any())
        verify(repository, never()).link(any(), any())
    }

    @Test
    fun `invoke allows linking to different dreams`() = runTest {
        // Given
        val taskId = 1L
        // Milestones from different dreams (repository doesn't validate dream boundaries)
        val milestoneIdsFromDifferentDreams = listOf(10L, 20L, 30L) // Could be from Dreams 1, 2, 3
        whenever(repository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = linkTaskToMilestonesUseCase(taskId, milestoneIdsFromDifferentDreams)

        // Then
        assertTrue(result.isSuccess)
        // Should link to all milestones regardless of dream
        verify(repository).link(taskId, 10L)
        verify(repository).link(taskId, 20L)
        verify(repository).link(taskId, 30L)
    }

    @Test
    fun `invoke handles empty milestone list by removing all links`() = runTest {
        // Given
        val taskId = 1L
        val existingLinks = listOf(
            TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 20L)
        )
        val emptyMilestoneIds = emptyList<Long>()
        whenever(repository.getLinksForTask(taskId)).thenReturn(existingLinks)

        // When
        val result = linkTaskToMilestonesUseCase(taskId, emptyMilestoneIds)

        // Then
        assertTrue(result.isSuccess)
        // Should remove all existing links
        verify(repository).unlink(taskId, 10L)
        verify(repository).unlink(taskId, 20L)
        // Should not add any new links
        verify(repository, never()).link(any(), any())
    }

    @Test
    fun `invoke handles repository exception gracefully`() = runTest {
        // Given
        val taskId = 1L
        val milestoneIds = listOf(10L)
        whenever(repository.getLinksForTask(taskId)).thenThrow(RuntimeException("Database error"))

        // When
        val result = linkTaskToMilestonesUseCase(taskId, milestoneIds)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke with duplicate milestone IDs deduplicates automatically`() = runTest {
        // Given
        val taskId = 1L
        val milestoneIdsWithDuplicates = listOf(10L, 20L, 10L, 30L, 20L) // Duplicates: 10, 20
        whenever(repository.getLinksForTask(taskId)).thenReturn(emptyList())

        // When
        val result = linkTaskToMilestonesUseCase(taskId, milestoneIdsWithDuplicates)

        // Then
        assertTrue(result.isSuccess)
        // Set conversion should deduplicate, so each milestone is linked only once
        verify(repository).link(taskId, 10L)
        verify(repository).link(taskId, 20L)
        verify(repository).link(taskId, 30L)
        // Verify no duplicate calls
        verify(repository, times(1)).link(taskId, 10L)
        verify(repository, times(1)).link(taskId, 20L)
        verify(repository, times(1)).link(taskId, 30L)
    }

    @Test
    fun `invoke preserves existing links that remain in new list`() = runTest {
        // Given
        val taskId = 1L
        // Old links: M1, M2, M3
        val existingLinks = listOf(
            TaskMilestoneLink(taskId = taskId, milestoneId = 10L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 20L),
            TaskMilestoneLink(taskId = taskId, milestoneId = 30L)
        )
        // New links: M2, M3 (M1 removed, M2 and M3 kept)
        val newMilestoneIds = listOf(20L, 30L)
        whenever(repository.getLinksForTask(taskId)).thenReturn(existingLinks)

        // When
        val result = linkTaskToMilestonesUseCase(taskId, newMilestoneIds)

        // Then
        assertTrue(result.isSuccess)
        // Should only remove M1
        verify(repository).unlink(taskId, 10L)
        // Should NOT touch M2 or M3 (they remain)
        verify(repository, never()).link(taskId, 20L)
        verify(repository, never()).link(taskId, 30L)
        verify(repository, never()).unlink(taskId, 20L)
        verify(repository, never()).unlink(taskId, 30L)
    }
}

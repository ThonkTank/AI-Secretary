package com.secretary.features.dreams.domain.repository

import com.secretary.features.dreams.domain.model.Milestone
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Milestone operations.
 * Dream-to-Task Feature - Domain Layer
 *
 * Defines contract for milestone data access without exposing implementation details.
 * All methods are suspend functions for coroutine support.
 */
interface MilestoneRepository {

    /**
     * Insert a new milestone into the database.
     * @param milestone The milestone to insert
     * @return The ID of the inserted milestone
     */
    suspend fun insert(milestone: Milestone): Long

    /**
     * Update an existing milestone in the database.
     * @param milestone The milestone with updated values
     */
    suspend fun update(milestone: Milestone)

    /**
     * Delete a milestone by its ID.
     * This will cascade delete all associated task links and interdependencies.
     * @param milestoneId The ID of the milestone to delete
     */
    suspend fun delete(milestoneId: Long)

    /**
     * Get a milestone by its ID.
     * @param milestoneId The ID of the milestone to retrieve
     * @return The milestone if found, null otherwise
     */
    suspend fun getById(milestoneId: Long): Milestone?

    /**
     * Get all milestones for a specific dream, ordered by order index.
     * @param dreamId The ID of the dream
     * @return List of milestones for the dream
     */
    suspend fun getByDreamId(dreamId: Long): List<Milestone>

    /**
     * Add XP to a milestone's current XP.
     * @param milestoneId The ID of the milestone
     * @param xpToAdd The amount of XP to add
     */
    suspend fun updateXP(milestoneId: Long, xpToAdd: Long)

    /**
     * Mark a milestone as completed.
     * Updates status to COMPLETED and sets completedAt timestamp.
     * @param milestoneId The ID of the milestone to complete
     * @param completedAt The timestamp when completed (defaults to current time)
     */
    suspend fun complete(milestoneId: Long, completedAt: Long = System.currentTimeMillis())

    /**
     * Observe milestones for a specific dream for reactive UI updates.
     * @param dreamId The ID of the dream
     * @return Flow that emits list of milestones whenever data changes
     */
    fun observeByDreamId(dreamId: Long): Flow<List<Milestone>>
}

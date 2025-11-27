package com.secretary.features.dreams.domain.repository

import com.secretary.features.dreams.domain.model.Dream
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Dream operations.
 * Dream-to-Task Feature - Domain Layer
 *
 * Defines contract for dream data access without exposing implementation details.
 * All methods are suspend functions for coroutine support.
 */
interface DreamRepository {

    /**
     * Insert a new dream into the database.
     * @param dream The dream to insert
     * @return The ID of the inserted dream
     */
    suspend fun insert(dream: Dream): Long

    /**
     * Update an existing dream in the database.
     * @param dream The dream with updated values
     */
    suspend fun update(dream: Dream)

    /**
     * Delete a dream by its ID.
     * This will cascade delete all associated milestones.
     * @param dreamId The ID of the dream to delete
     */
    suspend fun delete(dreamId: Long)

    /**
     * Get a dream by its ID.
     * @param dreamId The ID of the dream to retrieve
     * @return The dream if found, null otherwise
     */
    suspend fun getById(dreamId: Long): Dream?

    /**
     * Get all dreams, ordered by creation date (newest first).
     * @return List of all dreams
     */
    suspend fun getAll(): List<Dream>

    /**
     * Get all active (not archived) dreams.
     * @return List of active dreams
     */
    suspend fun getAllActive(): List<Dream>

    /**
     * Observe all dreams for reactive UI updates.
     * @return Flow that emits list of all dreams whenever data changes
     */
    fun observeAll(): Flow<List<Dream>>
}

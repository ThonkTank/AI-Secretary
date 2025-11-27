package com.secretary.features.dreams.domain.repository

import com.secretary.features.dreams.domain.model.MilestoneInterdependency

/**
 * Repository interface for MilestoneInterdependency operations.
 * Dream-to-Task Feature - Domain Layer
 *
 * Manages relationships between milestones with impact ratings.
 * Tracks how milestones influence each other positively or negatively.
 */
interface InterdependencyRepository {

    /**
     * Insert or update (upsert) an interdependency.
     * If an interdependency already exists for the source-target pair, it will be updated.
     * @param interdependency The interdependency to upsert
     */
    suspend fun upsert(interdependency: MilestoneInterdependency)

    /**
     * Delete an interdependency between two milestones.
     * @param sourceId The ID of the source milestone
     * @param targetId The ID of the target milestone
     */
    suspend fun delete(sourceId: Long, targetId: Long)

    /**
     * Get all interdependencies for a specific milestone (both source and target).
     * @param milestoneId The ID of the milestone
     * @return List of interdependencies involving the milestone
     */
    suspend fun getForMilestone(milestoneId: Long): List<MilestoneInterdependency>

    /**
     * Get all interdependencies in the system.
     * Used for analyzing milestone relationship networks.
     * @return List of all interdependencies
     */
    suspend fun getAll(): List<MilestoneInterdependency>
}

package com.secretary.features.dreams.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.secretary.features.dreams.data.entity.MilestoneInterdependencyEntity

/**
 * Room DAO for milestone_interdependencies table.
 * Dream-to-Task Feature - Data Layer
 *
 * Provides type-safe database queries for milestone interdependency operations.
 */
@Dao
interface MilestoneInterdependencyDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new interdependency
     */
    @Insert
    fun insertInterdependency(interdependency: MilestoneInterdependencyEntity)

    /**
     * Update an existing interdependency
     */
    @Update
    fun updateInterdependency(interdependency: MilestoneInterdependencyEntity)

    /**
     * Delete an interdependency
     */
    @Delete
    fun deleteInterdependency(interdependency: MilestoneInterdependencyEntity)

    /**
     * Insert or update an interdependency (upsert)
     */
    @Upsert
    fun upsertInterdependency(interdependency: MilestoneInterdependencyEntity)

    // ========== Query Operations ==========

    /**
     * Get all interdependencies where the milestone is the source
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE source_milestone_id = :milestoneId ORDER BY impact_rating DESC")
    fun getOutgoingInterdependencies(milestoneId: Long): List<MilestoneInterdependencyEntity>

    /**
     * Get all interdependencies where the milestone is the target
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE target_milestone_id = :milestoneId ORDER BY impact_rating DESC")
    fun getIncomingInterdependencies(milestoneId: Long): List<MilestoneInterdependencyEntity>

    /**
     * Get all interdependencies for a milestone (both source and target)
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE source_milestone_id = :milestoneId OR target_milestone_id = :milestoneId ORDER BY impact_rating DESC")
    fun getInterdependenciesForMilestone(milestoneId: Long): List<MilestoneInterdependencyEntity>

    /**
     * Get a specific interdependency between two milestones
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE source_milestone_id = :sourceId AND target_milestone_id = :targetId")
    fun getInterdependency(sourceId: Long, targetId: Long): MilestoneInterdependencyEntity?

    /**
     * Delete a specific interdependency by IDs
     */
    @Query("DELETE FROM milestone_interdependencies WHERE source_milestone_id = :sourceId AND target_milestone_id = :targetId")
    fun deleteInterdependencyByIds(sourceId: Long, targetId: Long)

    /**
     * Get interdependencies with positive impact (rating > 0)
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE source_milestone_id = :milestoneId AND impact_rating > 0 ORDER BY impact_rating DESC")
    fun getPositiveImpacts(milestoneId: Long): List<MilestoneInterdependencyEntity>

    /**
     * Get interdependencies with negative impact (rating < 0)
     */
    @Query("SELECT * FROM milestone_interdependencies WHERE source_milestone_id = :milestoneId AND impact_rating < 0 ORDER BY impact_rating ASC")
    fun getNegativeImpacts(milestoneId: Long): List<MilestoneInterdependencyEntity>

    /**
     * Get count of interdependencies for a milestone
     */
    @Query("SELECT COUNT(*) FROM milestone_interdependencies WHERE source_milestone_id = :milestoneId OR target_milestone_id = :milestoneId")
    fun getInterdependencyCount(milestoneId: Long): Int
}

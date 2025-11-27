package com.secretary.features.dreams.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secretary.features.dreams.data.entity.MilestoneEntity

/**
 * Room DAO for milestones table.
 * Dream-to-Task Feature - Data Layer
 *
 * Provides type-safe database queries for milestone operations.
 */
@Dao
interface MilestoneDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new milestone
     * @return The ID of the inserted milestone
     */
    @Insert
    fun insertMilestone(milestone: MilestoneEntity): Long

    /**
     * Update an existing milestone
     */
    @Update
    fun updateMilestone(milestone: MilestoneEntity)

    /**
     * Delete a milestone
     */
    @Delete
    fun deleteMilestone(milestone: MilestoneEntity)

    // ========== Query Operations ==========

    /**
     * Get all milestones for a specific dream, ordered by order_index
     */
    @Query("SELECT * FROM milestones WHERE dream_id = :dreamId ORDER BY order_index ASC")
    fun getMilestonesByDream(dreamId: Long): List<MilestoneEntity>

    /**
     * Get a single milestone by ID
     */
    @Query("SELECT * FROM milestones WHERE milestone_id = :milestoneId")
    fun getMilestoneById(milestoneId: Long): MilestoneEntity?

    /**
     * Get completed milestones for a specific dream
     */
    @Query("SELECT * FROM milestones WHERE dream_id = :dreamId AND status = 1 ORDER BY completed_at DESC")
    fun getCompletedMilestones(dreamId: Long): List<MilestoneEntity>

    /**
     * Get active milestones for a specific dream
     */
    @Query("SELECT * FROM milestones WHERE dream_id = :dreamId AND status = 0 ORDER BY order_index ASC")
    fun getActiveMilestones(dreamId: Long): List<MilestoneEntity>

    // ========== Statistics Queries ==========

    /**
     * Get total count of milestones for a dream
     */
    @Query("SELECT COUNT(*) FROM milestones WHERE dream_id = :dreamId")
    fun getMilestoneCount(dreamId: Long): Int

    /**
     * Get count of completed milestones for a dream
     */
    @Query("SELECT COUNT(*) FROM milestones WHERE dream_id = :dreamId AND status = 1")
    fun getCompletedMilestoneCount(dreamId: Long): Int

    /**
     * Get total XP earned across all milestones for a dream
     */
    @Query("SELECT SUM(current_xp) FROM milestones WHERE dream_id = :dreamId")
    fun getTotalXpForDream(dreamId: Long): Long?

    /**
     * Get next milestone that needs completion (lowest order_index, active)
     */
    @Query("SELECT * FROM milestones WHERE dream_id = :dreamId AND status = 0 ORDER BY order_index ASC LIMIT 1")
    fun getNextMilestone(dreamId: Long): MilestoneEntity?
}

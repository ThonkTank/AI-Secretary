package com.secretary.features.dreams.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secretary.features.dreams.data.entity.DreamEntity

/**
 * Room DAO for dreams table.
 * Dream-to-Task Feature - Data Layer
 *
 * Provides type-safe database queries for dream operations.
 */
@Dao
interface DreamDao {

    // ========== CRUD Operations ==========

    /**
     * Insert a new dream
     * @return The ID of the inserted dream
     */
    @Insert
    fun insertDream(dream: DreamEntity): Long

    /**
     * Update an existing dream
     */
    @Update
    fun updateDream(dream: DreamEntity)

    /**
     * Delete a dream
     */
    @Delete
    fun deleteDream(dream: DreamEntity)

    // ========== Query Operations ==========

    /**
     * Get all dreams, ordered by creation date (newest first)
     */
    @Query("SELECT * FROM dreams ORDER BY created_at DESC")
    fun getAllDreams(): List<DreamEntity>

    /**
     * Get active (not archived) dreams
     */
    @Query("SELECT * FROM dreams WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getActiveDreams(): List<DreamEntity>

    /**
     * Get archived dreams
     */
    @Query("SELECT * FROM dreams WHERE is_archived = 1 ORDER BY created_at DESC")
    fun getArchivedDreams(): List<DreamEntity>

    /**
     * Get a single dream by ID
     */
    @Query("SELECT * FROM dreams WHERE dream_id = :dreamId")
    fun getDreamById(dreamId: Long): DreamEntity?

    // ========== Statistics Queries ==========

    /**
     * Get total count of all dreams
     */
    @Query("SELECT COUNT(*) FROM dreams")
    fun getDreamCount(): Int

    /**
     * Get count of active dreams
     */
    @Query("SELECT COUNT(*) FROM dreams WHERE is_archived = 0")
    fun getActiveDreamCount(): Int

    /**
     * Get dream with highest level
     */
    @Query("SELECT * FROM dreams ORDER BY current_level DESC, total_xp DESC LIMIT 1")
    fun getDreamWithHighestLevel(): DreamEntity?

    /**
     * Get total XP across all dreams
     */
    @Query("SELECT SUM(total_xp) FROM dreams WHERE is_archived = 0")
    fun getTotalXpAcrossAllDreams(): Long?
}

package com.secretary.features.dreams.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretary.features.dreams.data.entity.DreamXpHistoryEntity
import com.secretary.features.dreams.data.entity.DreamDailySnapshotEntity

/**
 * DAO for Dream Analytics data operations.
 * Dream Analytics Feature - Data Layer
 *
 * Provides queries for XP history tracking and daily snapshots.
 */
@Dao
interface DreamAnalyticsDao {

    // ========== XP History ==========

    /**
     * Insert a new XP history entry
     * @return The ID of the inserted entry
     */
    @Insert
    suspend fun insertXpHistory(entry: DreamXpHistoryEntity): Long

    /**
     * Get all XP history for a dream, ordered by newest first
     */
    @Query("SELECT * FROM dream_xp_history WHERE dream_id = :dreamId ORDER BY created_at DESC")
    suspend fun getXpHistoryForDream(dreamId: Long): List<DreamXpHistoryEntity>

    /**
     * Get XP history since a specific timestamp
     */
    @Query("SELECT * FROM dream_xp_history WHERE dream_id = :dreamId AND created_at >= :sinceTimestamp ORDER BY created_at ASC")
    suspend fun getXpHistorySince(dreamId: Long, sinceTimestamp: Long): List<DreamXpHistoryEntity>

    /**
     * Get total XP gained since a specific timestamp
     */
    @Query("SELECT SUM(xp_change) FROM dream_xp_history WHERE dream_id = :dreamId AND created_at >= :sinceTimestamp")
    suspend fun getXpGainedSince(dreamId: Long, sinceTimestamp: Long): Long?

    // ========== Daily Snapshots ==========

    /**
     * Insert or update a daily snapshot
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSnapshot(snapshot: DreamDailySnapshotEntity)

    /**
     * Get all snapshots for a dream, ordered by newest first
     */
    @Query("SELECT * FROM dream_daily_snapshots WHERE dream_id = :dreamId ORDER BY date_day DESC")
    suspend fun getSnapshotsForDream(dreamId: Long): List<DreamDailySnapshotEntity>

    /**
     * Get snapshots since a specific date
     */
    @Query("SELECT * FROM dream_daily_snapshots WHERE dream_id = :dreamId AND date_day >= :sinceTimestamp ORDER BY date_day ASC")
    suspend fun getSnapshotsSince(dreamId: Long, sinceTimestamp: Long): List<DreamDailySnapshotEntity>

    /**
     * Get most recent N snapshots for a dream
     */
    @Query("SELECT * FROM dream_daily_snapshots WHERE dream_id = :dreamId ORDER BY date_day DESC LIMIT :limit")
    suspend fun getRecentSnapshots(dreamId: Long, limit: Int): List<DreamDailySnapshotEntity>
}

package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entity for daily XP snapshots for chart visualization.
 * Aggregates daily progress for performance.
 *
 * Dream Analytics Feature - Data Layer
 */
@Entity(
    tableName = "dream_daily_snapshots",
    primaryKeys = ["dream_id", "date_day"],
    foreignKeys = [
        ForeignKey(
            entity = DreamEntity::class,
            parentColumns = ["dream_id"],
            childColumns = ["dream_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("dream_id")]
)
data class DreamDailySnapshotEntity(
    @ColumnInfo(name = "dream_id")
    val dreamId: Long,

    @ColumnInfo(name = "date_day")
    val dateDay: Long,  // Timestamp truncated to start of day

    @ColumnInfo(name = "total_xp")
    val totalXp: Long,

    @ColumnInfo(name = "milestones_completed")
    val milestonesCompleted: Int
)

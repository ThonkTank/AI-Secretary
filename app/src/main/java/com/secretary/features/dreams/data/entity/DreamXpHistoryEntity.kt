package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity for tracking XP changes over time for analytics.
 * Stores individual XP transactions.
 *
 * Dream Analytics Feature - Data Layer
 */
@Entity(
    tableName = "dream_xp_history",
    foreignKeys = [
        ForeignKey(
            entity = DreamEntity::class,
            parentColumns = ["dream_id"],
            childColumns = ["dream_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DreamXpHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "dream_id", index = true)
    val dreamId: Long,

    @ColumnInfo(name = "xp_change")
    val xpChange: Long,

    @ColumnInfo(name = "source_type")
    val sourceType: String,  // "TASK", "MILESTONE_COMPLETION", "BONUS"

    @ColumnInfo(name = "source_id")
    val sourceId: Long? = null,  // Task ID or Milestone ID

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

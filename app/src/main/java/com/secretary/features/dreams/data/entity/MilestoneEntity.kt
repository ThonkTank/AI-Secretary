package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for milestones table.
 * Dream-to-Task Feature - Data Layer
 *
 * Represents a specific milestone within a dream's progression.
 * Milestones track XP progress and can be linked to tasks.
 */
@Entity(
    tableName = "milestones",
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
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "milestone_id")
    val milestoneId: Long = 0,

    @ColumnInfo(name = "dream_id")
    val dreamId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "target_xp")
    val targetXp: Long = 1000,

    @ColumnInfo(name = "current_xp")
    val currentXp: Long = 0,

    @ColumnInfo(name = "status")
    val status: Int = MilestoneStatus.ACTIVE, // 0=Active, 1=Completed

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
) {
    companion object {
        object MilestoneStatus {
            const val ACTIVE = 0
            const val COMPLETED = 1
        }
    }
}

package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room Entity for milestone_interdependencies table.
 * Dream-to-Task Feature - Data Layer
 *
 * Tracks relationships between milestones within and across dreams.
 * Impact rating indicates strength and direction of influence (-5 to +5).
 */
@Entity(
    tableName = "milestone_interdependencies",
    primaryKeys = ["source_milestone_id", "target_milestone_id"],
    foreignKeys = [
        ForeignKey(
            entity = MilestoneEntity::class,
            parentColumns = ["milestone_id"],
            childColumns = ["source_milestone_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MilestoneEntity::class,
            parentColumns = ["milestone_id"],
            childColumns = ["target_milestone_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("source_milestone_id"), Index("target_milestone_id")]
)
data class MilestoneInterdependencyEntity(
    @ColumnInfo(name = "source_milestone_id")
    val sourceMilestoneId: Long,

    @ColumnInfo(name = "target_milestone_id")
    val targetMilestoneId: Long,

    @ColumnInfo(name = "impact_rating")
    val impactRating: Int, // -5 (negative impact) to +5 (positive impact)

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

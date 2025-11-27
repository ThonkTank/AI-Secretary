package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.secretary.features.tasks.data.TaskEntity

/**
 * Room Entity for task_milestone_junction table.
 * Dream-to-Task Feature - Data Layer
 *
 * Many-to-many relationship between tasks and milestones.
 * Allows tasks to contribute XP to multiple milestones.
 */
@Entity(
    tableName = "task_milestone_junction",
    primaryKeys = ["task_id", "milestone_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MilestoneEntity::class,
            parentColumns = ["milestone_id"],
            childColumns = ["milestone_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("task_id"), Index("milestone_id")]
)
data class TaskMilestoneJunction(
    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @ColumnInfo(name = "milestone_id")
    val milestoneId: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

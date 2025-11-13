package com.secretary.helloworld.features.statistics.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secretary.helloworld.features.tasks.data.TaskEntity

/**
 * Room Entity for completions table.
 * Phase 4.5.3: Kotlin Migration - Converted to data class
 *
 * Stores historical completion records for task analytics and statistics.
 * Maps to existing SQLite schema (6 columns).
 *
 * Note: Will be fully integrated with Room in Phase 4.5.4
 */
@Entity(
    tableName = "completions",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")] // Index for foreign key performance
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "completion_id")
    val completionId: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long,

    @ColumnInfo(name = "time_spent_minutes")
    val timeSpentMinutes: Int = 0,

    @ColumnInfo(name = "difficulty")
    val difficulty: Int = 0, // 0-10 scale

    @ColumnInfo(name = "notes")
    val notes: String? = null
)

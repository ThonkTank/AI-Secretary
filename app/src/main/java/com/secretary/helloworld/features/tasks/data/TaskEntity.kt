package com.secretary.helloworld.features.tasks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for tasks table.
 * Phase 4.5.3 Wave 5: Converted to Kotlin
 *
 * Maps to existing SQLite schema (17 columns).
 * Migrates from TaskDatabaseHelper v4 to Room v5.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @ColumnInfo(name = "title")
    var title: String = "",

    @ColumnInfo(name = "description")
    var description: String? = null,

    @ColumnInfo(name = "category")
    var category: String = "General",

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "due_date")
    var dueDate: Long = 0,

    @ColumnInfo(name = "is_completed")
    var isCompleted: Int = 0, // SQLite stores boolean as 0/1

    @ColumnInfo(name = "priority")
    var priority: Int = 1, // 0=Low, 1=Medium, 2=High, 3=Urgent

    // Recurrence fields
    @ColumnInfo(name = "recurrence_type")
    var recurrenceType: Int = 0, // 0=None, 1=Interval, 2=Frequency

    @ColumnInfo(name = "recurrence_amount")
    var recurrenceAmount: Int = 0,

    @ColumnInfo(name = "recurrence_unit")
    var recurrenceUnit: Int = 0, // 0=Day, 1=Week, 2=Month, 3=Year

    @ColumnInfo(name = "last_completed_date")
    var lastCompletedDate: Long = 0,

    @ColumnInfo(name = "completions_this_period")
    var completionsThisPeriod: Int = 0,

    @ColumnInfo(name = "current_period_start")
    var currentPeriodStart: Long = 0,

    // Streak tracking fields
    @ColumnInfo(name = "current_streak")
    var currentStreak: Int = 0,

    @ColumnInfo(name = "longest_streak")
    var longestStreak: Int = 0,

    @ColumnInfo(name = "last_streak_date")
    var lastStreakDate: Long = 0
)

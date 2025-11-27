package com.secretary.features.dreams.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for dreams table.
 * Dream-to-Task Feature - Data Layer
 *
 * Represents a long-term goal or aspiration that can be broken down into milestones.
 * Dreams track XP earned from completed tasks and level progression.
 */
@Entity(tableName = "dreams")
data class DreamEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "dream_id")
    val dreamId: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "icon_name")
    val iconName: String? = null,

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6200EE.toInt(), // Material Design primary color

    @ColumnInfo(name = "total_xp")
    val totalXp: Long = 0,

    @ColumnInfo(name = "current_level")
    val currentLevel: Int = 1,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false
)

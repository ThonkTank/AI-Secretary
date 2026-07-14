package com.autosecretary.features.task.domain.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Room {@code @Entity} for the {@code task_category_window} table. A per-weekday time block that
 * reserves part of the day's scheduling window for one {@link TaskCategory}. During scheduling,
 * a category window is filled first with tasks of {@code categoryId}; any leftover reserved time
 * is filled with other tasks (two-pass scheduling).
 *
 * <p>{@code categoryId} is a plain string (no {@code @ForeignKey}, mirroring
 * {@code TaskCore.categoryId}); windows whose category no longer exists are ignored by the
 * provider rather than enforced by the DB.
 */
@Entity(tableName = "task_category_window")
public class TaskCategoryWindow {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public DayOfWeek dayOfWeek;

    @NonNull
    public String categoryId;

    @NonNull
    public LocalTime startTime;

    @NonNull
    public LocalTime endTime;

    public TaskCategoryWindow() {}

    @Ignore
    public TaskCategoryWindow(@NonNull DayOfWeek dayOfWeek, @NonNull String categoryId,
                             @NonNull LocalTime startTime, @NonNull LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.categoryId = categoryId;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}

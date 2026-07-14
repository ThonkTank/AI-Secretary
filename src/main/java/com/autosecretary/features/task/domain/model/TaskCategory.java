package com.autosecretary.features.task.domain.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Room {@code @Entity} for the {@code task_category} table. A flat grouping of tasks
 * (replacing the former parent-child task hierarchy). Each {@link TaskCore} references at
 * most one category via {@code TaskCore.categoryId}; that column is a plain nullable string
 * (no {@code @ForeignKey}) — ON DELETE SET NULL behaviour is enforced in application code
 * ({@code TaskDataService.deleteCategory}) to avoid Room FK/index schema-match pitfalls.
 */
@Entity(tableName = "task_category")
public class TaskCategory {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    @NonNull
    public String icon = TaskCore.DEFAULT_GOAL_ICON;

    @NonNull
    public String colorHex = TaskCore.DEFAULT_GOAL_COLOR_HEX;

    /** Display order for grouping in the manage list and widget chips (ascending). */
    public int sortOrder = 0;

    public TaskCategory() {}

    @Ignore
    public TaskCategory(@NonNull String name, @NonNull String icon, @NonNull String colorHex) {
        this.name = name;
        this.icon = icon;
        this.colorHex = colorHex;
    }
}

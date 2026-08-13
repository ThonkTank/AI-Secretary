package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "planning_conflicts",
        foreignKeys = @ForeignKey(
                entity = WorkItemEntity.class,
                parentColumns = "id",
                childColumns = "workItemId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("workItemId"))
public final class PlanningConflictEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String workItemId = "";
    @NonNull public String occurrenceKey = "";
    @NonNull public String reason = "NO_CAPACITY";
    @NonNull public String detail = "";
    @NonNull public String computedAt = "";
}

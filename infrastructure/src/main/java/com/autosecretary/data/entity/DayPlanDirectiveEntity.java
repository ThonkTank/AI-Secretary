package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "day_plan_directives",
        foreignKeys = @ForeignKey(
                entity = WorkItemEntity.class,
                parentColumns = "id",
                childColumns = "workItemId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"day", "workItemId"}, unique = true), @Index("workItemId")})
public final class DayPlanDirectiveEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String day = "";
    @NonNull public String workItemId = "";
    @NonNull public String relation = "LAST";
    public String anchorWorkItemId;
    @NonNull public String updatedAt = "";
}

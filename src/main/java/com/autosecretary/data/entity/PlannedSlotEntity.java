package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "planned_slots",
        foreignKeys = @ForeignKey(
                entity = WorkItemEntity.class,
                parentColumns = "id",
                childColumns = "workItemId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("workItemId"), @Index("day")})
public final class PlannedSlotEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String workItemId = "";
    @NonNull public String occurrenceKey = "";
    @NonNull public String day = "";
    @NonNull public String startAt = "";
    @NonNull public String endAt = "";
    @NonNull public String computedAt = "";
}

package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "work_item_completions",
        foreignKeys = @ForeignKey(
                entity = WorkItemEntity.class,
                parentColumns = "id",
                childColumns = "workItemId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("workItemId"), @Index("completedAt")})
public final class CompletionEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String workItemId = "";
    @NonNull public String occurrenceKey = "";
    @NonNull public String completedAt = "";
}

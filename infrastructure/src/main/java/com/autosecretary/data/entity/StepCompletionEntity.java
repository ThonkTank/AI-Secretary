package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "step_completions",
        foreignKeys = @ForeignKey(
                entity = StepEntity.class,
                parentColumns = "id",
                childColumns = "stepId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("stepId"), @Index(value = {"stepId", "occurrenceKey"}, unique = true)})
public final class StepCompletionEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String stepId = "";
    @NonNull public String occurrenceKey = "";
    @NonNull public String completedAt = "";
}

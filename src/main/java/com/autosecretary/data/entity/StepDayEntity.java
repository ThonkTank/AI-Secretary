package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "step_days",
        primaryKeys = {"stepId", "dayOfWeek"},
        foreignKeys = @ForeignKey(
                entity = StepEntity.class,
                parentColumns = "id",
                childColumns = "stepId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("stepId"))
public final class StepDayEntity {
    @NonNull public String stepId = "";
    @NonNull public String dayOfWeek = "";
}

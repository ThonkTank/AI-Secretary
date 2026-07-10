package com.autosecretary.features.task.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import java.time.LocalDate;

@Entity(
        tableName = "task_planned_meals",
        primaryKeys = {"taskId", "day"},
        indices = @Index("taskId"),
        foreignKeys = @ForeignKey(
                entity = TaskCore.class,
                parentColumns = "id",
                childColumns = "taskId",
                onDelete = ForeignKey.CASCADE
        )
)
public class TaskPlannedMeal {
    @NonNull
    public String taskId;
    @NonNull
    public LocalDate day;
    public String recipeId;
    public int plannedServings;
    public boolean completed;
    public int actualServings;

    public void markCompleted(int actualServingsOverride) {
        this.completed = true;
        this.actualServings = actualServingsOverride > 0 ? actualServingsOverride : plannedServings;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_steps", foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id", childColumns = "taskId", onDelete = ForeignKey.CASCADE), indices = @Index("taskId"))
public class TaskStepEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    public int position;
    @NonNull public String text;
    public int weekdayMask;
    public int intervalDays;
    @NonNull public String amountKind;
    public Integer plannedSets;
    public Integer plannedReps;
    public Integer plannedDurationSeconds;
    @NonNull public String note;
    @Ignore public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text) {
        this(id, taskId, position, text, 0, 0, "NONE", null, null, null, "");
    }
    @Ignore public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, @NonNull String amountKind,
                          Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String note) {
        this(id, taskId, position, text, weekdayMask, 0, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds, note);
    }
    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, int intervalDays,
                          @NonNull String amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String note) {
        this.id = id; this.taskId = taskId; this.position = position; this.text = text;
        this.weekdayMask = weekdayMask; this.intervalDays = intervalDays;
        this.amountKind = amountKind;
        this.plannedSets = plannedSets; this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds; this.note = note;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
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
    @NonNull public String restTimerMode;
    public Integer restTimerSeconds;
    public boolean assistantEnabled;
    public int assistantMinSets;
    public int assistantMaxSets;
    public int assistantMinReps;
    public int assistantMaxReps;
    public int assistantTargetRir;
    public long assistantLoadIncrementMilli;
    public int assistantWeeklySetCeiling;
    @NonNull public String plannedLoadMode;
    @NonNull public String plannedLoadUnit;
    public Long plannedLoadMilli;
    public String primaryMuscle;
    @NonNull public String secondaryMuscles;
    @NonNull public String assistantStatus;
    public int assistantObservations;
    public int assistantReadyStreak;
    public int assistantHardStreak;
    @NonNull public String note;
    @ColumnInfo(defaultValue = "'SCHEDULED'") @NonNull public String activationKind;
    @Ignore public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text) {
        this(id, taskId, position, text, 0, 0, "NONE", null, null, null,
                "OFF", null, "");
    }
    @Ignore public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, @NonNull String amountKind,
                          Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String note) {
        this(id, taskId, position, text, weekdayMask, 0, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds,
                "SETS_REPS".equals(amountKind) ? "INHERIT" : "OFF", null, note);
    }
    @Ignore
    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, int intervalDays,
                          @NonNull String amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String note) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds,
                "SETS_REPS".equals(amountKind) ? "INHERIT" : "OFF", null, note);
    }
    @Ignore
    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, int intervalDays,
                          @NonNull String amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String restTimerMode,
                          Integer restTimerSeconds, @NonNull String note) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds, restTimerMode, restTimerSeconds, note,
                "SCHEDULED");
    }

    @Ignore
    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, int intervalDays,
                          @NonNull String amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String note,
                          @NonNull String activationKind) {
        this(id, taskId, position, text, weekdayMask, intervalDays, amountKind, plannedSets,
                plannedReps, plannedDurationSeconds,
                "SETS_REPS".equals(amountKind) ? "INHERIT" : "OFF", null, note,
                activationKind);
    }

    public TaskStepEntity(@NonNull String id, @NonNull String taskId, int position,
                          @NonNull String text, int weekdayMask, int intervalDays,
                          @NonNull String amountKind, Integer plannedSets, Integer plannedReps,
                          Integer plannedDurationSeconds, @NonNull String restTimerMode,
                          Integer restTimerSeconds, @NonNull String note,
                          @NonNull String activationKind) {
        this.id = id; this.taskId = taskId; this.position = position; this.text = text;
        this.weekdayMask = weekdayMask; this.intervalDays = intervalDays;
        this.amountKind = amountKind;
        this.plannedSets = plannedSets; this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.restTimerMode = restTimerMode; this.restTimerSeconds = restTimerSeconds;
        this.assistantEnabled = false; this.assistantMinSets = 2; this.assistantMaxSets = 3;
        this.assistantMinReps = 8; this.assistantMaxReps = 12; this.assistantTargetRir = 2;
        this.assistantLoadIncrementMilli = 2500; this.assistantWeeklySetCeiling = 10;
        this.plannedLoadMode = "UNSPECIFIED"; this.plannedLoadUnit = "NONE";
        this.secondaryMuscles = ""; this.assistantStatus = "DISABLED";
        this.note = note;
        this.activationKind = activationKind;
    }
}

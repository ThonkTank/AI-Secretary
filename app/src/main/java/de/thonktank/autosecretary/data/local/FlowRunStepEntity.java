package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "flow_run_steps",
        foreignKeys = @ForeignKey(entity = StepFlowRunEntity.class, parentColumns = "id",
                childColumns = "runId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("runId"), @Index(value = {"runId", "position"}, unique = true),
                @Index("sourceTemplateId")})
public class FlowRunStepEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String runId;
    public int position;
    @NonNull public String sourceTemplateId;
    @NonNull public String text;
    @NonNull public String amountKind;
    @Nullable public Integer plannedSets;
    @Nullable public Integer plannedReps;
    @Nullable public Integer plannedDurationSeconds;
    @NonNull public String restTimerMode;
    @Nullable public Integer restTimerSeconds;
    @NonNull public String plannedLoadMode;
    @NonNull public String plannedLoadUnit;
    @Nullable public Long plannedLoadMilli;
    public int targetRir;
    @NonNull public String note;
    @Nullable public String delayMode;
    @Nullable public Long defaultDelayMillis;
    @Nullable public Long lastUsedDelayMillis;
    @Nullable public Long chosenDelayMillis;

    public FlowRunStepEntity(@NonNull String id, @NonNull String runId, int position,
                             @NonNull String sourceTemplateId, @NonNull String text,
                             @NonNull String amountKind, @Nullable Integer plannedSets,
                             @Nullable Integer plannedReps,
                             @Nullable Integer plannedDurationSeconds,
                             @NonNull String restTimerMode,
                             @Nullable Integer restTimerSeconds,
                             @NonNull String plannedLoadMode, @NonNull String plannedLoadUnit,
                             @Nullable Long plannedLoadMilli, int targetRir,
                             @NonNull String note,
                             @Nullable String delayMode, @Nullable Long defaultDelayMillis,
                             @Nullable Long lastUsedDelayMillis,
                             @Nullable Long chosenDelayMillis) {
        this.id = id;
        this.runId = runId;
        this.position = position;
        this.sourceTemplateId = sourceTemplateId;
        this.text = text;
        this.amountKind = amountKind;
        this.plannedSets = plannedSets;
        this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds;
        this.restTimerMode = restTimerMode;
        this.restTimerSeconds = restTimerSeconds;
        this.plannedLoadMode = plannedLoadMode;
        this.plannedLoadUnit = plannedLoadUnit;
        this.plannedLoadMilli = plannedLoadMilli;
        this.targetRir = targetRir;
        this.note = note;
        this.delayMode = delayMode;
        this.defaultDelayMillis = defaultDelayMillis;
        this.lastUsedDelayMillis = lastUsedDelayMillis;
        this.chosenDelayMillis = chosenDelayMillis;
    }
}

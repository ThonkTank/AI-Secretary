package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "step_flow_runs",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index("seedStepId"),
                @Index(value = "sourceKey", unique = true),
                @Index(value = {"state", "readyAtEpochMillis"}),
                @Index(value = {"state", "queueOrder", "createdAtEpochMillis"})})
public class StepFlowRunEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String seedStepId;
    @NonNull public String sourceKey;
    @NonNull public String scheduledOn;
    @NonNull public String slot;
    @NonNull public String state;
    public int currentPosition;
    @Nullable public Long readyAtEpochMillis;
    @Nullable public String currentSheetOccurrenceId;
    public long queueOrder;
    public int nextSheetSequence;
    public long createdAtEpochMillis;
    public long updatedAtEpochMillis;

    public StepFlowRunEntity(@NonNull String id, @NonNull String taskId,
                             @NonNull String seedStepId, @NonNull String sourceKey,
                             @NonNull String scheduledOn, @NonNull String slot,
                             @NonNull String state, int currentPosition,
                             @Nullable Long readyAtEpochMillis,
                             @Nullable String currentSheetOccurrenceId, long queueOrder,
                             int nextSheetSequence, long createdAtEpochMillis,
                             long updatedAtEpochMillis) {
        this.id = id;
        this.taskId = taskId;
        this.seedStepId = seedStepId;
        this.sourceKey = sourceKey;
        this.scheduledOn = scheduledOn;
        this.slot = slot;
        this.state = state;
        this.currentPosition = currentPosition;
        this.readyAtEpochMillis = readyAtEpochMillis;
        this.currentSheetOccurrenceId = currentSheetOccurrenceId;
        this.queueOrder = queueOrder;
        this.nextSheetSequence = nextSheetSequence;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "flow_candidates",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index(value = "sourceKey", unique = true),
                @Index(value = {"taskId", "seedStepId", "slot"}, unique = true),
                @Index(value = {"slot", "queueOrder", "createdAtEpochMillis"})})
public final class FlowCandidateEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String seedStepId;
    @NonNull public String sourceKey;
    @NonNull public String scheduledOn;
    @NonNull public String slot;
    public long queueOrder;
    public long createdAtEpochMillis;

    public FlowCandidateEntity(@NonNull String id, @NonNull String taskId,
            @NonNull String seedStepId, @NonNull String sourceKey,
            @NonNull String scheduledOn, @NonNull String slot, long queueOrder,
            long createdAtEpochMillis) {
        this.id = id; this.taskId = taskId; this.seedStepId = seedStepId;
        this.sourceKey = sourceKey; this.scheduledOn = scheduledOn; this.slot = slot;
        this.queueOrder = queueOrder; this.createdAtEpochMillis = createdAtEpochMillis;
    }
}

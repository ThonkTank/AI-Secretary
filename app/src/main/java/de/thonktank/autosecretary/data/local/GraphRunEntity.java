package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;

/** Durable run identity; execution state lives exclusively on GraphRunStepEntity. */
@Entity(tableName = "step_flow_runs",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index("seedStepId"), @Index(value = "sourceKey", unique = true),
                @Index(value = {"collected", "cancelled", "queueOrder"})})
public final class GraphRunEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String taskId = "";
    @NonNull public String seedStepId = "";
    @NonNull public String startStepId = "";
    @NonNull public String sourceKey = "";
    @NonNull public String scheduledOn = "";
    @NonNull public String slot = "";
    public long queueOrder;
    public int nextExecutionSequence;
    public long createdAtEpochMillis;
    public long updatedAtEpochMillis;
    public long alreadyPaidTau;
    public boolean collected;
    public boolean cancelled;
}

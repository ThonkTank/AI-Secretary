package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "flow_run_resources",
        foreignKeys = @ForeignKey(entity = StepFlowRunEntity.class, parentColumns = "id",
                childColumns = "runId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("runId"), @Index(value = {"resourceId", "state"}),
                @Index(value = {"runId", "acquirePosition"})})
public class FlowRunResourceEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String runId;
    @NonNull public String sourceLeaseId;
    @NonNull public String resourceId;
    @NonNull public String resourceName;
    public int capacityAtCreation;
    public int units;
    public int acquirePosition;
    public int releasePosition;
    @NonNull public String state;
    @Nullable public Long reservedAtEpochMillis;
    @Nullable public Long activatedAtEpochMillis;
    @Nullable public Long releasedAtEpochMillis;

    public FlowRunResourceEntity(@NonNull String id, @NonNull String runId,
                                 @NonNull String sourceLeaseId, @NonNull String resourceId,
                                 @NonNull String resourceName, int capacityAtCreation, int units,
                                 int acquirePosition, int releasePosition, @NonNull String state,
                                 @Nullable Long reservedAtEpochMillis,
                                 @Nullable Long activatedAtEpochMillis,
                                 @Nullable Long releasedAtEpochMillis) {
        this.id = id;
        this.runId = runId;
        this.sourceLeaseId = sourceLeaseId;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.capacityAtCreation = capacityAtCreation;
        this.units = units;
        this.acquirePosition = acquirePosition;
        this.releasePosition = releasePosition;
        this.state = state;
        this.reservedAtEpochMillis = reservedAtEpochMillis;
        this.activatedAtEpochMillis = activatedAtEpochMillis;
        this.releasedAtEpochMillis = releasedAtEpochMillis;
    }
}

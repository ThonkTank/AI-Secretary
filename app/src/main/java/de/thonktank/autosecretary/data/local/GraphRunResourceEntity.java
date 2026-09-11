package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(tableName = "flow_run_resources", foreignKeys = {
        @ForeignKey(entity = GraphRunEntity.class, parentColumns = "id", childColumns = "runId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = GraphRunStepEntity.class, parentColumns = "id", childColumns = "acquireStepId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = GraphRunStepEntity.class, parentColumns = "id", childColumns = "releaseStepId", onDelete = ForeignKey.CASCADE)
}, indices = {@Index("runId"), @Index(value = {"resourceId", "state"}), @Index("acquireStepId"), @Index("releaseStepId")})
public final class GraphRunResourceEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String runId = "";
    @NonNull public String sourceLeaseId = "";
    @NonNull public String resourceId = "";
    @NonNull public String resourceName = "";
    public int capacityAtCreation;
    public int units;
    @NonNull public String state = "";
    @Nullable public Long reservedAtEpochMillis;
    @Nullable public Long activatedAtEpochMillis;
    @Nullable public Long releasedAtEpochMillis;
    @NonNull public String acquireStepId = "";
    @NonNull public String releaseStepId = "";
    public boolean releaseAfterWait;
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "step_resource_leases", foreignKeys = {
        @ForeignKey(entity = TaskEntity.class, parentColumns = "id", childColumns = "taskId",
                onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                childColumns = "acquireStepId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                childColumns = "releaseStepId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = CapacityResourceEntity.class, parentColumns = "id",
                childColumns = "resourceId", onDelete = ForeignKey.RESTRICT)
}, indices = {@Index("taskId"), @Index("acquireStepId"), @Index("releaseStepId"),
        @Index("resourceId")})
public class StepResourceLeaseEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String acquireStepId;
    @NonNull public String releaseStepId;
    @NonNull public String resourceId;
    public int units;

    public StepResourceLeaseEntity(@NonNull String id, @NonNull String taskId,
                                   @NonNull String acquireStepId, @NonNull String releaseStepId,
                                   @NonNull String resourceId, int units) {
        this.id = id;
        this.taskId = taskId;
        this.acquireStepId = acquireStepId;
        this.releaseStepId = releaseStepId;
        this.resourceId = resourceId;
        this.units = units;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(tableName = "flow_step_waits", foreignKeys = @ForeignKey(entity = TaskStepEntity.class,
        parentColumns = "id", childColumns = "stepId", onDelete = ForeignKey.CASCADE))
public final class FlowStepWaitEntity {
    @PrimaryKey @NonNull public String stepId = "";
    @NonNull public String mode = "";
    public long defaultDelayMillis;
    @Nullable public Long lastUsedDelayMillis;
}

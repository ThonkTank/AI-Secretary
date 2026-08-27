package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "step_transitions", primaryKeys = "sourceStepId",
        foreignKeys = {
                @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                        childColumns = "sourceStepId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                        childColumns = "targetStepId", onDelete = ForeignKey.CASCADE)
        }, indices = @Index("targetStepId"))
public class StepTransitionEntity {
    @NonNull public String sourceStepId;
    @NonNull public String targetStepId;
    @NonNull public String delayMode;
    public long defaultDelayMillis;
    @Nullable public Long lastUsedDelayMillis;

    public StepTransitionEntity(@NonNull String sourceStepId, @NonNull String targetStepId,
                                @NonNull String delayMode, long defaultDelayMillis,
                                @Nullable Long lastUsedDelayMillis) {
        this.sourceStepId = sourceStepId;
        this.targetStepId = targetStepId;
        this.delayMode = delayMode;
        this.defaultDelayMillis = defaultDelayMillis;
        this.lastUsedDelayMillis = lastUsedDelayMillis;
    }
}

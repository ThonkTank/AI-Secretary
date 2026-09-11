package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "step_transitions", primaryKeys = {"sourceStepId", "targetStepId"}, foreignKeys = {
        @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id", childColumns = "sourceStepId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id", childColumns = "targetStepId", onDelete = ForeignKey.CASCADE)
}, indices = @Index("targetStepId"))
public final class FlowDefinitionEdgeEntity {
    @NonNull public String sourceStepId = "";
    @NonNull public String targetStepId = "";
}

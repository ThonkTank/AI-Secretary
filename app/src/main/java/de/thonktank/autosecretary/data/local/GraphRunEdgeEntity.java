package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "flow_run_edges", primaryKeys = {"runId", "sourceStepId", "targetStepId"}, foreignKeys = {
        @ForeignKey(entity = GraphRunEntity.class, parentColumns = "id", childColumns = "runId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = GraphRunStepEntity.class, parentColumns = "id", childColumns = "sourceStepId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = GraphRunStepEntity.class, parentColumns = "id", childColumns = "targetStepId", onDelete = ForeignKey.CASCADE)
}, indices = {@Index("sourceStepId"), @Index("targetStepId")})
public final class GraphRunEdgeEntity {
    @NonNull public String runId = "";
    @NonNull public String sourceStepId = "";
    @NonNull public String targetStepId = "";
}

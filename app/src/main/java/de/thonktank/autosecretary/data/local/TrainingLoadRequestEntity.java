package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "training_load_requests",
        foreignKeys = @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                childColumns = "templateId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("templateId"), @Index("sourceOccurrenceStepId"),
                @Index(value = "auditOrder", unique = true)})
public final class TrainingLoadRequestEntity {
    @PrimaryKey @NonNull public final String id;
    @NonNull public final String templateId;
    @NonNull public final String sourceOccurrenceStepId;
    @NonNull public final String direction;
    @NonNull public final String currentLoadMode;
    @NonNull public final String currentLoadUnit;
    public final long currentLoadMilli;
    @NonNull public final String createdOn;
    public final long auditOrder;
    public final int ruleVersion;
    @NonNull public final String state;
    @NonNull public final String resolution;
    public final String resolvedOn;

    public TrainingLoadRequestEntity(@NonNull String id, @NonNull String templateId,
                                     @NonNull String sourceOccurrenceStepId,
                                     @NonNull String direction,
                                     @NonNull String currentLoadMode,
                                     @NonNull String currentLoadUnit, long currentLoadMilli,
                                     @NonNull String createdOn, long auditOrder,
                                     int ruleVersion, @NonNull String state,
                                     @NonNull String resolution, String resolvedOn) {
        this.id = id;
        this.templateId = templateId;
        this.sourceOccurrenceStepId = sourceOccurrenceStepId;
        this.direction = direction;
        this.currentLoadMode = currentLoadMode;
        this.currentLoadUnit = currentLoadUnit;
        this.currentLoadMilli = currentLoadMilli;
        this.createdOn = createdOn;
        this.auditOrder = auditOrder;
        this.ruleVersion = ruleVersion;
        this.state = state;
        this.resolution = resolution;
        this.resolvedOn = resolvedOn;
    }
}

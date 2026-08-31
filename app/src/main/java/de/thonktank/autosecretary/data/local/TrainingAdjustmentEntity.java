package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "training_adjustments",
        foreignKeys = @ForeignKey(entity = TaskStepEntity.class, parentColumns = "id",
                childColumns = "templateId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("templateId"), @Index("sourceOccurrenceStepId"),
                @Index(value = "auditOrder", unique = true)})
public final class TrainingAdjustmentEntity {
    @PrimaryKey @NonNull public final String id;
    @NonNull public final String templateId;
    @NonNull public final String sourceOccurrenceStepId;
    @NonNull public final String reason;
    public final int beforeSets;
    public final int beforeReps;
    @NonNull public final String beforeLoadMode;
    @NonNull public final String beforeLoadUnit;
    public final Long beforeLoadMilli;
    public final int afterSets;
    public final int afterReps;
    @NonNull public final String afterLoadMode;
    @NonNull public final String afterLoadUnit;
    public final Long afterLoadMilli;
    @NonNull public final String createdOn;
    @NonNull public final String state;
    public final long auditOrder;
    public final int ruleVersion;

    public TrainingAdjustmentEntity(@NonNull String id, @NonNull String templateId,
                                    @NonNull String sourceOccurrenceStepId,
                                    @NonNull String reason, int beforeSets, int beforeReps,
                                    @NonNull String beforeLoadMode,
                                    @NonNull String beforeLoadUnit, Long beforeLoadMilli,
                                    int afterSets, int afterReps,
                                    @NonNull String afterLoadMode,
                                    @NonNull String afterLoadUnit, Long afterLoadMilli,
                                    @NonNull String createdOn, @NonNull String state,
                                    long auditOrder, int ruleVersion) {
        this.id = id; this.templateId = templateId;
        this.sourceOccurrenceStepId = sourceOccurrenceStepId; this.reason = reason;
        this.beforeSets = beforeSets; this.beforeReps = beforeReps;
        this.beforeLoadMode = beforeLoadMode; this.beforeLoadUnit = beforeLoadUnit;
        this.beforeLoadMilli = beforeLoadMilli; this.afterSets = afterSets;
        this.afterReps = afterReps; this.afterLoadMode = afterLoadMode;
        this.afterLoadUnit = afterLoadUnit; this.afterLoadMilli = afterLoadMilli;
        this.createdOn = createdOn; this.state = state;
        this.auditOrder = auditOrder; this.ruleVersion = ruleVersion;
    }
}

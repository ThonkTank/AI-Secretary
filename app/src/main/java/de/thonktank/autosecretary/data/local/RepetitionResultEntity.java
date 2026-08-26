package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Ignore;

@Entity(tableName = "repetition_results",
        primaryKeys = {"stepId", "slotIndex"},
        foreignKeys = @ForeignKey(entity = OccurrenceStepEntity.class, parentColumns = "id",
                childColumns = "stepId", onDelete = ForeignKey.CASCADE),
        indices = @Index("stepId"))
public final class RepetitionResultEntity {
    @NonNull public final String stepId;
    public final int slotIndex;
    public final int actualRepetitions;
    @NonNull public final String loadMode;
    @NonNull public final String loadUnit;
    public final Long loadMilli;
    public final Integer rir;
    @NonNull public final String source;
    @NonNull public final String safetyFlag;

    @Ignore public RepetitionResultEntity(@NonNull String stepId, int slotIndex,
                                  int actualRepetitions) {
        this(stepId, slotIndex, actualRepetitions, "UNSPECIFIED", "NONE", null,
                null, "LEGACY", "NONE");
    }

    public RepetitionResultEntity(@NonNull String stepId, int slotIndex,
                                  int actualRepetitions, @NonNull String loadMode,
                                  @NonNull String loadUnit, Long loadMilli, Integer rir,
                                  @NonNull String source, @NonNull String safetyFlag) {
        this.stepId = stepId;
        this.slotIndex = slotIndex;
        this.actualRepetitions = actualRepetitions;
        this.loadMode = loadMode;
        this.loadUnit = loadUnit;
        this.loadMilli = loadMilli;
        this.rir = rir;
        this.source = source;
        this.safetyFlag = safetyFlag;
    }
}

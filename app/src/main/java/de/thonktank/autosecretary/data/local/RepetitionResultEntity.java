package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "repetition_results",
        primaryKeys = {"stepId", "slotIndex"},
        foreignKeys = @ForeignKey(entity = OccurrenceStepEntity.class, parentColumns = "id",
                childColumns = "stepId", onDelete = ForeignKey.CASCADE),
        indices = @Index("stepId"))
public final class RepetitionResultEntity {
    @NonNull public final String stepId;
    public final int slotIndex;
    public final int actualRepetitions;

    public RepetitionResultEntity(@NonNull String stepId, int slotIndex,
                                  int actualRepetitions) {
        this.stepId = stepId;
        this.slotIndex = slotIndex;
        this.actualRepetitions = actualRepetitions;
    }
}

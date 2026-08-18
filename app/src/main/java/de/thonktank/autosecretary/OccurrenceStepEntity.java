package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "occurrence_steps",
        foreignKeys = @ForeignKey(entity = OccurrenceEntity.class, parentColumns = "id",
                childColumns = "occurrenceId", onDelete = ForeignKey.CASCADE),
        indices = @Index("occurrenceId"))
public class OccurrenceStepEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String occurrenceId;
    public int position;
    @NonNull public String text;
    public boolean done;
    @NonNull public String amountKind;
    public Integer plannedSets;
    public Integer plannedReps;
    public Integer plannedDurationSeconds;
    @NonNull public String note;
    @NonNull public String actualRepetitions;
    @Nullable public String sourceTemplateId;
    @NonNull public String comboOwnerId;

    public OccurrenceStepEntity(@NonNull String id, @NonNull String occurrenceId,
                                int position, @NonNull String text, boolean done,
                                @NonNull String amountKind, Integer plannedSets,
                                Integer plannedReps, Integer plannedDurationSeconds,
                                @NonNull String note, @NonNull String actualRepetitions,
                                @Nullable String sourceTemplateId,
                                @NonNull String comboOwnerId) {
        this.id = id; this.occurrenceId = occurrenceId; this.position = position;
        this.text = text; this.done = done; this.amountKind = amountKind;
        this.plannedSets = plannedSets; this.plannedReps = plannedReps;
        this.plannedDurationSeconds = plannedDurationSeconds; this.note = note;
        this.actualRepetitions = actualRepetitions; this.sourceTemplateId = sourceTemplateId;
        this.comboOwnerId = comboOwnerId;
    }
}

package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "occurrence_steps", foreignKeys = @ForeignKey(entity = OccurrenceEntity.class, parentColumns = "id", childColumns = "occurrenceId", onDelete = ForeignKey.CASCADE), indices = @Index("occurrenceId"))
public class OccurrenceStepEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String occurrenceId;
    public int position;
    @NonNull public String text;
    public boolean done;
    public OccurrenceStepEntity(@NonNull String id, @NonNull String occurrenceId, int position, @NonNull String text, boolean done) { this.id = id; this.occurrenceId = occurrenceId; this.position = position; this.text = text; this.done = done; }
}

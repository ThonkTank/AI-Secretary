package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "occurrences", foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id", childColumns = "taskId", onDelete = ForeignKey.CASCADE), indices = @Index("taskId"))
public class OccurrenceEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String scheduledOn;
    @NonNull public String state; // OPEN, COMPLETED
    public int sortOrder;
    @NonNull public String completedOn;
    public OccurrenceEntity(@NonNull String id, @NonNull String taskId, @NonNull String scheduledOn, @NonNull String state, int sortOrder, @NonNull String completedOn) {
        this.id = id; this.taskId = taskId; this.scheduledOn = scheduledOn; this.state = state; this.sortOrder = sortOrder; this.completedOn = completedOn;
    }
}

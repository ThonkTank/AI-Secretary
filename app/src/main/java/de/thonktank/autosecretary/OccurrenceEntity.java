package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "occurrences",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index(value = {"state", "completedOn"}),
                @Index(value = {"taskId", "scheduledOn", "slot"}, unique = true)})
public class OccurrenceEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String scheduledOn;
    @NonNull public String state;
    public int sortOrder;
    @Nullable public String completedOn;
    @NonNull public String slot;

    @Ignore public OccurrenceEntity(@NonNull String id, @NonNull String taskId,
                                    @NonNull String scheduledOn, @NonNull String state,
                                    int sortOrder, @Nullable String completedOn) {
        this(id, taskId, scheduledOn, state, sortOrder, completedOn, "MORNING");
    }

    public OccurrenceEntity(@NonNull String id, @NonNull String taskId,
                            @NonNull String scheduledOn, @NonNull String state,
                                    int sortOrder, @Nullable String completedOn, @NonNull String slot) {
        this.id = id; this.taskId = taskId; this.scheduledOn = scheduledOn;
        this.state = state; this.sortOrder = sortOrder; this.completedOn = completedOn;
        this.slot = slot;
    }
}

package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "occurrences",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index(value = {"state", "completedOn"}),
                @Index(value = "sourceKey", unique = true), @Index("flowRunId")})
public class OccurrenceEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String scheduledOn;
    @NonNull public String state;
    public int sortOrder;
    @Nullable public String completedOn;
    @NonNull public String slot;
    @ColumnInfo(defaultValue = "'SCHEDULED'") @NonNull public String kind;
    @ColumnInfo(defaultValue = "''") @NonNull public String sourceKey;
    @Nullable public String flowRunId;
    @ColumnInfo(defaultValue = "0") public int flowSheetSequence;

    @Ignore public OccurrenceEntity(@NonNull String id, @NonNull String taskId,
                                    @NonNull String scheduledOn, @NonNull String state,
                                    int sortOrder, @Nullable String completedOn) {
        this(id, taskId, scheduledOn, state, sortOrder, completedOn, "MORNING");
    }

    @Ignore public OccurrenceEntity(@NonNull String id, @NonNull String taskId,
                            @NonNull String scheduledOn, @NonNull String state,
                                    int sortOrder, @Nullable String completedOn, @NonNull String slot) {
        this(id, taskId, scheduledOn, state, sortOrder, completedOn, slot, "SCHEDULED",
                "scheduled:" + taskId + ':' + scheduledOn + ':' + slot, null, 0);
    }

    public OccurrenceEntity(@NonNull String id, @NonNull String taskId,
                            @NonNull String scheduledOn, @NonNull String state,
                            int sortOrder, @Nullable String completedOn, @NonNull String slot,
                            @NonNull String kind, @NonNull String sourceKey,
                            @Nullable String flowRunId, int flowSheetSequence) {
        this.id = id; this.taskId = taskId; this.scheduledOn = scheduledOn;
        this.state = state; this.sortOrder = sortOrder; this.completedOn = completedOn;
        this.slot = slot;
        this.kind = kind;
        this.sourceKey = sourceKey;
        this.flowRunId = flowRunId;
        this.flowSheetSequence = flowSheetSequence;
    }
}

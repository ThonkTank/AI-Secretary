package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "combo_obligations",
        foreignKeys = {
                @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                        childColumns = "taskId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = OccurrenceEntity.class, parentColumns = "id",
                        childColumns = "occurrenceId", onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index("taskId"), @Index("occurrenceId"),
                @Index(value = {"ownerId", "state", "scheduledOn"})
        })
public final class ComboObligationEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String ownerId;
    @NonNull public String taskId;
    @NonNull public String kind;
    @NonNull public String slot;
    @NonNull public String scheduledOn;
    @NonNull public String occurrenceId;
    @NonNull public String state;
    @Nullable public String resolvedOn;

    public ComboObligationEntity(@NonNull String id, @NonNull String ownerId,
                                 @NonNull String taskId, @NonNull String kind,
                                 @NonNull String slot, @NonNull String scheduledOn,
                                 @NonNull String occurrenceId, @NonNull String state,
                                 @Nullable String resolvedOn) {
        this.id = id;
        this.ownerId = ownerId;
        this.taskId = taskId;
        this.kind = kind;
        this.slot = slot;
        this.scheduledOn = scheduledOn;
        this.occurrenceId = occurrenceId;
        this.state = state;
        this.resolvedOn = resolvedOn;
    }
}

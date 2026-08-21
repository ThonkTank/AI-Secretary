package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "combo_progress",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = @Index("taskId"))
public class ComboEntity {
    @PrimaryKey @NonNull public String ownerId;
    @NonNull public String taskId;
    @NonNull public String kind;
    public int points;
    @NonNull public String settledThroughOn;

    public ComboEntity(@NonNull String ownerId, @NonNull String taskId,
                       @NonNull String kind, int points, @NonNull String settledThroughOn) {
        this.ownerId = ownerId;
        this.taskId = taskId;
        this.kind = kind;
        this.points = points;
        this.settledThroughOn = settledThroughOn;
    }
}

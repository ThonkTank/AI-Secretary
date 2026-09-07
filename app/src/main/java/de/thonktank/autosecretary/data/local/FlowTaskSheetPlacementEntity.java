package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "flow_task_sheet_placements",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"taskId", "slot"}, unique = true),
                @Index(value = {"slot", "displayOn", "sortOrder"})})
public final class FlowTaskSheetPlacementEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String taskId;
    @NonNull public String slot;
    @NonNull public String displayOn;
    public int sortOrder;

    public FlowTaskSheetPlacementEntity(@NonNull String id, @NonNull String taskId,
            @NonNull String slot, @NonNull String displayOn, int sortOrder) {
        this.id = id; this.taskId = taskId; this.slot = slot;
        this.displayOn = displayOn; this.sortOrder = sortOrder;
    }
}

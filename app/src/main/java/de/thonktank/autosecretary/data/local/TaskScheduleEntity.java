package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_schedule_entries",
        foreignKeys = @ForeignKey(entity = TaskEntity.class, parentColumns = "id",
                childColumns = "taskId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("taskId"), @Index(value = {"taskId", "slot"}, unique = true),
                @Index(value = {"slot", "displayOrder"})})
public final class TaskScheduleEntity {
    @PrimaryKey @NonNull public final String id;
    @NonNull public final String taskId;
    @NonNull public final String slot;
    public final long displayOrder;

    public TaskScheduleEntity(@NonNull String id, @NonNull String taskId,
                              @NonNull String slot, long displayOrder) {
        this.id = id;
        this.taskId = taskId;
        this.slot = slot;
        this.displayOrder = displayOrder;
    }
}

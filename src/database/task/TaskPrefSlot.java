package database.task;

import java.time.LocalTime;
import java.time.DayOfWeek;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;

@Entity (tableName = "task_pref_slots",
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskPrefSlot {
    @PrimaryKey
    public long id;
    public long taskId;
    public DayOfWeek day;
    public LocalTime start;
}
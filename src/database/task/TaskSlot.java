package database.task;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity(tableName = "task_slots",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskSlot {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long taskId;
    public Long parentSlotId;
    @Ignore
    public TaskSlot parentSlot;

    public LocalDate day;
    public LocalTime start;
    public LocalTime end;
    public LocalTime realStart;
    public LocalTime realEnd;
    public boolean completed;
    public int sinceCompleted() {return (int) ChronoUnit.DAYS.between(day, LocalDate.now());}
    public int score;
}
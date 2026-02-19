package database.task;

import java.time.LocalDate;
import java.time.LocalTime;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;

@Entity (tableName = "task_slots")
public class TaskSlot {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long taskId;

    public LocalDate day;
    public LocalTime start;
    public LocalTime end;
    public LocalTime realStart;
    public LocalTime realEnd;
    public int score;
}
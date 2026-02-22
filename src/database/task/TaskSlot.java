package database.task;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(tableName = "task_slots",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskSlot {
    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public String parent;
    @Ignore
    public List<TaskSlot> children = new ArrayList<>();

    public LocalDate day;
    public LocalTime start;
    public LocalTime end;
    public LocalTime realStart;
    public LocalTime realEnd;
    public boolean scheduled;
    public boolean completed;
    public int sinceCompleted() {return (int) ChronoUnit.DAYS.between(day, LocalDate.now());}
    public int score;

    public static List<TaskSlot> buildTree(List<TaskSlot> slots) {
        Map<String, TaskSlot> mappedSlots = new HashMap<>();
        List<TaskSlot> slotTree = new ArrayList<>();
        
        for (TaskSlot slot : slots) {
            mappedSlots.put(slot.id, slot);
        }

        for (TaskSlot slot : slots) {
            if (slot.parent != null) {
                mappedSlots.get(slot.parent).children.add(slot);
            } else {
                slotTree.add(slot);
            }
        }
        
        return slotTree;
    }
}
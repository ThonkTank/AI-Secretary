package database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity (tableName = "task_relation",
    indices = @Index("child"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "child",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskRelation {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long child;
    public Long parent;

    public TaskRelation(Long child, Long parent) {
        this.child = child;
        this.parent = parent;
    }
}

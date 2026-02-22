package database.task;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity (tableName = "task_relation",
    indices = @Index("child"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "child",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskRelation {
    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String child;
    public String parent;

    public TaskRelation(String child, String parent) {
        this.child = child;
        this.parent = parent;
    }
}

package com.autosecretary.database.task;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.autosecretary.util.TreeBuilder;

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

    public static final TreeBuilder<TaskSlot> TREE_BUILDER = new TreeBuilder<>(
            slot -> slot.id,
            slot -> slot.parent != null
                    ? Collections.singletonList(slot.parent)
                    : Collections.emptyList(),
            (parent, child) -> parent.children.add(child),
            slot -> slot.children
    );

    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public String parent;
    @Ignore
    public List<TaskSlot> children = new ArrayList<>();

    public LocalDate day;
    public LocalTime start;
    public LocalTime end;
    public boolean scheduled;
    public boolean completed;
    public int sinceCompleted() {return (int) ChronoUnit.DAYS.between(day, LocalDate.now());}
    public int score;

    public LocalTime realStart;
    public LocalTime realEnd;
}
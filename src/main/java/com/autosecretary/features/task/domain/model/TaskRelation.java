package com.autosecretary.features.task.domain.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Room entity linking tasks in a parent-child hierarchy. Both {@code child} and
 * {@code parent} reference task_core IDs. Used by
 * {@link com.autosecretary.features.task.domain.TaskTreeOperations TaskTreeOperations}
 * to build/flatten task trees.
 */
@Entity (tableName = "task_relation",
    primaryKeys = {"child", "parent"},
    indices = @Index("child"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "child",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskRelation {
    @NonNull
    public String child;
    @NonNull
    public String parent;

    public TaskRelation() {}

    @Ignore
    public TaskRelation(String parent, String child) {
        this.parent = parent;
        this.child = child;
    }
}

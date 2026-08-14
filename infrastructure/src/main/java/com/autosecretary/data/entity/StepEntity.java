package com.autosecretary.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "steps",
        foreignKeys = @ForeignKey(
                entity = WorkItemEntity.class,
                parentColumns = "id",
                childColumns = "workItemId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"workItemId", "position"}, unique = true)})
public final class StepEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String workItemId = "";
    @NonNull public String title = "";
    public int position;
}

package com.autosecretary.features.task.data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Room entity for scheduled/completed time blocks. Each slot belongs to one task
 * via {@code taskId}. Tracks planned (start/end) and actual (realStart/realEnd)
 * execution times. Supports slot-level tree hierarchy via {@code parent}.
 */
@Entity(tableName = "task_slots",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskSlot {

    public enum DisplacementGroupType {
        CHAIN, FIXED, SINGLE
    }

    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    public String parent;
    public String chainId;
    @Ignore
    public List<TaskSlot> children = new ArrayList<>();

    public LocalDate day;
    public LocalTime start;
    public LocalTime end;
    public boolean scheduled;
    public boolean completed;
    public int score;
    /**
     * Persisted displacement value used as reproducible loss baseline during re-planning.
     */
    public int displacementScore;
    /**
     * Atomic displacement group id (e.g. prerequisite chain or fixed block group).
     */
    public String displacementGroupId;
    /**
     * Group type for displacement semantics.
     */
    public DisplacementGroupType displacementGroupType;

    public LocalTime realStart;
    public LocalTime realEnd;
}

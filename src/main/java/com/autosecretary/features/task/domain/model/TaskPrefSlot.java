package com.autosecretary.features.task.domain.model;

import java.util.Set;
import java.util.UUID;
import java.time.DayOfWeek;
import java.time.LocalTime;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Embedded;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Room entity for preferred scheduling patterns. Each row specifies a preferred day-of-week
 * set and a preferred start time for a task. A task can have multiple {@code TaskPrefSlot}
 * rows (one per preferred pattern), and the scheduler scores candidate placements higher
 * when they match a preference.
 *
 * <p>When {@code TaskCore.adaptive == true}, {@code TaskLifecycleManager.adaptPrefSlot()}
 * nudges the closest matching {@code start} toward the user's actual completion time on
 * each check-off, using an exponential moving average (α = 0.2, rounded to 5-minute
 * granularity). This lets the schedule self-adjust to real behaviour over time.
 *
 * <p>Stored in the {@code task_pref_slots} table. Serialized as a comma-separated string
 * of day names by {@code Converters} in the {@code database/} package.
 */
@Entity (tableName = "task_pref_slots",
    indices = @Index("taskId"),
    foreignKeys = @ForeignKey(
        entity = TaskCore.class,
        parentColumns = "id",
        childColumns = "taskId",
        onDelete = ForeignKey.CASCADE
    ))

public class TaskPrefSlot {
    @PrimaryKey() @NonNull
    public String id = UUID.randomUUID().toString();
    public String taskId;
    /** Days of the week on which this preference applies. Null means "any day". */
    public Set<DayOfWeek> days;
    /** Preferred start time. Adapted toward real completion times when {@code TaskCore.adaptive == true}. */
    public LocalTime start;
}

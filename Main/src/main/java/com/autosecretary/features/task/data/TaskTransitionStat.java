package com.autosecretary.features.task.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import java.time.LocalDateTime;

/**
 * Persistence model for learned task transitions (A -> B) used by scheduling follow-up boosts.
 */
@Entity(
        tableName = "task_transition_stats",
        primaryKeys = {"fromTaskId", "toTaskId"}
)
public class TaskTransitionStat {
    @NonNull
    public String fromTaskId;
    @NonNull
    public String toTaskId;
    public int weight;
    public LocalDateTime lastSeen;

    public TaskTransitionStat(@NonNull String fromTaskId,
                              @NonNull String toTaskId,
                              int weight,
                              LocalDateTime lastSeen) {
        this.fromTaskId = fromTaskId;
        this.toTaskId = toTaskId;
        this.weight = weight;
        this.lastSeen = lastSeen;
    }
}

package com.autosecretary.features.task.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDateTime;
import java.util.List;

@Dao
public interface TaskTransitionStatDao {

    @Query("SELECT * FROM task_transition_stats")
    List<TaskTransitionStat> readAll();

    @Query("UPDATE task_transition_stats SET weight = weight + :delta, lastSeen = :lastSeen WHERE fromTaskId = :fromTaskId AND toTaskId = :toTaskId")
    int incrementWeight(String fromTaskId, String toTaskId, int delta, LocalDateTime lastSeen);

    // Named `insert` (not `write`) intentionally: IGNORE conflict strategy is used here, not REPLACE.
    // Task DAO convention is read*/write* (upsert via REPLACE) / delete*; this is the sole non-upsert write.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(TaskTransitionStat stat);

    @Transaction
    default void recordTransition(String fromTaskId, String toTaskId, int delta, LocalDateTime lastSeen) {
        if (fromTaskId == null || toTaskId == null || fromTaskId.equals(toTaskId)) {
            return;
        }

        int updated = incrementWeight(fromTaskId, toTaskId, delta, lastSeen);
        if (updated == 0) {
            insert(new TaskTransitionStat(fromTaskId, toTaskId, delta, lastSeen));
        }
    }
}

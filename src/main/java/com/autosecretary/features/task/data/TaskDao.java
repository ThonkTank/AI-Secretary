package com.autosecretary.features.task.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;

/**
 * Room DAO for task persistence. Provides bulk write ({@link #writeList}), single-task
 * upsert ({@link #write}), and read operations. All writes use REPLACE conflict strategy (upsert).
 */
@Dao
public interface TaskDao {

    // ============== READ ==============
    @Transaction
    @Query("SELECT * FROM task_core WHERE id = :id")
    Task read(String id);
    @Transaction
    @Query("SELECT * FROM task_core")
    List<Task> readAll();

    // ============== Write ==============
    /**
     * Bulk write for a pre-flattened list of tasks. Used by {@code RegenerateScheduleUseCase}.
     * Callers must flatten the task tree via {@code TaskTreeOperations.flatten()} before calling.
     */
    @Transaction
    default void writeList(List<Task> tasks) {
        // 2-pass: insert all TaskCore rows first (they are FK targets),
        // then write dependent entities (slots, prefSlots, prerequisites, relations).
        for (Task task : tasks) {
            writeCore(task.core);
        }
        for (Task task : tasks) {
            writeDependents(task);
        }
    }

    /**
     * Single-task upsert. Used by {@code saveEditedTask()} and {@code CheckOffTaskUseCase}.
     */
    @Transaction
    default void write(Task task) {
        writeCore(task.core);
        writeDependents(task);
    }

    /**
     * Writes dependent rows that reference a task core row.
     * PrefSlots and prerequisites are replaced wholesale (delete + insert) so that
     * rows removed during editing do not silently accumulate as orphans.
     * Slots are upserted without prior deletion to preserve historical completed slots.
     */
    default void writeDependents(Task task) {
        writeSlots(task.slots);
        deletePrefSlotsByTaskId(task.core.id);
        writePrefSlots(task.prefSlots);
        deletePrerequisitesByTaskId(task.core.id);
        writePrerequisites(task.prerequisites);
        writePlannedMeals(task.plannedMeals);
        deleteRelationsByParentId(task.core.id);
        for (Task child : task.children) {
            writeRelation(new TaskRelation(task.core.id, child.core.id));
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeCore(TaskCore core);

    //Pref Slots
    @Query("DELETE FROM task_pref_slots WHERE taskId = :taskId")
    void deletePrefSlotsByTaskId(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrefSlots(List<TaskPrefSlot> prefSlots);

    //Prerequisites
    @Query("DELETE FROM task_prerequisites WHERE taskId = :taskId")
    void deletePrerequisitesByTaskId(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePrerequisites(List<TaskPrerequisite> prerequisites);

    //Parent
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeRelation(TaskRelation relation);

    //Task Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlots(List<TaskSlot> slots);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlot(TaskSlot slots);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePlannedMeals(List<TaskPlannedMeal> plannedMeals);

    @Query("UPDATE task_slots SET realStart = :startTime, realEnd = NULL WHERE id = :slotId")
    void startTimer(String slotId, LocalTime startTime);

    @Query("UPDATE task_slots SET realEnd = :endTime WHERE id = :slotId")
    void stopTimer(String slotId, LocalTime endTime);

    @Query("""
            SELECT taskId
            FROM task_slots
            WHERE taskId != :taskId
              AND (realStart IS NOT NULL OR completed = 1)
              AND (day < :day OR (day = :day AND COALESCE(realStart, realEnd, end, start) <= :eventTime))
            ORDER BY day DESC, COALESCE(realStart, realEnd, end, start) DESC
            LIMIT 1
            """)
    String readMostRecentTaskBefore(String taskId, LocalDate day, LocalTime eventTime);

    // ============== Delete ==============
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(String id);

    @Query("DELETE FROM task_relation WHERE parent = :taskId")
    void deleteRelationsByParentId(String taskId);

    @Query("DELETE FROM task_prerequisites WHERE prerequisiteId = :taskId")
    void deletePrerequisitesByDependencyId(String taskId);

    @Transaction
    default void deleteTaskGraph(String taskId) {
        deleteRelationsByParentId(taskId);
        deletePrerequisitesByDependencyId(taskId);
        deleteCore(taskId);
    }

    @Query("DELETE FROM task_core")
    void deleteAllCore();
}

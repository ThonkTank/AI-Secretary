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
            // Update parent-of (children) relations using the in-memory children list.
            // task.children is @Ignore and only populated by TaskTreeOperations.buildTree(),
            // so this step belongs here (bulk regen) rather than in writeDependents (which is
            // also called from the single-task editor path where children is always empty).
            deleteRelationsByParentId(task.core.id);
            for (Task child : task.children) {
                writeRelation(new TaskRelation(task.core.id, child.core.id));
            }
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
     * <p>
     * <strong>Delete+Insert strategy:</strong> PrefSlots and prerequisites are replaced
     * wholesale (delete + insert) so that rows removed during editing do not silently
     * accumulate as orphans.
     * <p>
     * <strong>Upsert-only (no deletion):</strong>
     * <ul>
     *   <li>Slots: upserted without prior deletion to preserve historical completed slots
     *       (allows replay of past executions).</li>
     *   <li>PlannedMeals: upserted without prior deletion. <strong>Warning:</strong> if a
     *       planned meal is removed from the in-memory {@code task.plannedMeals} list and
     *       the task is saved, the removed meal will persist as an orphan in the DB.
     *       This is intentional (meals may be pre-staged) but callers should be aware.</li>
     * </ul>
     * <p>
     * <strong>Parent-of (children) relations are NOT managed here.</strong>
     * {@code task.children} is {@code @Ignore} and only populated by
     * {@code TaskTreeOperations.buildTree()}. Managing child links here would silently wipe
     * all grandchild relations whenever a parent task is saved from the editor.
     * Child links are written by {@link #writeList(List)} which operates on the full tree.
     */
    default void writeDependents(Task task) {
        writeSlots(task.slots);
        deletePrefSlotsByTaskId(task.core.id);
        writePrefSlots(task.prefSlots);
        deletePrerequisitesByTaskId(task.core.id);
        writePrerequisites(task.prerequisites);
        writePlannedMeals(task.plannedMeals);
        deleteRelationsByChildId(task.core.id);
        for (TaskRelation parent : task.parents) {
            writeRelation(parent);
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
    @Query("DELETE FROM task_relation WHERE child = :taskId")
    void deleteRelationsByChildId(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeRelation(TaskRelation relation);

    //Task Slots
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlots(List<TaskSlot> slots);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeSlot(TaskSlot slot);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writePlannedMeals(List<TaskPlannedMeal> plannedMeals);

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

    /**
     * Removes regeneratable slots in the active planning window.
     * Preserves started/completed work; only untouched scheduled slots are removed.
     */
    @Query("""
            DELETE FROM task_slots
            WHERE scheduled = 1
              AND completed = 0
              AND realStart IS NULL
              AND day >= :startInclusive
              AND day < :endExclusive
            """)
    void deleteRegeneratableSlotsInWindow(LocalDate startInclusive, LocalDate endExclusive);

    // ============== Delete ==============
    @Query("DELETE FROM task_core WHERE id = :id")
    void deleteCore(String id);

    @Query("DELETE FROM task_relation WHERE parent = :taskId")
    void deleteRelationsByParentId(String taskId);

    /**
     * Deletes all prerequisite links where {@code prerequisiteId = taskId}.
     * Removes the dependency relationships for all tasks that listed the given task as a
     * prerequisite, so they are no longer blocked by it.
     */
    @Query("DELETE FROM task_prerequisites WHERE prerequisiteId = :taskId")
    void deletePrerequisitesByDependencyId(String taskId);

    @Transaction
    default void deleteTaskGraph(String taskId) {
        deleteRelationsByParentId(taskId);
        deleteRelationsByChildId(taskId);
        deletePrerequisitesByDependencyId(taskId);
        deleteCore(taskId);
    }

    @Query("DELETE FROM task_core")
    void deleteAllCore();
}

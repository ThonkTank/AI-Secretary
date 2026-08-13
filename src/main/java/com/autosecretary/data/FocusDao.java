package com.autosecretary.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Upsert;

import com.autosecretary.data.entity.CompletionEntity;
import com.autosecretary.data.entity.DayPlanDirectiveEntity;
import com.autosecretary.data.entity.MigrationCandidateEntity;
import com.autosecretary.data.entity.MigrationReportEntity;
import com.autosecretary.data.entity.PlannedSlotEntity;
import com.autosecretary.data.entity.PlanningConflictEntity;
import com.autosecretary.data.entity.StepCompletionEntity;
import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.StepEntity;
import com.autosecretary.data.entity.UndoJournalEntity;
import com.autosecretary.data.entity.WorkItemEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public interface FocusDao {
    @Query("SELECT * FROM work_items ORDER BY createdAt, title")
    List<WorkItemEntity> readWorkItems();

    @Query("SELECT * FROM work_items WHERE id = :id")
    WorkItemEntity readWorkItem(String id);

    @Query("SELECT * FROM steps ORDER BY workItemId, position")
    List<StepEntity> readSteps();

    @Query("SELECT * FROM steps WHERE workItemId = :workItemId ORDER BY position")
    List<StepEntity> readSteps(String workItemId);

    @Query("SELECT * FROM steps WHERE id = :id LIMIT 1")
    StepEntity readStep(String id);

    @Query("SELECT * FROM step_days")
    List<StepDayEntity> readStepDays();

    @Query("SELECT * FROM step_completions")
    List<StepCompletionEntity> readStepCompletions();

    @Query("SELECT * FROM work_item_completions ORDER BY completedAt")
    List<CompletionEntity> readCompletions();

    @Query("SELECT * FROM work_item_completions WHERE completedAt >= :since ORDER BY completedAt")
    List<CompletionEntity> readCompletionsSince(String since);

    @Query("SELECT * FROM day_plan_directives WHERE day = :day ORDER BY updatedAt")
    List<DayPlanDirectiveEntity> readDirectives(String day);

    @Query("SELECT * FROM day_plan_directives WHERE day = :day AND workItemId = :workItemId LIMIT 1")
    DayPlanDirectiveEntity readDirective(String day, String workItemId);

    @Query("SELECT * FROM planned_slots WHERE day = :day ORDER BY startAt")
    List<PlannedSlotEntity> readPlannedSlots(String day);

    @Query("SELECT * FROM planning_conflicts ORDER BY computedAt DESC")
    List<PlanningConflictEntity> readPlanningConflicts();

    @Query("SELECT * FROM migration_reports ORDER BY id DESC LIMIT 1")
    MigrationReportEntity readLatestMigrationReport();

    @Query("SELECT * FROM migration_candidates ORDER BY title")
    List<MigrationCandidateEntity> readMigrationCandidates();

    @Query("SELECT * FROM migration_candidates WHERE id = :id LIMIT 1")
    MigrationCandidateEntity readMigrationCandidate(String id);

    @Query("DELETE FROM migration_candidates WHERE id = :id")
    int deleteMigrationCandidate(String id);

    @Query("UPDATE migration_reports SET acknowledged = 1 WHERE id = :id")
    int acknowledgeMigration(long id);

    @Query("SELECT * FROM undo_journal WHERE undoneAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    UndoJournalEntity readLatestUndo();

    @Query("SELECT COUNT(*) FROM undo_journal")
    int countUndoJournal();

    @Upsert
    void upsertWorkItem(WorkItemEntity item);

    @Upsert
    void upsertSteps(List<StepEntity> steps);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStepDays(List<StepDayEntity> days);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStepCompletion(StepCompletionEntity completion);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCompletion(CompletionEntity completion);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDirective(DayPlanDirectiveEntity directive);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUndo(UndoJournalEntity undo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlannedSlots(List<PlannedSlotEntity> slots);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPlanningConflicts(List<PlanningConflictEntity> conflicts);

    @Query("DELETE FROM work_items WHERE id = :id")
    void deleteWorkItem(String id);

    @Query("DELETE FROM steps WHERE id = :id")
    void deleteStep(String id);

    @Query("DELETE FROM step_days WHERE stepId = :stepId")
    void deleteStepDays(String stepId);

    @Query("UPDATE steps SET position = position + 1000000 WHERE workItemId = :workItemId")
    void parkStepPositions(String workItemId);

    @Query("DELETE FROM step_completions WHERE stepId = :stepId AND occurrenceKey = :occurrenceKey")
    void deleteStepCompletion(String stepId, String occurrenceKey);

    @Query("DELETE FROM day_plan_directives WHERE day = :day AND workItemId = :workItemId")
    void deleteDirective(String day, String workItemId);

    @Query("DELETE FROM planned_slots")
    void deletePlannedSlots();

    @Query("DELETE FROM planning_conflicts")
    void deletePlanningConflicts();

    @Query("UPDATE undo_journal SET undoneAt = :undoneAt WHERE id = :id AND undoneAt IS NULL")
    int markUndone(String id, String undoneAt);

    @Query("DELETE FROM undo_journal WHERE id NOT IN (SELECT id FROM undo_journal ORDER BY createdAt DESC LIMIT 20)")
    void trimUndoJournal();

    @Transaction
    default void upsertGraph(
            WorkItemEntity item,
            List<StepEntity> steps,
            List<StepDayEntity> days) {
        upsertWorkItem(item);
        // Free the unique (workItemId, position) keys before reordered stable IDs are upserted.
        parkStepPositions(item.id);
        Set<String> incomingIds = new HashSet<>();
        for (StepEntity step : steps) incomingIds.add(step.id);
        for (StepEntity existing : readSteps(item.id)) {
            if (!incomingIds.contains(existing.id)) deleteStep(existing.id);
        }
        upsertSteps(steps);
        for (StepEntity step : steps) deleteStepDays(step.id);
        upsertStepDays(days);
    }

    @Transaction
    default void replacePlan(
            List<PlannedSlotEntity> slots,
            List<PlanningConflictEntity> conflicts) {
        deletePlannedSlots();
        deletePlanningConflicts();
        insertPlannedSlots(slots);
        insertPlanningConflicts(conflicts);
    }
}

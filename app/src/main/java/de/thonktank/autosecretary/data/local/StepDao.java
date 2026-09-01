package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface StepDao {
    @Upsert void insertTemplates(List<TaskStepEntity> steps);
    @Update void updateTemplate(TaskStepEntity step);
    @Query("DELETE FROM task_steps WHERE id = :id") void deleteTemplate(String id);
    @Query("DELETE FROM task_steps WHERE taskId = :taskId") void deleteTemplates(String taskId);
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY position")
    List<TaskStepEntity> templates(String taskId);
    @Query("SELECT * FROM task_steps WHERE id = :id LIMIT 1") TaskStepEntity template(String id);
    @Query("SELECT * FROM task_steps WHERE taskId IN (:taskIds) ORDER BY taskId, position")
    List<TaskStepEntity> templatesFor(List<String> taskIds);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOccurrenceSteps(List<OccurrenceStepEntity> steps);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId = :occurrenceId ORDER BY position")
    List<OccurrenceStepEntity> occurrenceSteps(String occurrenceId);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId IN (:occurrenceIds) "
            + "ORDER BY occurrenceId, position")
    List<OccurrenceStepEntity> occurrenceStepsFor(List<String> occurrenceIds);
    @Query("SELECT * FROM occurrence_steps WHERE id = :id LIMIT 1")
    OccurrenceStepEntity occurrenceStep(String id);
    @Update void updateOccurrenceStep(OccurrenceStepEntity step);
    @Query("DELETE FROM occurrence_steps WHERE id = :id") void deleteOccurrenceStep(String id);
    @Query("UPDATE occurrence_steps SET position = :position WHERE id = :stepId")
    int updateOccurrenceStepPosition(String stepId, int position);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putRepetitionResult(RepetitionResultEntity result);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putRepetitionResults(List<RepetitionResultEntity> results);
    @Query("SELECT * FROM repetition_results WHERE stepId = :stepId ORDER BY slotIndex")
    List<RepetitionResultEntity> repetitionResults(String stepId);
    @Query("SELECT * FROM repetition_results WHERE stepId IN (:stepIds) "
            + "ORDER BY stepId, slotIndex")
    List<RepetitionResultEntity> repetitionResultsFor(List<String> stepIds);
    @Query("DELETE FROM repetition_results WHERE stepId = :stepId AND slotIndex >= :fromIndex")
    void deleteRepetitionResultsFrom(String stepId, int fromIndex);
}

package de.thonktank.autosecretary;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertTask(TaskEntity task);
    @Update void updateTask(TaskEntity task);
    @Query("SELECT * FROM tasks WHERE archived = 0 AND conditionDone = 0") List<TaskEntity> activeTasks();
    @Query("SELECT * FROM tasks") List<TaskEntity> allTasks();
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1") TaskEntity task(String id);
    @Query("DELETE FROM tasks WHERE id = :id") void deleteTask(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertTemplates(List<TaskStepEntity> steps);
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY position") List<TaskStepEntity> templates(String taskId);
    @Query("SELECT * FROM task_steps WHERE taskId IN (:taskIds) ORDER BY taskId, position") List<TaskStepEntity> templatesFor(List<String> taskIds);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertOccurrence(OccurrenceEntity occurrence);
    @Update void updateOccurrence(OccurrenceEntity occurrence);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state LIMIT 1") OccurrenceEntity openForTask(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE id = :id LIMIT 1") OccurrenceEntity occurrence(String id);
    @Query("SELECT * FROM occurrences WHERE state = :state") List<OccurrenceEntity> occurrencesByState(String state);
    @Query("SELECT * FROM occurrences WHERE state = :state AND completedOn = :date") List<OccurrenceEntity> completedOccurrences(String state, String date);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertOccurrenceSteps(List<OccurrenceStepEntity> steps);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId = :occurrenceId ORDER BY position") List<OccurrenceStepEntity> occurrenceSteps(String occurrenceId);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId IN (:occurrenceIds) ORDER BY occurrenceId, position") List<OccurrenceStepEntity> occurrenceStepsFor(List<String> occurrenceIds);
    @Query("SELECT * FROM occurrence_steps WHERE id = :id LIMIT 1") OccurrenceStepEntity occurrenceStep(String id);
    @Update void updateOccurrenceStep(OccurrenceStepEntity step);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putStats(StatsEntity stats);
    @Query("SELECT * FROM stats WHERE id = 1") StatsEntity stats();
}

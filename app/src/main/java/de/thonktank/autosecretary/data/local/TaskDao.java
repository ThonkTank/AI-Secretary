package de.thonktank.autosecretary.data.local;

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
    @Query("DELETE FROM task_steps WHERE id = :id") void deleteTemplate(String id);
    @Query("DELETE FROM task_steps WHERE taskId = :taskId") void deleteTemplates(String taskId);
    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY position") List<TaskStepEntity> templates(String taskId);
    @Query("SELECT * FROM task_steps WHERE id = :id LIMIT 1") TaskStepEntity template(String id);
    @Query("SELECT * FROM task_steps WHERE taskId IN (:taskIds) ORDER BY taskId, position") List<TaskStepEntity> templatesFor(List<String> taskIds);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putScheduleEntries(List<TaskScheduleEntity> entries);
    @Query("DELETE FROM task_schedule_entries WHERE id = :id")
    void deleteScheduleEntry(String id);
    @Query("SELECT * FROM task_schedule_entries ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntries();
    @Query("SELECT * FROM task_schedule_entries WHERE taskId = :taskId ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntries(String taskId);
    @Query("SELECT * FROM task_schedule_entries WHERE id = :id LIMIT 1")
    TaskScheduleEntity scheduleEntry(String id);
    @Query("SELECT * FROM task_schedule_entries WHERE slot = :slot ORDER BY displayOrder, id")
    List<TaskScheduleEntity> scheduleEntriesInSlot(String slot);
    @Query("SELECT * FROM task_schedule_entries WHERE taskId IN (:taskIds) ORDER BY slot, displayOrder, id")
    List<TaskScheduleEntity> scheduleEntriesFor(List<String> taskIds);
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insertOccurrence(OccurrenceEntity occurrence);
    @Update void updateOccurrence(OccurrenceEntity occurrence);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state LIMIT 1") OccurrenceEntity openForTask(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND slot = :slot "
            + "AND state = :state ORDER BY scheduledOn LIMIT 1")
    OccurrenceEntity openForTaskSlot(String taskId, String slot, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "ORDER BY scheduledOn, slot")
    List<OccurrenceEntity> openOccurrencesForTask(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE id = :id LIMIT 1") OccurrenceEntity occurrence(String id);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND scheduledOn = :scheduledOn AND slot = :slot LIMIT 1") OccurrenceEntity occurrence(String taskId, String scheduledOn, String slot);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND scheduledOn = :scheduledOn AND state = :state") List<OccurrenceEntity> occurrences(String taskId, String scheduledOn, String state);
    @Query("SELECT * FROM occurrences WHERE state = :state") List<OccurrenceEntity> occurrencesByState(String state);
    @Query("SELECT * FROM occurrences WHERE state = :state AND slot = :slot "
            + "ORDER BY sortOrder, scheduledOn, id")
    List<OccurrenceEntity> occurrencesByStateAndSlot(String state, String slot);
    @Query("SELECT * FROM occurrences") List<OccurrenceEntity> allOccurrences();
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId") List<OccurrenceEntity> occurrencesForTask(String taskId);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "ORDER BY scheduledOn ASC LIMIT 1")
    OccurrenceEntity earliestOccurrence(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state = :state "
            + "ORDER BY completedOn DESC, scheduledOn DESC LIMIT 1")
    OccurrenceEntity latestCompletedOccurrence(String taskId, String state);
    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND state IN (:states) "
            + "ORDER BY completedOn DESC, scheduledOn DESC LIMIT 1")
    OccurrenceEntity latestCompletedOccurrence(String taskId, List<String> states);
    @Query("SELECT * FROM occurrences WHERE state = :state AND completedOn = :date") List<OccurrenceEntity> completedOccurrences(String state, String date);
    @Query("SELECT * FROM occurrences WHERE state IN (:states) AND completedOn = :date")
    List<OccurrenceEntity> completedOccurrences(List<String> states, String date);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertOccurrenceSteps(List<OccurrenceStepEntity> steps);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId = :occurrenceId ORDER BY position") List<OccurrenceStepEntity> occurrenceSteps(String occurrenceId);
    @Query("SELECT * FROM occurrence_steps WHERE occurrenceId IN (:occurrenceIds) ORDER BY occurrenceId, position") List<OccurrenceStepEntity> occurrenceStepsFor(List<String> occurrenceIds);
    @Query("SELECT * FROM occurrence_steps WHERE id = :id LIMIT 1") OccurrenceStepEntity occurrenceStep(String id);
    @Update void updateOccurrenceStep(OccurrenceStepEntity step);
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
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putStats(StatsEntity stats);
    @Query("SELECT * FROM stats WHERE id = 1") StatsEntity stats();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putCombo(ComboEntity combo);
    @Query("SELECT * FROM combo_progress WHERE ownerId = :ownerId LIMIT 1") ComboEntity combo(String ownerId);
    @Query("SELECT * FROM combo_progress") List<ComboEntity> allCombos();
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertRewardBooking(RewardBookingEntity booking);
    @Query("SELECT id FROM reward_bookings WHERE occurrenceStepId = :occurrenceStepId")
    List<String> rewardBookingIds(String occurrenceStepId);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putRewardAssignments(List<RewardAssignmentEntity> assignments);
    @Query("SELECT * FROM reward_bookings WHERE id = :id LIMIT 1")
    RewardBookingEntity ledgerRewardBooking(String id);
    @Query("SELECT rb.id,rb.transactionId,"
            + "COALESCE(ra.occurrenceId,rb.occurrenceId) AS occurrenceId,"
            + "rb.occurrenceStepId,rb.ownerId,rb.kind,rb.target,rb.xpDelta,"
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId FROM reward_bookings rb "
            + "LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId)=:occurrenceId "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(String occurrenceId);
    @Query("SELECT rb.id,rb.transactionId,"
            + "COALESCE(ra.occurrenceId,rb.occurrenceId) AS occurrenceId,"
            + "rb.occurrenceStepId,rb.ownerId,rb.kind,rb.target,rb.xpDelta,"
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId FROM reward_bookings rb "
            + "LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId) IN (:occurrenceIds) "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(List<String> occurrenceIds);
}

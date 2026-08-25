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
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId,rb.plannedXp FROM reward_bookings rb "
            + "LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId)=:occurrenceId "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(String occurrenceId);
    @Query("SELECT rb.id,rb.transactionId,"
            + "COALESCE(ra.occurrenceId,rb.occurrenceId) AS occurrenceId,"
            + "rb.occurrenceStepId,rb.ownerId,rb.kind,rb.target,rb.xpDelta,"
            + "rb.comboPointDelta,rb.bookedOn,rb.reversesBookingId,rb.plannedXp FROM reward_bookings rb "
            + "LEFT JOIN reward_assignments ra ON ra.bookingId=rb.id "
            + "WHERE COALESCE(ra.occurrenceId,rb.occurrenceId) IN (:occurrenceIds) "
            + "ORDER BY rb.bookedOn,rb.id")
    List<RewardBookingEntity> rewardBookings(List<String> occurrenceIds);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertComboObligations(List<ComboObligationEntity> obligations);
    @Update void updateComboObligation(ComboObligationEntity obligation);
    @Query("SELECT * FROM combo_obligations ORDER BY scheduledOn,slot,ownerId")
    List<ComboObligationEntity> comboObligations();
    @Query("SELECT * FROM combo_decay_events WHERE ownerId = :ownerId "
            + "AND eventOn = :eventOn LIMIT 1")
    ComboDecayEventEntity comboDecayEvent(String ownerId, String eventOn);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertComboDecayEvent(ComboDecayEventEntity event);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertCapacityResource(CapacityResourceEntity resource);
    @Update void updateCapacityResource(CapacityResourceEntity resource);
    @Query("SELECT * FROM capacity_resources ORDER BY normalizedName, id")
    List<CapacityResourceEntity> capacityResources();
    @Query("SELECT * FROM capacity_resources WHERE id = :id LIMIT 1")
    CapacityResourceEntity capacityResource(String id);
    @Query("DELETE FROM capacity_resources WHERE id = :id")
    void deleteCapacityResource(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepTransitions(List<StepTransitionEntity> transitions);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepTransition(StepTransitionEntity transition);
    @Query("SELECT step_transitions.* FROM step_transitions JOIN task_steps "
            + "ON task_steps.id = step_transitions.sourceStepId "
            + "WHERE task_steps.taskId = :taskId ORDER BY task_steps.position")
    List<StepTransitionEntity> stepTransitions(String taskId);
    @Query("DELETE FROM step_transitions WHERE sourceStepId IN "
            + "(SELECT id FROM task_steps WHERE taskId = :taskId)")
    void deleteStepTransitions(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepResourceLeases(List<StepResourceLeaseEntity> leases);
    @Query("SELECT * FROM step_resource_leases WHERE taskId = :taskId ORDER BY id")
    List<StepResourceLeaseEntity> stepResourceLeases(String taskId);
    @Query("DELETE FROM step_resource_leases WHERE taskId = :taskId")
    void deleteStepResourceLeases(String taskId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertStepFlowRun(StepFlowRunEntity run);
    @Update void updateStepFlowRun(StepFlowRunEntity run);
    @Query("SELECT * FROM step_flow_runs WHERE id = :id LIMIT 1")
    StepFlowRunEntity stepFlowRun(String id);
    @Query("SELECT * FROM step_flow_runs WHERE sourceKey = :sourceKey LIMIT 1")
    StepFlowRunEntity stepFlowRunBySourceKey(String sourceKey);
    @Query("SELECT * FROM step_flow_runs WHERE state NOT IN ('COMPLETED','CANCELLED') "
            + "ORDER BY queueOrder, createdAtEpochMillis, id")
    List<StepFlowRunEntity> activeStepFlowRuns();
    @Query("SELECT * FROM step_flow_runs WHERE taskId = :taskId "
            + "AND state NOT IN ('COMPLETED','CANCELLED') "
            + "ORDER BY queueOrder, createdAtEpochMillis, id")
    List<StepFlowRunEntity> activeStepFlowRuns(String taskId);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertFlowRunSteps(List<FlowRunStepEntity> steps);
    @Query("SELECT * FROM flow_run_steps WHERE runId = :runId ORDER BY position")
    List<FlowRunStepEntity> flowRunSteps(String runId);
    @Update void updateFlowRunStep(FlowRunStepEntity step);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertFlowRunResources(List<FlowRunResourceEntity> resources);
    @Query("SELECT * FROM flow_run_resources WHERE runId = :runId "
            + "ORDER BY acquirePosition, releasePosition, id")
    List<FlowRunResourceEntity> flowRunResources(String runId);
    @Query("SELECT * FROM flow_run_resources WHERE state IN ('RESERVED','ACTIVE') "
            + "ORDER BY resourceId, id")
    List<FlowRunResourceEntity> consumingFlowResources();
    @Update void updateFlowRunResource(FlowRunResourceEntity resource);
}

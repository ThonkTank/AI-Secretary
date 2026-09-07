package de.thonktank.autosecretary.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FlowDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertCapacityResource(CapacityResourceEntity resource);
    @Update void updateCapacityResource(CapacityResourceEntity resource);
    @Query("SELECT * FROM capacity_resources ORDER BY normalizedName, id")
    List<CapacityResourceEntity> capacityResources();
    @Query("SELECT * FROM capacity_resources WHERE id = :id LIMIT 1")
    CapacityResourceEntity capacityResource(String id);
    @Query("DELETE FROM capacity_resources WHERE id = :id") void deleteCapacityResource(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepTransitions(List<StepTransitionEntity> transitions);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepTransition(StepTransitionEntity transition);
    @Query("SELECT step_transitions.* FROM step_transitions JOIN task_steps "
            + "ON task_steps.id = step_transitions.sourceStepId "
            + "WHERE task_steps.taskId = :taskId ORDER BY task_steps.position")
    List<StepTransitionEntity> stepTransitions(String taskId);
    @Query("SELECT step_transitions.* FROM step_transitions JOIN task_steps "
            + "ON task_steps.id = step_transitions.sourceStepId "
            + "WHERE task_steps.taskId IN (:taskIds) ORDER BY task_steps.taskId, task_steps.position")
    List<StepTransitionEntity> stepTransitionsFor(List<String> taskIds);
    @Query("DELETE FROM step_transitions WHERE sourceStepId IN "
            + "(SELECT id FROM task_steps WHERE taskId = :taskId)")
    void deleteStepTransitions(String taskId);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putStepResourceLeases(List<StepResourceLeaseEntity> leases);
    @Query("SELECT * FROM step_resource_leases WHERE taskId = :taskId ORDER BY id")
    List<StepResourceLeaseEntity> stepResourceLeases(String taskId);
    @Query("SELECT * FROM step_resource_leases WHERE taskId IN (:taskIds) "
            + "ORDER BY taskId, id")
    List<StepResourceLeaseEntity> stepResourceLeasesFor(List<String> taskIds);
    @Query("DELETE FROM step_resource_leases WHERE taskId = :taskId")
    void deleteStepResourceLeases(String taskId);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertFlowCandidate(FlowCandidateEntity candidate);
    @Query("SELECT * FROM flow_candidates WHERE id = :id LIMIT 1")
    FlowCandidateEntity flowCandidate(String id);
    @Query("SELECT * FROM flow_candidates WHERE sourceKey = :sourceKey LIMIT 1")
    FlowCandidateEntity flowCandidateBySourceKey(String sourceKey);
    @Query("SELECT * FROM flow_candidates ORDER BY queueOrder, createdAtEpochMillis, id")
    List<FlowCandidateEntity> flowCandidates();
    @Query("SELECT * FROM flow_candidates WHERE taskId = :taskId "
            + "ORDER BY queueOrder, createdAtEpochMillis, id")
    List<FlowCandidateEntity> flowCandidates(String taskId);
    @Query("DELETE FROM flow_candidates WHERE id = :id") void deleteFlowCandidate(String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putFlowTaskSheetPlacement(FlowTaskSheetPlacementEntity placement);
    @Query("SELECT * FROM flow_task_sheet_placements WHERE taskId = :taskId AND slot = :slot "
            + "LIMIT 1")
    FlowTaskSheetPlacementEntity flowTaskSheetPlacement(String taskId, String slot);
    @Query("SELECT * FROM flow_task_sheet_placements ORDER BY slot, displayOn, sortOrder, id")
    List<FlowTaskSheetPlacementEntity> flowTaskSheetPlacements();
    @Insert(onConflict = OnConflictStrategy.IGNORE) long insertStepFlowRun(StepFlowRunEntity run);
    @Update void updateStepFlowRun(StepFlowRunEntity run);
    @Query("SELECT * FROM step_flow_runs WHERE id = :id LIMIT 1") StepFlowRunEntity stepFlowRun(String id);
    @Query("SELECT * FROM step_flow_runs WHERE sourceKey = :sourceKey LIMIT 1")
    StepFlowRunEntity stepFlowRunBySourceKey(String sourceKey);
    @Query("SELECT * FROM step_flow_runs WHERE state NOT IN ('COMPLETED','CANCELLED') "
            + "ORDER BY queueOrder, createdAtEpochMillis, id")
    List<StepFlowRunEntity> activeStepFlowRuns();
    @Query("SELECT * FROM step_flow_runs WHERE taskId = :taskId "
            + "AND state NOT IN ('COMPLETED','CANCELLED') "
            + "ORDER BY queueOrder, createdAtEpochMillis, id")
    List<StepFlowRunEntity> activeStepFlowRuns(String taskId);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertFlowRunSteps(List<FlowRunStepEntity> steps);
    @Query("SELECT * FROM flow_run_steps WHERE runId = :runId ORDER BY position")
    List<FlowRunStepEntity> flowRunSteps(String runId);
    @Query("SELECT * FROM flow_run_steps WHERE runId IN (:runIds) ORDER BY runId, position")
    List<FlowRunStepEntity> flowRunStepsFor(List<String> runIds);
    @Update void updateFlowRunStep(FlowRunStepEntity step);
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertFlowRunResources(List<FlowRunResourceEntity> resources);
    @Query("SELECT * FROM flow_run_resources WHERE runId = :runId "
            + "ORDER BY acquirePosition, releasePosition, id")
    List<FlowRunResourceEntity> flowRunResources(String runId);
    @Query("SELECT * FROM flow_run_resources WHERE runId IN (:runIds) "
            + "ORDER BY runId, acquirePosition, releasePosition, id")
    List<FlowRunResourceEntity> flowRunResourcesFor(List<String> runIds);
    @Query("SELECT * FROM flow_run_resources WHERE state IN ('RESERVED','ACTIVE') "
            + "ORDER BY resourceId, id")
    List<FlowRunResourceEntity> consumingFlowResources();
    @Update void updateFlowRunResource(FlowRunResourceEntity resource);
}

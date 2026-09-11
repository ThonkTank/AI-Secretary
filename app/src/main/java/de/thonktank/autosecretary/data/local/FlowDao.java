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
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putDefinitionEdge(FlowDefinitionEdgeEntity edge);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putStepWait(FlowStepWaitEntity wait);
    default void putStepTransitions(List<StepTransitionEntity> transitions) {
        for (StepTransitionEntity transition : transitions) putStepTransition(transition);
    }
    default void putStepTransition(StepTransitionEntity transition) {
        FlowDefinitionEdgeEntity edge = new FlowDefinitionEdgeEntity();
        edge.sourceStepId = transition.sourceStepId; edge.targetStepId = transition.targetStepId;
        FlowStepWaitEntity wait = new FlowStepWaitEntity();
        wait.stepId = transition.sourceStepId; wait.mode = transition.delayMode;
        wait.defaultDelayMillis = transition.defaultDelayMillis; wait.lastUsedDelayMillis = transition.lastUsedDelayMillis;
        putDefinitionEdge(edge); putStepWait(wait);
    }
    @Query("SELECT step_transitions.*, w.mode AS delayMode, w.defaultDelayMillis, w.lastUsedDelayMillis FROM step_transitions JOIN flow_step_waits w ON w.stepId=step_transitions.sourceStepId JOIN task_steps "
            + "ON task_steps.id = step_transitions.sourceStepId "
            + "WHERE task_steps.taskId = :taskId ORDER BY task_steps.position")
    List<StepTransitionEntity> stepTransitions(String taskId);
    @Query("SELECT step_transitions.*, w.mode AS delayMode, w.defaultDelayMillis, w.lastUsedDelayMillis FROM step_transitions JOIN flow_step_waits w ON w.stepId=step_transitions.sourceStepId JOIN task_steps "
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
    // Schema 24 operations remain explicit failures while old callers are removed in the cutover.
    default long insertStepFlowRun(StepFlowRunEntity run) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default void updateStepFlowRun(StepFlowRunEntity run) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default StepFlowRunEntity stepFlowRun(String id) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default StepFlowRunEntity stepFlowRunBySourceKey(String sourceKey) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<StepFlowRunEntity> activeStepFlowRuns() { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<StepFlowRunEntity> activeStepFlowRuns(String taskId) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default void insertFlowRunSteps(List<FlowRunStepEntity> steps) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<FlowRunStepEntity> flowRunSteps(String runId) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<FlowRunStepEntity> flowRunStepsFor(List<String> runIds) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default void updateFlowRunStep(FlowRunStepEntity step) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default void insertFlowRunResources(List<FlowRunResourceEntity> resources) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<FlowRunResourceEntity> flowRunResources(String runId) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<FlowRunResourceEntity> flowRunResourcesFor(List<String> runIds) { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default List<FlowRunResourceEntity> consumingFlowResources() { throw new UnsupportedOperationException("Use graph runtime repository"); }
    default void updateFlowRunResource(FlowRunResourceEntity resource) { throw new UnsupportedOperationException("Use graph runtime repository"); }
}

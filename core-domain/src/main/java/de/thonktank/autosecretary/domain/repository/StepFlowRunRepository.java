package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.TaskId;

import java.util.List;
import java.util.Collections;

/** Runtime-side storage for durable flow cursors and their immutable definition snapshots. */
public interface StepFlowRunRepository extends TransactionalRepository {
    default boolean insertFlowRun(FlowRunSnapshot snapshot) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default void updateFlowRun(StepFlowRun run) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default StepFlowRun findFlowRun(String id) { return null; }
    default StepFlowRun findFlowRunBySourceKey(String sourceKey) { return null; }
    default List<StepFlowRun> activeFlowRuns() { return Collections.emptyList(); }
    default List<StepFlowRun> activeFlowRuns(TaskId taskId) { return Collections.emptyList(); }
    default List<FlowRunStepSnapshot> flowRunSteps(String runId) {
        return Collections.emptyList();
    }
    default void updateFlowRunStep(FlowRunStepSnapshot step) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
    default List<FlowRunResourceSnapshot> flowRunResources(String runId) {
        return Collections.emptyList();
    }
    default List<FlowRunResourceSnapshot> consumingFlowResources() {
        return Collections.emptyList();
    }
    default void updateFlowRunResource(FlowRunResourceSnapshot resource) {
        throw new UnsupportedOperationException("Step flows are not supported by this store");
    }
}

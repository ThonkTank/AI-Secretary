package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;

import java.util.List;

/** Flow definitions, runs, transitions and capacity resources. */
public interface FlowRepository {
    List<CapacityResource> capacityResources();
    CapacityResource findCapacityResource(String id);
    void putCapacityResource(CapacityResource resource);
    void deleteCapacityResource(String id);
    List<StepTransition> stepTransitions(TaskId taskId);
    List<StepResourceLease> stepResourceLeases(TaskId taskId);
    void replaceStepFlow(TaskId taskId, List<StepTransition> transitions,
                         List<StepResourceLease> leases);
    void updateStepTransition(StepTransition transition);
    boolean insertFlowRun(FlowRunSnapshot snapshot);
    void updateFlowRun(StepFlowRun run);
    StepFlowRun findFlowRun(String id);
    StepFlowRun findFlowRunBySourceKey(String sourceKey);
    List<StepFlowRun> activeFlowRuns();
    List<StepFlowRun> activeFlowRuns(TaskId taskId);
    List<FlowRunStepSnapshot> flowRunSteps(String runId);
    List<FlowRunStepSnapshot> flowRunStepsFor(List<String> runIds);
    void updateFlowRunStep(FlowRunStepSnapshot step);
    List<FlowRunResourceSnapshot> flowRunResources(String runId);
    List<FlowRunResourceSnapshot> flowRunResourcesFor(List<String> runIds);
    List<FlowRunResourceSnapshot> consumingFlowResources();
    void updateFlowRunResource(FlowRunResourceSnapshot resource);
}

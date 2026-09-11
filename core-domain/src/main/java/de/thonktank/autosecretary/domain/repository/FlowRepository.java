package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.FlowCandidate;
import de.thonktank.autosecretary.domain.model.FlowTaskSheetPlacement;
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
    List<StepTransition> stepTransitionsFor(List<TaskId> taskIds);
    List<StepResourceLease> stepResourceLeases(TaskId taskId);
    List<StepResourceLease> stepResourceLeasesFor(List<TaskId> taskIds);
    void replaceStepFlow(TaskId taskId, List<StepTransition> transitions,
                         List<StepResourceLease> leases);
    void updateStepTransition(StepTransition transition);
    boolean insertFlowCandidate(FlowCandidate candidate);
    FlowCandidate findFlowCandidate(String id);
    FlowCandidate findFlowCandidateBySourceKey(String sourceKey);
    List<FlowCandidate> flowCandidates();
    List<FlowCandidate> flowCandidates(TaskId taskId);
    void deleteFlowCandidate(String id);
    void putFlowTaskSheetPlacement(FlowTaskSheetPlacement placement);
    FlowTaskSheetPlacement findFlowTaskSheetPlacement(TaskId taskId,
                                                      de.thonktank.autosecretary.domain.model.TaskSlot slot);
    List<FlowTaskSheetPlacement> flowTaskSheetPlacements();
}

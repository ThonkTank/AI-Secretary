package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
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
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.FlowRepository;

import java.util.ArrayList;
import java.util.List;

/** Room adapter for flow definitions, runs, transitions and resources. */
public final class RoomFlowRepository implements FlowRepository {
    private final FlowDao dao;
    private final StepFlowEntityMapper mapper = new StepFlowEntityMapper();

    public RoomFlowRepository(AppDatabase database) {
        this.dao = database.flows();
    }
    @Override public List<CapacityResource> capacityResources() {
        List<CapacityResource> result = new ArrayList<>();
        for (CapacityResourceEntity value : dao.capacityResources()) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public CapacityResource findCapacityResource(String id) {
        CapacityResourceEntity value = dao.capacityResource(id);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public void putCapacityResource(CapacityResource value) {
        CapacityResourceEntity entity = mapper.toEntity(value);
        if (dao.insertCapacityResource(entity) == -1L) dao.updateCapacityResource(entity);
    }
    @Override public void deleteCapacityResource(String id) { dao.deleteCapacityResource(id); }
    @Override public List<StepTransition> stepTransitions(TaskId taskId) {
        List<StepTransition> result = new ArrayList<>();
        for (StepTransitionEntity value : dao.stepTransitions(taskId.value)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<StepTransition> stepTransitionsFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId taskId : taskIds) values.add(taskId.value);
        List<StepTransition> result = new ArrayList<>();
        for (StepTransitionEntity value : dao.stepTransitionsFor(values))
            result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<StepResourceLease> stepResourceLeases(TaskId taskId) {
        List<StepResourceLease> result = new ArrayList<>();
        for (StepResourceLeaseEntity value : dao.stepResourceLeases(taskId.value)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<StepResourceLease> stepResourceLeasesFor(List<TaskId> taskIds) {
        if (taskIds.isEmpty()) return new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TaskId taskId : taskIds) values.add(taskId.value);
        List<StepResourceLease> result = new ArrayList<>();
        for (StepResourceLeaseEntity value : dao.stepResourceLeasesFor(values))
            result.add(mapper.toDomain(value));
        return result;
    }
    @Override public void replaceStepFlow(TaskId taskId, List<StepTransition> transitions,
                                          List<StepResourceLease> leases) {
        dao.deleteStepResourceLeases(taskId.value);
        dao.deleteStepTransitions(taskId.value);
        List<StepTransitionEntity> transitionEntities = new ArrayList<>();
        for (StepTransition value : transitions) transitionEntities.add(mapper.toEntity(value));
        if (!transitionEntities.isEmpty()) dao.putStepTransitions(transitionEntities);
        List<StepResourceLeaseEntity> leaseEntities = new ArrayList<>();
        for (StepResourceLease value : leases) leaseEntities.add(mapper.toEntity(value));
        if (!leaseEntities.isEmpty()) dao.putStepResourceLeases(leaseEntities);
    }
    @Override public void updateStepTransition(StepTransition value) {
        dao.putStepTransition(mapper.toEntity(value));
    }
    @Override public boolean insertFlowCandidate(FlowCandidate value) {
        return dao.insertFlowCandidate(mapper.toEntity(value)) != -1L;
    }
    @Override public FlowCandidate findFlowCandidate(String id) {
        FlowCandidateEntity value = dao.flowCandidate(id);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public FlowCandidate findFlowCandidateBySourceKey(String sourceKey) {
        FlowCandidateEntity value = dao.flowCandidateBySourceKey(sourceKey);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<FlowCandidate> flowCandidates() {
        return mapCandidates(dao.flowCandidates());
    }
    @Override public List<FlowCandidate> flowCandidates(TaskId taskId) {
        return mapCandidates(dao.flowCandidates(taskId.value));
    }
    @Override public void deleteFlowCandidate(String id) { dao.deleteFlowCandidate(id); }
    @Override public void putFlowTaskSheetPlacement(FlowTaskSheetPlacement value) {
        dao.putFlowTaskSheetPlacement(mapper.toEntity(value));
    }
    @Override public FlowTaskSheetPlacement findFlowTaskSheetPlacement(TaskId taskId,
                                                                       TaskSlot slot) {
        FlowTaskSheetPlacementEntity value = dao.flowTaskSheetPlacement(
                taskId.value, slot.storageCode);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<FlowTaskSheetPlacement> flowTaskSheetPlacements() {
        List<FlowTaskSheetPlacement> result = new ArrayList<>();
        for (FlowTaskSheetPlacementEntity value : dao.flowTaskSheetPlacements())
            result.add(mapper.toDomain(value));
        return result;
    }
    private List<FlowCandidate> mapCandidates(List<FlowCandidateEntity> values) {
        List<FlowCandidate> result = new ArrayList<>();
        for (FlowCandidateEntity value : values) result.add(mapper.toDomain(value));
        return result;
    }
}

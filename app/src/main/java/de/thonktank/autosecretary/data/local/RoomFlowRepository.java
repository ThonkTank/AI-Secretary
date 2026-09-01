package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.FlowRepository;

import java.util.ArrayList;
import java.util.List;

/** Room adapter for flow definitions, runs, transitions and resources. */
public final class RoomFlowRepository implements FlowRepository {
    private final AppDatabase database;
    private final FlowDao dao;
    private final StepFlowEntityMapper mapper = new StepFlowEntityMapper();

    public RoomFlowRepository(AppDatabase database) {
        this.database = database;
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
    @Override public List<StepResourceLease> stepResourceLeases(TaskId taskId) {
        List<StepResourceLease> result = new ArrayList<>();
        for (StepResourceLeaseEntity value : dao.stepResourceLeases(taskId.value)) result.add(mapper.toDomain(value));
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
    @Override public boolean insertFlowRun(FlowRunSnapshot snapshot) {
        return database.runInTransaction(() -> {
            if (dao.insertStepFlowRun(mapper.toEntity(snapshot.run)) == -1L) return false;
            List<FlowRunStepEntity> steps = new ArrayList<>();
            for (FlowRunStepSnapshot value : snapshot.steps) steps.add(mapper.toEntity(value));
            dao.insertFlowRunSteps(steps);
            List<FlowRunResourceEntity> resources = new ArrayList<>();
            for (FlowRunResourceSnapshot value : snapshot.resources) resources.add(mapper.toEntity(value));
            if (!resources.isEmpty()) dao.insertFlowRunResources(resources);
            return true;
        });
    }
    @Override public void updateFlowRun(StepFlowRun value) { dao.updateStepFlowRun(mapper.toEntity(value)); }
    @Override public StepFlowRun findFlowRun(String id) {
        StepFlowRunEntity value = dao.stepFlowRun(id);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public StepFlowRun findFlowRunBySourceKey(String sourceKey) {
        StepFlowRunEntity value = dao.stepFlowRunBySourceKey(sourceKey);
        return value == null ? null : mapper.toDomain(value);
    }
    @Override public List<StepFlowRun> activeFlowRuns() { return mapRuns(dao.activeStepFlowRuns()); }
    @Override public List<StepFlowRun> activeFlowRuns(TaskId taskId) {
        return mapRuns(dao.activeStepFlowRuns(taskId.value));
    }
    @Override public List<FlowRunStepSnapshot> flowRunSteps(String runId) {
        List<FlowRunStepSnapshot> result = new ArrayList<>();
        for (FlowRunStepEntity value : dao.flowRunSteps(runId)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<FlowRunStepSnapshot> flowRunStepsFor(List<String> runIds) {
        if (runIds.isEmpty()) return new ArrayList<>();
        List<FlowRunStepSnapshot> result = new ArrayList<>();
        for (FlowRunStepEntity value : dao.flowRunStepsFor(runIds)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public void updateFlowRunStep(FlowRunStepSnapshot value) {
        dao.updateFlowRunStep(mapper.toEntity(value));
    }
    @Override public List<FlowRunResourceSnapshot> flowRunResources(String runId) {
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.flowRunResources(runId)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<FlowRunResourceSnapshot> flowRunResourcesFor(List<String> runIds) {
        if (runIds.isEmpty()) return new ArrayList<>();
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.flowRunResourcesFor(runIds)) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public List<FlowRunResourceSnapshot> consumingFlowResources() {
        List<FlowRunResourceSnapshot> result = new ArrayList<>();
        for (FlowRunResourceEntity value : dao.consumingFlowResources()) result.add(mapper.toDomain(value));
        return result;
    }
    @Override public void updateFlowRunResource(FlowRunResourceSnapshot value) {
        dao.updateFlowRunResource(mapper.toEntity(value));
    }
    private List<StepFlowRun> mapRuns(List<StepFlowRunEntity> values) {
        List<StepFlowRun> result = new ArrayList<>();
        for (StepFlowRunEntity value : values) result.add(mapper.toDomain(value));
        return result;
    }
}

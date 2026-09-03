package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves a definition into an edit-proof linear runtime snapshot. */
public final class CreateFlowRunSnapshot {
    private final IdGenerator ids;
    private final StepSnapshotFactory snapshots;

    public CreateFlowRunSnapshot(IdGenerator ids) {
        this.ids = ids;
        snapshots = new StepSnapshotFactory(ids);
    }

    public FlowRunSnapshot execute(StepFlowDefinition definition, String seedStepId,
                                   String sourceKey, LocalDate scheduledOn, TaskSlot slot,
                                   long queueOrder, long nowEpochMillis) {
        List<TaskStepTemplate> path = definition.resolvedPath(seedStepId);
        String runId = ids.nextId();
        StepFlowRun run = new StepFlowRun(runId, definition.taskId, seedStepId, sourceKey,
                scheduledOn, slot, StepFlowRunState.WAITING_RESOURCE, 0, null, null,
                queueOrder, 0, nowEpochMillis, nowEpochMillis);
        List<FlowRunStepSnapshot> steps = new ArrayList<>();
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < path.size(); index++) {
            TaskStepTemplate template = path.get(index);
            positions.put(template.id, index);
            StepTransition transition = definition.transitionAfter(template.id);
            FlowDelayPolicy delay = transition == null ? null : transition.delay;
            steps.add(snapshots.flowRun(template, runId, index, delay));
        }
        Map<String, CapacityResource> resourceById = new HashMap<>();
        for (CapacityResource resource : definition.resources)
            resourceById.put(resource.id, resource);
        List<FlowRunResourceSnapshot> resources = new ArrayList<>();
        for (StepResourceLease lease : definition.leasesForPath(path)) {
            CapacityResource resource = resourceById.get(lease.resourceId);
            resources.add(new FlowRunResourceSnapshot(ids.nextId(), runId, lease.id,
                    resource.id, resource.name, resource.capacity, lease.units,
                    positions.get(lease.acquireStepId), positions.get(lease.releaseStepId),
                    FlowResourceState.PLANNED, null, null, null));
        }
        return new FlowRunSnapshot(run, steps, resources);
    }
}

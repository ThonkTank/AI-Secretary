package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowCandidate;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.FlowTaskSheetPlacement;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Atomic admission boundary: no run exists before this command succeeds. */
public final class StartFlowCandidate {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flows;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final MomentSource moments;
    private final IdGenerator ids;
    private final ToggleStep toggle;
    private final GraphFlowRuntime graph;

    public StartFlowCandidate(CatalogRepository catalog, StepRepository steps,
                              TodayRepository today, FlowRepository flows,
                              TransactionRunner transactions, Clock clock,
                              MomentSource moments, IdGenerator ids, ToggleStep toggle) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.flows = flows;
        this.transactions = transactions;
        this.clock = clock;
        this.moments = moments;
        this.ids = ids;
        this.toggle = toggle;
        this.graph = null;
    }

    public StartFlowCandidate(GraphFlowRuntime graph) {
        this.catalog = null; this.steps = null; this.today = null; this.flows = null;
        this.transactions = null; this.clock = null; this.moments = null; this.ids = null;
        this.toggle = null; this.graph = graph;
    }

    public StartFlowCandidateResult execute(String candidateId, Long chosenDelayMillis) {
        if (graph != null) {
            GraphFlowRuntime.Result result = graph.start(candidateId, chosenDelayMillis);
            switch (result.status) {
                case CHANGED: return StartFlowCandidateResult.started(result.runId, result.reward);
                case DURATION_REQUIRED: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.DURATION_REQUIRED);
                case CAPACITY_UNAVAILABLE: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.CAPACITY_CHANGED);
                case NOT_FOUND: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.NOT_FOUND);
                default: return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.STALE_CANDIDATE);
            }
        }
        return transactions.inTransaction(() -> start(candidateId, chosenDelayMillis));
    }

    private StartFlowCandidateResult start(String candidateId, Long chosenDelayMillis) {
        FlowCandidate candidate = flows.findFlowCandidate(candidateId);
        if (candidate == null)
            return StartFlowCandidateResult.of(StartFlowCandidateResult.Status.NOT_FOUND);
        Task task = catalog.findTask(candidate.taskId);
        if (task == null || task.archived || task.conditionDone)
            return StartFlowCandidateResult.of(
                    StartFlowCandidateResult.Status.STALE_CANDIDATE);
        List<TaskStepTemplate> templates = steps.templates(candidate.taskId);
        StepFlowDefinition definition;
        try {
            definition = new StepFlowDefinition(candidate.taskId, templates,
                    flows.stepTransitions(candidate.taskId),
                    flows.stepResourceLeases(candidate.taskId), flows.capacityResources());
            if (!definition.participates(candidate.seedStepId))
                return StartFlowCandidateResult.of(
                        StartFlowCandidateResult.Status.STALE_CANDIDATE);
        } catch (IllegalArgumentException invalid) {
            return StartFlowCandidateResult.of(
                    StartFlowCandidateResult.Status.STALE_CANDIDATE);
        }

        long now = moments.nowEpochMillis();
        String occurrenceId = ids.nextId();
        FlowRunSnapshot unresolved = new CreateFlowRunSnapshot(ids).execute(definition,
                candidate.seedStepId, candidate.sourceKey, candidate.scheduledOn,
                candidate.slot, candidate.queueOrder, occurrenceId, now);
        if (!hasStartCapacity(unresolved.resources))
            return StartFlowCandidateResult.of(
                    StartFlowCandidateResult.Status.CAPACITY_CHANGED);

        List<FlowRunResourceSnapshot> resources = new ArrayList<>();
        for (FlowRunResourceSnapshot resource : unresolved.resources)
            resources.add(resource.acquirePosition == 0
                    ? withState(resource, FlowResourceState.RESERVED, now) : resource);
        FlowRunSnapshot snapshot = new FlowRunSnapshot(unresolved.run, unresolved.steps, resources);
        if (!flows.insertFlowRun(snapshot))
            return StartFlowCandidateResult.of(
                    StartFlowCandidateResult.Status.STALE_CANDIDATE);

        FlowTaskSheetPlacement placement = flows.findFlowTaskSheetPlacement(
                candidate.taskId, candidate.slot);
        int order = placement == null ? (int) Math.max(0L,
                Math.min(Integer.MAX_VALUE, candidate.queueOrder / 1_000_000_000L))
                : placement.sortOrder;
        if (placement == null) flows.putFlowTaskSheetPlacement(new FlowTaskSheetPlacement(
                FlowTaskSheetPlacement.stableId(candidate.taskId, candidate.slot),
                candidate.taskId, candidate.slot, clock.today(), order));
        Occurrence occurrence = Occurrence.flowStep(occurrenceId, candidate.taskId,
                clock.today(), candidate.slot, order, snapshot.run.id, 0);
        today.insertOccurrence(occurrence);
        FlowRunStepSnapshot seed = snapshot.steps.get(0);
        OccurrenceStep step = new StepSnapshotFactory(ids).fromFlow(seed, occurrence.id, 0);
        steps.insertOccurrenceSteps(Collections.singletonList(step));
        toggle.execute(step.id, chosenDelayMillis);
        OccurrenceStep completed = steps.findOccurrenceStep(step.id);
        if (completed == null || !completed.done)
            throw new IllegalStateException("Atomic flow start did not complete its seed step");
        flows.deleteFlowCandidate(candidate.id);
        return StartFlowCandidateResult.started(snapshot.run.id);
    }

    private boolean hasStartCapacity(List<FlowRunResourceSnapshot> resources) {
        Map<String, Integer> required = new HashMap<>();
        for (FlowRunResourceSnapshot resource : resources)
            if (resource.acquirePosition == 0)
                required.put(resource.resourceId,
                        required.getOrDefault(resource.resourceId, 0) + resource.units);
        Map<String, Integer> used = new HashMap<>();
        for (FlowRunResourceSnapshot resource : flows.consumingFlowResources())
            used.put(resource.resourceId,
                    used.getOrDefault(resource.resourceId, 0) + resource.units);
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            CapacityResource capacity = flows.findCapacityResource(entry.getKey());
            if (capacity == null || used.getOrDefault(entry.getKey(), 0) + entry.getValue()
                    > capacity.capacity) return false;
        }
        return true;
    }

    private static FlowRunResourceSnapshot withState(FlowRunResourceSnapshot resource,
                                                       FlowResourceState state, long now) {
        return new FlowRunResourceSnapshot(resource.id, resource.runId, resource.sourceLeaseId,
                resource.resourceId, resource.resourceName, resource.capacityAtCreation,
                resource.units, resource.acquirePosition, resource.releasePosition, state,
                now, null, null);
    }
}

package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable graph execution. Layout positions are deliberately absent from runtime state. */
public final class FlowGraphRun {
    public enum State { BLOCKED, WAITING_RESOURCE, AVAILABLE, WAITING_TIME, DONE }

    public final String id;
    public final TaskId taskId;
    public final String startStepId;
    public final FlowTileGraph graph;
    public final Map<String, Step> steps;
    public final List<Lease> leases;
    /** Step-Tau principal already harvested; historical HEAD multipliers remain in the ledger. */
    public final long alreadyPaidTau;
    public final boolean collected;
    /** Restored for historical cancelled runs; the tile editor adds no cancellation command. */
    public final boolean cancelled;
    private final long totalEarnedTau;

    public FlowGraphRun(String id, TaskId taskId, String startStepId, FlowTileGraph graph,
                        List<Step> steps, List<Lease> leases, long alreadyPaidTau,
                        boolean collected) {
        this(id, taskId, startStepId, graph, steps, leases, alreadyPaidTau, collected, false);
    }

    public FlowGraphRun(String id, TaskId taskId, String startStepId, FlowTileGraph graph,
                        List<Step> steps, List<Lease> leases, long alreadyPaidTau,
                        boolean collected, boolean cancelled) {
        if (id == null || id.trim().isEmpty() || alreadyPaidTau < 0L)
            throw new IllegalArgumentException("Run identity or reward is invalid");
        this.id = id;
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.startStepId = Objects.requireNonNull(startStepId, "startStepId");
        this.graph = Objects.requireNonNull(graph, "graph");
        Map<String, Step> indexed = new LinkedHashMap<>();
        long total = 0L;
        java.util.Set<String> sources = new java.util.HashSet<>();
        for (Step step : steps) {
            if (indexed.put(step.id, step) != null || !sources.add(step.source.id))
                throw new IllegalArgumentException("Duplicate run step");
            total = Math.addExact(total, step.earnedTau);
        }
        if (indexed.isEmpty() || !indexed.keySet().equals(new java.util.HashSet<>(graph.stepIds))
                || !graph.roots().equals(Collections.singletonList(startStepId)))
            throw new IllegalArgumentException("Run snapshot must have exactly its chosen start");
        this.steps = Collections.unmodifiableMap(indexed);
        this.totalEarnedTau = total;
        for (Step step : steps) if (step.state != State.BLOCKED) {
            for (String predecessor : graph.predecessors(step.id))
                if (indexed.get(predecessor).state != State.DONE)
                    throw new IllegalArgumentException("Run step bypasses an unfinished prerequisite");
        }
        this.leases = Collections.unmodifiableList(new ArrayList<>(leases));
        java.util.Set<String> leaseIds = new java.util.HashSet<>();
        for (Lease lease : leases) {
            if (!leaseIds.add(lease.id) || !graph.reaches(lease.acquireStepId, lease.releaseStepId))
                throw new IllegalArgumentException("Invalid run lease");
            State acquire = indexed.get(lease.acquireStepId).state;
            Step release = indexed.get(lease.releaseStepId);
            if (lease.state == FlowResourceState.RESERVED && (acquire == State.WAITING_TIME || acquire == State.DONE))
                throw new IllegalArgumentException("A performed acquisition must activate its reserved capacity");
            if (lease.state == FlowResourceState.ACTIVE && acquire != State.WAITING_TIME && acquire != State.DONE)
                throw new IllegalArgumentException("Active capacity needs a completed acquisition action");
            if (!cancelled && lease.state == FlowResourceState.RELEASED && (release.actionAtEpochMillis == null
                    || (lease.releaseAfterWait && release.state != State.DONE)))
                throw new IllegalArgumentException("Capacity was released before its release point");
            if (cancelled && lease.state.consumesCapacity())
                throw new IllegalArgumentException("A cancelled run cannot hold capacity");
        }
        this.alreadyPaidTau = alreadyPaidTau;
        this.collected = collected;
        this.cancelled = cancelled;
        if (collected && !allDone())
            throw new IllegalArgumentException("Reward settlement does not match the run");
    }

    public boolean allDone() {
        return steps.values().stream().allMatch(step -> step.state == State.DONE);
    }

    public boolean collectionAvailable() { return !cancelled && allDone() && !collected; }

    public long earnedTau() {
        return totalEarnedTau;
    }

    public long uncollectedTau() {
        return collected || cancelled ? 0L : Math.max(0L, earnedTau() - alreadyPaidTau);
    }

    /** Stable ledger identity; storage inserts it and the resulting run in the same transaction. */
    public String collectionKey() { return "flow-collection:" + id; }

    public List<Step> availableSteps() {
        if (cancelled) return Collections.emptyList();
        List<Step> result = new ArrayList<>();
        for (String stepId : graph.stepIds) {
            Step step = steps.get(stepId);
            if (step.state == State.AVAILABLE) result.add(step);
        }
        return Collections.unmodifiableList(result);
    }

    public Long nextReadyAt() {
        if (cancelled) return null;
        Long next = null;
        for (Step step : steps.values())
            if (step.readyAtEpochMillis != null && (next == null || step.readyAtEpochMillis < next))
                next = step.readyAtEpochMillis;
        return next;
    }

    public static final class Step {
        public final String id;
        public final FlowGraphDefinition.Node source;
        public final State state;
        public final Long chosenDelayMillis;
        public final Long readyAtEpochMillis;
        public final Long actionAtEpochMillis;
        public final long earnedTau;

        public Step(String id, FlowGraphDefinition.Node source, State state,
                    Long chosenDelayMillis, Long readyAtEpochMillis, Long actionAtEpochMillis,
                    long earnedTau) {
            if (id == null || id.trim().isEmpty() || earnedTau < 0L
                    || (readyAtEpochMillis != null && readyAtEpochMillis < 0L)
                    || (actionAtEpochMillis != null && actionAtEpochMillis < 0L))
                throw new IllegalArgumentException("Run step is invalid");
            this.id = id;
            this.source = Objects.requireNonNull(source, "source");
            this.state = Objects.requireNonNull(state, "state");
            boolean acted = state == State.WAITING_TIME || state == State.DONE;
            if ((state == State.WAITING_TIME) != (readyAtEpochMillis != null)
                    || acted != (actionAtEpochMillis != null)
                    || acted != (chosenDelayMillis != null))
                throw new IllegalArgumentException("Run step state and action data disagree");
            if (chosenDelayMillis != null
                    && (chosenDelayMillis < 0L || chosenDelayMillis > FlowDelayPolicy.MAX_DELAY_MILLIS))
                throw new IllegalArgumentException("Chosen wait is invalid");
            if (readyAtEpochMillis != null && readyAtEpochMillis < actionAtEpochMillis)
                throw new IllegalArgumentException("Wait ends before its action");
            this.chosenDelayMillis = chosenDelayMillis;
            this.readyAtEpochMillis = readyAtEpochMillis;
            this.actionAtEpochMillis = actionAtEpochMillis;
            this.earnedTau = earnedTau;
        }

        /** One wait per runtime step; never addressed by the ambiguous run ID. */
        public String waitId() { return "flow-wait:" + id; }
    }

    public static final class Lease {
        public final String id;
        public final String sourceLeaseId;
        public final String resourceId;
        public final String acquireStepId;
        public final String releaseStepId;
        public final int units;
        public final boolean releaseAfterWait;
        public final FlowResourceState state;

        public Lease(String id, String sourceLeaseId, String resourceId, String acquireStepId,
                     String releaseStepId, int units, boolean releaseAfterWait, FlowResourceState state) {
            if (id == null || id.trim().isEmpty() || sourceLeaseId == null || sourceLeaseId.trim().isEmpty()
                    || resourceId == null || resourceId.trim().isEmpty()
                    || acquireStepId == null || releaseStepId == null || units < 1)
                throw new IllegalArgumentException("Run lease is incomplete");
            this.id = id;
            this.sourceLeaseId = sourceLeaseId;
            this.resourceId = resourceId;
            this.acquireStepId = acquireStepId;
            this.releaseStepId = releaseStepId;
            this.units = units;
            this.releaseAfterWait = releaseAfterWait;
            this.state = Objects.requireNonNull(state, "state");
        }

        public Lease withState(FlowResourceState next) {
            return new Lease(id, sourceLeaseId, resourceId, acquireStepId, releaseStepId,
                    units, releaseAfterWait, next);
        }
    }
}

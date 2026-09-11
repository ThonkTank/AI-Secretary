package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowGraphDefinition;
import de.thonktank.autosecretary.domain.model.FlowGraphRun;
import de.thonktank.autosecretary.domain.model.FlowGraphRun.Lease;
import de.thonktank.autosecretary.domain.model.FlowGraphRun.State;
import de.thonktank.autosecretary.domain.model.FlowGraphRun.Step;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowTileGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure graph state transitions, evaluated against capacity read inside the caller's transaction.
 * A result is not an admission or a payment until run and ledger have been committed together.
 */
public final class FlowGraphExecution {
    private final IdGenerator ids;

    public FlowGraphExecution(IdGenerator ids) { this.ids = java.util.Objects.requireNonNull(ids); }

    public FlowGraphRun snapshot(FlowGraphDefinition definition, String startTemplateId) {
        if (!definition.graph.roots().contains(startTemplateId))
            throw new IllegalArgumentException("Only an explicit start can create a run");
        FlowTileGraph reachable = definition.graph.reachableFrom(startTemplateId);
        String runId = ids.nextId();
        Map<String, String> runtimeIds = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        for (String sourceId : reachable.topologicalOrder()) {
            String runtimeId = ids.nextId();
            runtimeIds.put(sourceId, runtimeId);
            steps.add(new Step(runtimeId, definition.nodes.get(sourceId), State.BLOCKED,
                    null, null, null, 0L));
        }
        List<FlowTileGraph.Link> links = new ArrayList<>();
        for (FlowTileGraph.Link link : reachable.links)
            links.add(new FlowTileGraph.Link(runtimeIds.get(link.source), runtimeIds.get(link.target)));
        List<Lease> leases = new ArrayList<>();
        for (FlowGraphDefinition.Lease lease : definition.leases)
            if (runtimeIds.containsKey(lease.acquireStepId))
                leases.add(new Lease(ids.nextId(), lease.id, lease.resourceId,
                        runtimeIds.get(lease.acquireStepId), runtimeIds.get(lease.releaseStepId),
                        lease.units, lease.releaseAfterWait, FlowResourceState.PLANNED));
        return new FlowGraphRun(runId, definition.taskId, runtimeIds.get(startTemplateId),
                new FlowTileGraph(new ArrayList<>(runtimeIds.values()), links), steps, leases, 0L, false);
    }

    public FlowGraphRun settle(FlowGraphRun run, long now, Capacity capacity) {
        requireTime(now);
        if (run.cancelled) return run;
        Map<String, Step> steps = new LinkedHashMap<>(run.steps);
        List<Lease> leases = new ArrayList<>(run.leases);
        boolean changed = false;
        for (Step step : run.steps.values()) {
            if (step.state == State.WAITING_TIME && step.readyAtEpochMillis <= now) {
                steps.put(step.id, new Step(step.id, step.source, State.DONE,
                        step.chosenDelayMillis, null, step.actionAtEpochMillis, step.earnedTau));
                release(leases, step.id, true);
                changed = true;
            }
        }
        for (String id : run.graph.topologicalOrder()) {
            Step step = steps.get(id);
            if (step.state != State.BLOCKED && step.state != State.WAITING_RESOURCE) continue;
            boolean ready = true;
            for (String predecessor : run.graph.predecessors(id))
                if (steps.get(predecessor).state != State.DONE) { ready = false; break; }
            if (!ready) continue;
            State next = canReserve(leases, id, capacity) ? State.AVAILABLE : State.WAITING_RESOURCE;
            if (next == State.AVAILABLE) for (int i = 0; i < leases.size(); i++) {
                Lease lease = leases.get(i);
                if (lease.acquireStepId.equals(id) && lease.state == FlowResourceState.PLANNED)
                    leases.set(i, lease.withState(FlowResourceState.RESERVED));
            }
            if (next != step.state) {
                steps.put(id, new Step(id, step.source, next, null, null, null, step.earnedTau));
                changed = true;
            }
        }
        return changed ? copy(run, steps, leases, run.alreadyPaidTau, run.collected) : run;
    }

    /**
     * Does not auto-settle first: an obsolete visible action must never execute a new offer.
     * earnedTau is this step's final total, not a delta on top of migrated partial set rewards.
     */
    public Change complete(FlowGraphRun run, String stepId, Long enteredDelayMillis,
                           long earnedTau, long now, Capacity capacity) {
        requireTime(now);
        Step step = run.steps.get(stepId);
        if (step == null || step.state != State.AVAILABLE || run.collected || run.cancelled)
            return Change.unchanged(run);
        if (step.source.waitAfter.mode == FlowDelayPolicy.Mode.REMEMBER_LAST && enteredDelayMillis == null)
            return new Change(run, false, true, 0L, null);
        if (earnedTau < 0L) throw new IllegalArgumentException("Earned reward must not be negative");
        long delay = step.source.waitAfter.choose(enteredDelayMillis);
        Long readyAt = delay == 0L ? null : Math.addExact(now, delay);
        Map<String, Step> steps = new LinkedHashMap<>(run.steps);
        steps.put(stepId, new Step(stepId, step.source, delay == 0L ? State.DONE : State.WAITING_TIME,
                delay, readyAt, now, earnedTau));
        List<Lease> leases = new ArrayList<>(run.leases);
        for (int i = 0; i < leases.size(); i++) {
            Lease lease = leases.get(i);
            if (lease.acquireStepId.equals(stepId) && lease.state == FlowResourceState.RESERVED)
                leases.set(i, lease.withState(FlowResourceState.ACTIVE));
        }
        release(leases, stepId, false);
        if (delay == 0L) release(leases, stepId, true);
        FlowGraphRun next = settle(copy(run, steps, leases, run.alreadyPaidTau, false), now, capacity);
        // Collect on the last action only if it has no outstanding wait of its own.
        if (next.allDone()) return collect(next);
        return new Change(next, true, false, 0L, null);
    }

    public Change adjustWait(FlowGraphRun run, String waitId, long readyAt, long now, Capacity capacity) {
        requireTime(now);
        requireTime(readyAt);
        if (run.cancelled) return Change.unchanged(run);
        for (Step step : run.steps.values()) {
            if (!step.waitId().equals(waitId) || step.state != State.WAITING_TIME) continue;
            if (readyAt < step.actionAtEpochMillis)
                throw new IllegalArgumentException("Wait cannot end before its action");
            if (step.readyAtEpochMillis == readyAt) return Change.unchanged(run);
            Map<String, Step> steps = new LinkedHashMap<>(run.steps);
            steps.put(step.id, new Step(step.id, step.source, State.WAITING_TIME,
                    step.chosenDelayMillis, readyAt, step.actionAtEpochMillis, step.earnedTau));
            FlowGraphRun next = settle(copy(run, steps, run.leases, run.alreadyPaidTau, run.collected), now, capacity);
            // Time edits and clock ticks never silently collect rewards.
            return new Change(next, true, false, 0L, null);
        }
        return Change.unchanged(run);
    }

    public Change collect(FlowGraphRun run) {
        if (!run.collectionAvailable()) return Change.unchanged(run);
        long payment = run.uncollectedTau();
        FlowGraphRun next = copy(run, run.steps, run.leases, Math.max(run.alreadyPaidTau, run.earnedTau()), true);
        return new Change(next, true, false, payment, run.collectionKey());
    }

    private static boolean canReserve(List<Lease> leases, String stepId, Capacity capacity) {
        Map<String, Long> required = new HashMap<>();
        Map<String, Long> used = new HashMap<>(capacity.otherRunsUsed);
        for (Lease lease : leases) {
            if (lease.state.consumesCapacity())
                used.merge(lease.resourceId, (long) lease.units, Math::addExact);
            else if (lease.state == FlowResourceState.PLANNED && lease.acquireStepId.equals(stepId))
                required.merge(lease.resourceId, (long) lease.units, Math::addExact);
        }
        for (Map.Entry<String, Long> entry : required.entrySet()) {
            long total = capacity.total.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > total - used.getOrDefault(entry.getKey(), 0L)) return false;
        }
        return true;
    }

    private static void release(List<Lease> leases, String stepId, boolean afterWait) {
        for (int i = 0; i < leases.size(); i++) {
            Lease lease = leases.get(i);
            if (lease.releaseStepId.equals(stepId) && lease.releaseAfterWait == afterWait
                    && lease.state.consumesCapacity())
                leases.set(i, lease.withState(FlowResourceState.RELEASED));
        }
    }

    private static FlowGraphRun copy(FlowGraphRun run, Map<String, Step> steps, List<Lease> leases,
                                     long paid, boolean collected) {
        return new FlowGraphRun(run.id, run.taskId, run.startStepId, run.graph,
                new ArrayList<>(steps.values()), leases, paid, collected, run.cancelled);
    }

    private static void requireTime(long now) {
        if (now < 0L) throw new IllegalArgumentException("Timestamp must not be negative");
    }

    public static final class Capacity {
        public final Map<String, Integer> total;
        /** Consuming claims from other runs only; this run is accounted for by the reducer. */
        public final Map<String, Long> otherRunsUsed;

        public Capacity(Map<String, Integer> total, Map<String, Long> otherRunsUsed) {
            this.total = java.util.Collections.unmodifiableMap(new HashMap<>(total));
            this.otherRunsUsed = java.util.Collections.unmodifiableMap(new HashMap<>(otherRunsUsed));
            if (total.values().stream().anyMatch(value -> value == null || value < 0)
                    || otherRunsUsed.values().stream().anyMatch(value -> value == null || value < 0L))
                throw new IllegalArgumentException("Capacity must not be negative");
        }
    }

    public static final class Change {
        public final FlowGraphRun run;
        public final boolean changed;
        public final boolean durationRequired;
        public final long paymentTau;
        public final String paymentKey;

        private Change(FlowGraphRun run, boolean changed, boolean durationRequired,
                       long paymentTau, String paymentKey) {
            this.run = run;
            this.changed = changed;
            this.durationRequired = durationRequired;
            this.paymentTau = paymentTau;
            this.paymentKey = paymentKey;
        }

        static Change unchanged(FlowGraphRun run) { return new Change(run, false, false, 0L, null); }
    }
}

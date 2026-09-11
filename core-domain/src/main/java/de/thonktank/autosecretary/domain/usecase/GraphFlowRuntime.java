package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import java.util.*;

/** Graph execution and the existing occurrence/reward ledger share one transaction owner. */
public final class GraphFlowRuntime implements FlowProgression {
    @Override public boolean canHarvestOccurrence(Occurrence occurrence) {
        return occurrence.kind != OccurrenceKind.FLOW_STEP;
    }
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flows;
    private final FlowGraphDefinitionRepository definitions;
    private final FlowGraphRunRepository runs;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final MomentSource moments;
    private final IdGenerator ids;
    private final FlowGraphExecution execution;
    private final StepSnapshotFactory snapshots;
    private final StepExecutionService actions;
    private final RewardCalculator rewards;

    public GraphFlowRuntime(CatalogRepository catalog, StepRepository steps, TodayRepository today,
                            FlowRepository flows, FlowGraphDefinitionRepository definitions,
                            FlowGraphRunRepository runs, TransactionRunner transactions,
                            Clock clock, MomentSource moments, IdGenerator ids, ComboPolicySource policies) {
        this.catalog = catalog; this.steps = steps; this.today = today; this.flows = flows;
        this.definitions = definitions; this.runs = runs; this.transactions = transactions;
        this.clock = clock; this.moments = moments; this.ids = ids;
        execution = new FlowGraphExecution(ids); snapshots = new StepSnapshotFactory(ids);
        rewards = new RewardCalculator(policies);
        actions = new StepExecutionService(catalog, steps, today, transactions, clock, rewards,
                new CompletionStateMachine(), this);
    }

    public Result start(String candidateId, Long chosenDelayMillis) {
        return transactions.inTransaction(() -> {
            FlowCandidate candidate = flows.findFlowCandidate(candidateId);
            if (candidate == null) return Result.missing();
            Task task = catalog.findTask(candidate.taskId);
            if (task == null || task.archived || task.conditionDone) return Result.missing();
            FlowGraphDefinition definition = definitions.find(candidate.taskId);
            if (definition == null || !definition.graph.roots().contains(candidate.seedStepId)) return Result.missing();
            FlowGraphRunRecord existing = runs.findBySourceKey(candidate.sourceKey);
            if (existing != null) return new Result(FlowGraphCommands.Status.UNCHANGED, existing.run.id, RewardReceipt.none());
            long now = moments.nowEpochMillis();
            FlowGraphRun run = execution.snapshot(definition, candidate.seedStepId);
            run = execution.settle(run, now, capacity(run.id));
            FlowGraphRun.Step start = run.steps.get(run.startStepId);
            if (start.state != FlowGraphRun.State.AVAILABLE)
                return new Result(FlowGraphCommands.Status.CAPACITY_UNAVAILABLE, null, RewardReceipt.none());
            if (requiresDuration(start, chosenDelayMillis))
                return new Result(FlowGraphCommands.Status.DURATION_REQUIRED, null, RewardReceipt.none());
            start.source.waitAfter.choose(chosenDelayMillis); // Reject invalid input before admission.
            runs.insert(candidate, run, now);
            flows.deleteFlowCandidate(candidate.id);
            ensurePlacement(candidate);
            ensureOffers(runs.find(run.id), now);
            return completeInside(start.id, chosenDelayMillis);
        });
    }

    /** Only a runtime step ID is accepted, never a run ID or a mutable template position. */
    public Result complete(String runtimeStepId, Long chosenDelayMillis) {
        return transactions.inTransaction(() -> completeInside(runtimeStepId, chosenDelayMillis));
    }

    private Result completeInside(String runtimeStepId, Long chosenDelayMillis) {
        FlowGraphRunRecord record = runs.findByStepId(runtimeStepId);
        if (record == null) return Result.missing();
        FlowGraphRun.Step runtimeStep = record.run.steps.get(runtimeStepId);
        if (record.run.cancelled || record.run.collected || runtimeStep.state != FlowGraphRun.State.AVAILABLE)
            return new Result(FlowGraphCommands.Status.UNCHANGED, record.run.id, RewardReceipt.none());
        if (requiresDuration(runtimeStep, chosenDelayMillis))
            return new Result(FlowGraphCommands.Status.DURATION_REQUIRED, record.run.id, RewardReceipt.none());
        runtimeStep.source.waitAfter.choose(chosenDelayMillis);
        ensureOffers(record, moments.nowEpochMillis());
        OccurrenceStep action = availableOccurrenceStep(record.run, runtimeStep);
        if (action == null) throw new IllegalStateException("Available flow action has no occurrence");
        actions.completeStep(today.findOccurrence(action.occurrenceId), action, ids.nextId(), chosenDelayMillis);
        FlowGraphRun after = runs.find(record.run.id).run;
        if (after.steps.get(runtimeStepId).state == FlowGraphRun.State.AVAILABLE)
            throw new IllegalStateException("Flow action did not advance atomically");
        activateReadyInside();
        return new Result(FlowGraphCommands.Status.CHANGED, after.id, collectionReceipt(after));
    }

    @Override public void onStepCompleted(Occurrence occurrence, OccurrenceStep step, Long chosenDelayMillis) {
        if (occurrence.kind != OccurrenceKind.FLOW_STEP) return;
        FlowGraphRunRecord record = runs.find(occurrence.flowRunId);
        if (record == null) throw new IllegalStateException("Flow occurrence lost its execution");
        FlowGraphRun.Step runtimeStep = resolve(record.run, step);
        if (runtimeStep == null || runtimeStep.state != FlowGraphRun.State.AVAILABLE)
            throw new IllegalStateException("Obsolete flow action cannot complete another step");
        long now = moments.nowEpochMillis();
        FlowGraphExecution.Change change = execution.complete(record.run, runtimeStep.id, chosenDelayMillis,
                earnedTau(record.run, runtimeStep.source.id), now, capacity(record.run.id));
        if (change.durationRequired) throw new IllegalArgumentException("Choose this step's waiting time first");
        runs.update(change.run, now);
        rememberDelay(runtimeStep, change.run.steps.get(runtimeStep.id).chosenDelayMillis, record.run.taskId);
        if (change.run.collected) credit(change, occurrence);
        today.updateOccurrence(occurrence.complete(clock.today()));
        ensureOffers(runs.find(record.run.id), now);
    }

    public Result collect(String runId) {
        return transactions.inTransaction(() -> {
            FlowGraphRunRecord record = runs.find(runId);
            if (record == null) return Result.missing();
            FlowGraphExecution.Change change = execution.collect(record.run);
            if (!change.changed) return new Result(FlowGraphCommands.Status.UNCHANGED, runId, RewardReceipt.none());
            long now = moments.nowEpochMillis();
            // An internal ledger occurrence is not a Today sheet. The projection exposes one
            // explicit collection action while the graph is all-done and still uncollected.
            int sequence = runs.allocateExecutionSequence(runId, now);
            Occurrence occurrence = Occurrence.flowStep(ids.nextId(), record.run.taskId, clock.today(),
                    record.slot, sortOrder(record), runId, sequence);
            today.insertOccurrence(occurrence);
            runs.update(change.run, now);
            credit(change, occurrence);
            today.updateOccurrence(occurrence.complete(clock.today()));
            activateReadyInside();
            return new Result(FlowGraphCommands.Status.CHANGED, runId, collectionReceipt(change.run));
        });
    }

    public boolean adjustWait(String runId, String waitId, long readyAtEpochMillis) {
        return transactions.inTransaction(() -> {
            FlowGraphRunRecord record = runs.find(runId);
            if (record == null) return false;
            FlowGraphExecution.Change change = execution.adjustWait(record.run, waitId, readyAtEpochMillis,
                    moments.nowEpochMillis(), capacity(runId));
            if (!change.changed) return false;
            runs.update(change.run, moments.nowEpochMillis());
            activateReadyInside();
            return true;
        });
    }

    public boolean activateReady() { return transactions.inTransaction(this::activateReadyInside); }
    public Long nextReadyAtEpochMillis() { return transactions.inTransaction(runs::nextReadyAtEpochMillis); }

    private boolean activateReadyInside() {
        boolean changed = false;
        boolean passChanged;
        do {
            passChanged = false;
            for (FlowGraphRunRecord record : runs.active()) {
                long now = moments.nowEpochMillis();
                FlowGraphRun next = execution.settle(record.run, now, capacity(record.run.id));
                if (next != record.run) { runs.update(next, now); passChanged = true; }
                if (ensureOffers(runs.find(next.id), now)) passChanged = true;
            }
            changed |= passChanged;
        } while (passChanged); // Monotone settle; a later release can unblock an earlier run.
        return changed;
    }

    public FlowGraphExecution.Capacity capacity(String excludedRunId) {
        Map<String, Integer> total = new HashMap<>();
        for (CapacityResource resource : flows.capacityResources()) total.put(resource.id, resource.capacity);
        return new FlowGraphExecution.Capacity(total, runs.consumingUnitsExcluding(excludedRunId));
    }

    private boolean ensureOffers(FlowGraphRunRecord record, long now) {
        boolean created = false;
        for (FlowGraphRun.Step action : record.run.availableSteps()) {
            if (availableOccurrenceStep(record.run, action) != null) continue;
            int sequence = runs.allocateExecutionSequence(record.run.id, now);
            Occurrence occurrence = Occurrence.flowStep(ids.nextId(), record.run.taskId, clock.today(), record.slot,
                    sortOrder(record), record.run.id, sequence);
            today.insertOccurrence(occurrence);
            steps.insertOccurrenceSteps(Collections.singletonList(snapshots.fromFlow(action, occurrence.id, 0)));
            created = true;
        }
        return created;
    }

    public OccurrenceStep availableOccurrenceStep(FlowGraphRun run, FlowGraphRun.Step action) {
        OccurrenceStep result = null;
        for (Occurrence occurrence : today.occurrences(run.taskId)) {
            if (occurrence.state != OccurrenceState.OPEN || !run.id.equals(occurrence.flowRunId)) continue;
            for (OccurrenceStep step : steps.occurrenceSteps(occurrence.id)) if (!step.done && resolve(run, step) == action) {
                if (result != null) throw new IllegalStateException("Duplicate available execution step");
                result = step;
            }
        }
        return result;
    }

    private static FlowGraphRun.Step resolve(FlowGraphRun run, OccurrenceStep step) {
        if (step.flowRunStepId != null) return run.steps.get(step.flowRunStepId);
        // Source IDs are immutable within this run. This also reads old occurrence adapters
        // during the atomic schema cutover; no linear position or old coordinator is used.
        for (FlowGraphRun.Step value : run.steps.values())
            if (value.source.id.equals(step.sourceTemplateId)) return value;
        return null;
    }

    private long earnedTau(FlowGraphRun run, String sourceId) {
        long total = 0L;
        String owner = ComboProgress.stepOwner(sourceId);
        for (RewardBooking booking : bookings(run))
            if (booking.target == RewardBooking.Target.VESSEL && owner.equals(booking.ownerId))
                total = Math.addExact(total, booking.xpDelta);
        if (total < 0L) throw new IllegalStateException("Flow step reward ledger is negative");
        return total;
    }

    private List<RewardBooking> bookings(FlowGraphRun run) {
        List<String> occurrenceIds = new ArrayList<>();
        for (Occurrence occurrence : today.occurrences(run.taskId))
            if (run.id.equals(occurrence.flowRunId)) occurrenceIds.add(occurrence.id);
        Map<String, RewardBooking> unique = new LinkedHashMap<>();
        for (RewardBooking booking : today.rewardBookings(occurrenceIds)) unique.put(booking.id, booking);
        return new ArrayList<>(unique.values());
    }

    private RewardReceipt collectionReceipt(FlowGraphRun run) {
        if (!run.collected) return RewardReceipt.none();
        for (RewardBooking booking : bookings(run)) if (booking.id.equals(run.collectionKey()))
            return RewardReceipt.of(booking.transactionId, Collections.singletonList(booking), RewardReceipt.Target.HEAD);
        return RewardReceipt.none();
    }

    private void credit(FlowGraphExecution.Change change, Occurrence occurrence) {
        if (change.paymentKey == null || change.paymentTau == 0L) return;
        for (RewardBooking booking : bookings(change.run)) if (booking.id.equals(change.paymentKey)) return;
        Task task = Objects.requireNonNull(catalog.findTask(change.run.taskId));
        String owner = ComboProgress.taskOwner(task.id);
        ComboProgress combo = today.combo(owner);
        if (combo == null) combo = ComboProgress.fresh(owner, task.id, ComboProgress.Kind.TASK);
        RewardCalculator.HarvestReward reward = rewards.harvest(task, occurrence, true,
                Math.toIntExact(change.paymentTau), combo, clock.today());
        ComboProgress.Change gain = combo.change(reward.requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(change.paymentKey, change.paymentKey, occurrence.id, null,
                owner, reward.kind, RewardBooking.Target.HEAD, reward.xp, gain.appliedDelta, clock.today(), null);
        today.putCombo(gain.progress);
        today.setXp(Math.addExact(today.xp(), booking.xpDelta));
        today.insertRewardBooking(booking);
        new ComboObligationResolver(today).resolve(owner, task, occurrence, clock.today());
    }

    private void rememberDelay(FlowGraphRun.Step step, Long chosen, TaskId taskId) {
        if (step.source.waitAfter.mode != FlowDelayPolicy.Mode.REMEMBER_LAST) return;
        FlowGraphDefinition current = definitions.find(taskId);
        if (current == null || !current.nodes.containsKey(step.source.id)) return;
        FlowGraphDefinition.Node node = current.nodes.get(step.source.id);
        if (node.waitAfter.mode != step.source.waitAfter.mode
                || node.waitAfter.defaultDelayMillis != step.source.waitAfter.defaultDelayMillis
                || Objects.equals(node.waitAfter.lastUsedDelayMillis, chosen)) return;
        List<FlowGraphDefinition.Node> nodes = new ArrayList<>();
        for (FlowGraphDefinition.Node value : current.nodes.values()) nodes.add(value.id.equals(node.id)
                ? new FlowGraphDefinition.Node(value.id, value.title, value.prescription, value.note,
                    new FlowDelayPolicy(value.waitAfter.mode, value.waitAfter.defaultDelayMillis, chosen)) : value);
        definitions.replace(new FlowGraphDefinition(taskId, current.graph, nodes, current.leases));
    }

    private int sortOrder(FlowGraphRunRecord record) {
        FlowTaskSheetPlacement placement = flows.findFlowTaskSheetPlacement(record.run.taskId, record.slot);
        return placement == null ? (int) Math.min(Integer.MAX_VALUE, record.queueOrder / 1_000_000_000L) : placement.sortOrder;
    }

    private void ensurePlacement(FlowCandidate candidate) {
        if (flows.findFlowTaskSheetPlacement(candidate.taskId, candidate.slot) == null)
            flows.putFlowTaskSheetPlacement(new FlowTaskSheetPlacement(FlowTaskSheetPlacement.stableId(candidate.taskId, candidate.slot),
                    candidate.taskId, candidate.slot, clock.today(), (int) Math.min(Integer.MAX_VALUE, candidate.queueOrder / 1_000_000_000L)));
    }

    private static boolean requiresDuration(FlowGraphRun.Step step, Long delay) {
        return step.source.waitAfter.mode == FlowDelayPolicy.Mode.REMEMBER_LAST && delay == null;
    }

    @Override public boolean canReopenStep(Occurrence occurrence, OccurrenceStep step) {
        return occurrence == null || occurrence.kind != OccurrenceKind.FLOW_STEP;
    }
    @Override public boolean canReopenOccurrence(Occurrence occurrence) {
        return occurrence == null || occurrence.kind != OccurrenceKind.FLOW_STEP;
    }

    public static final class Result {
        public final FlowGraphCommands.Status status;
        public final String runId;
        public final RewardReceipt reward;
        Result(FlowGraphCommands.Status status, String runId, RewardReceipt reward) {
            this.status = status; this.runId = runId; this.reward = reward;
        }
        static Result missing() { return new Result(FlowGraphCommands.Status.NOT_FOUND, null, RewardReceipt.none()); }
    }
}

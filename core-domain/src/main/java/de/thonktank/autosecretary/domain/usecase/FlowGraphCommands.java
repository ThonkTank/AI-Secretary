package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.domain.model.FlowGraphDefinition;
import de.thonktank.autosecretary.domain.model.FlowGraphRun;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Transactional boundary for the graph reducer; admission, claims and reward credits never split. */
public final class FlowGraphCommands {
    public enum Status { CHANGED, UNCHANGED, DURATION_REQUIRED, CAPACITY_UNAVAILABLE, NOT_FOUND }

    public interface Store {
        FlowGraphRun find(String runId);
        FlowGraphRun findBySourceKey(String sourceKey);
        /** Reload candidate, task eligibility and definition inside the admission transaction. */
        Start currentStart(String sourceKey);
        /** Delete only the admitted candidate; false must roll back the complete start. */
        boolean consumeStart(String sourceKey);
        FlowGraphExecution.Capacity capacityExcluding(String runId);
        /** Must use a unique source-key constraint; never replace another run. */
        void insert(String sourceKey, FlowGraphRun run);
        void update(FlowGraphRun run);
        /** Idempotent ledger insertion using the run's stable collection key. */
        void credit(String key, String runId, long tau);
    }

    private final Store store;
    private final TransactionRunner transactions;
    private final MomentSource moments;
    private final FlowGraphExecution execution;

    public FlowGraphCommands(Store store, TransactionRunner transactions, MomentSource moments,
                             IdGenerator ids) {
        this.store = java.util.Objects.requireNonNull(store);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.moments = java.util.Objects.requireNonNull(moments);
        this.execution = new FlowGraphExecution(ids);
    }

    public Result start(String sourceKey, Long chosenDelayMillis, long tau) {
        if (sourceKey == null || sourceKey.trim().isEmpty())
            throw new IllegalArgumentException("Start source key is missing");
        return transactions.inTransaction(() -> {
            FlowGraphRun existing = store.findBySourceKey(sourceKey);
            if (existing != null) return new Result(Status.UNCHANGED, existing);
            Start start = store.currentStart(sourceKey);
            if (start == null) return new Result(Status.NOT_FOUND, null);
            long now = moments.nowEpochMillis();
            FlowGraphRun run = execution.snapshot(start.definition, start.stepId);
            FlowGraphExecution.Capacity capacity = store.capacityExcluding(run.id);
            run = execution.settle(run, now, capacity);
            if (run.steps.get(run.startStepId).state != FlowGraphRun.State.AVAILABLE)
                return new Result(Status.CAPACITY_UNAVAILABLE, null);
            FlowGraphExecution.Change change = execution.complete(run, run.startStepId,
                    chosenDelayMillis, tau, now, capacity);
            if (change.durationRequired) return new Result(Status.DURATION_REQUIRED, null);
            store.insert(sourceKey, change.run);
            if (!store.consumeStart(sourceKey))
                throw new IllegalStateException("Flow candidate changed during admission");
            credit(change);
            return new Result(Status.CHANGED, change.run);
        });
    }

    public Result complete(String runId, String stepId, Long chosenDelayMillis, long tau) {
        return transactions.inTransaction(() -> {
            FlowGraphRun run = store.find(runId);
            if (run == null) return new Result(Status.NOT_FOUND, null);
            return persist(execution.complete(run, stepId, chosenDelayMillis, tau,
                    moments.nowEpochMillis(), store.capacityExcluding(run.id)));
        });
    }

    public Result adjustWait(String runId, String waitId, long readyAtEpochMillis) {
        return transactions.inTransaction(() -> {
            FlowGraphRun run = store.find(runId);
            if (run == null) return new Result(Status.NOT_FOUND, null);
            return persist(execution.adjustWait(run, waitId, readyAtEpochMillis,
                    moments.nowEpochMillis(), store.capacityExcluding(run.id)));
        });
    }

    public Result collect(String runId) {
        return transactions.inTransaction(() -> {
            FlowGraphRun run = store.find(runId);
            return run == null ? new Result(Status.NOT_FOUND, null) : persist(execution.collect(run));
        });
    }

    public Result settle(String runId) {
        return transactions.inTransaction(() -> {
            FlowGraphRun run = store.find(runId);
            if (run == null) return new Result(Status.NOT_FOUND, null);
            FlowGraphRun next = execution.settle(run, moments.nowEpochMillis(), store.capacityExcluding(run.id));
            if (next == run) return new Result(Status.UNCHANGED, run);
            store.update(next);
            return new Result(Status.CHANGED, next);
        });
    }

    private Result persist(FlowGraphExecution.Change change) {
        if (change.durationRequired) return new Result(Status.DURATION_REQUIRED, change.run);
        if (!change.changed) return new Result(Status.UNCHANGED, change.run);
        store.update(change.run);
        credit(change);
        return new Result(Status.CHANGED, change.run);
    }

    private void credit(FlowGraphExecution.Change change) {
        if (change.paymentKey != null && change.paymentTau > 0L)
            store.credit(change.paymentKey, change.run.id, change.paymentTau);
    }

    public static final class Start {
        public final FlowGraphDefinition definition;
        public final String stepId;

        public Start(FlowGraphDefinition definition, String stepId) {
            this.definition = java.util.Objects.requireNonNull(definition);
            this.stepId = java.util.Objects.requireNonNull(stepId);
        }
    }

    public static final class Result {
        public final Status status;
        public final FlowGraphRun run;

        private Result(Status status, FlowGraphRun run) { this.status = status; this.run = run; }
    }
}

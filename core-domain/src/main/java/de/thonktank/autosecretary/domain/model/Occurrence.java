package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

public final class Occurrence {
    public final String id;
    public final TaskId taskId;
    public final LocalDate scheduledOn;
    public final TaskSlot slot;
    public final OccurrenceState state;
    public final int sortOrder;
    public final LocalDate completedOn;
    public final OccurrenceKind kind;
    public final String sourceKey;
    public final String flowRunId;
    public final int flowExecutionSequence;

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, OccurrenceState state,
                      int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, TaskSlot.MORNING, state, sortOrder, completedOn,
                OccurrenceKind.SCHEDULED);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn) {
        this(id, taskId, scheduledOn, slot, state, sortOrder, completedOn,
                OccurrenceKind.SCHEDULED);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn,
                      OccurrenceKind kind) {
        this(id, taskId, scheduledOn, slot, state, sortOrder, completedOn, kind,
                defaultSourceKey(taskId, scheduledOn, slot, kind), null, 0);
    }

    public Occurrence(String id, TaskId taskId, LocalDate scheduledOn, TaskSlot slot,
                      OccurrenceState state, int sortOrder, LocalDate completedOn,
                      OccurrenceKind kind, String sourceKey, String flowRunId,
                      int flowExecutionSequence) {
        if (id == null || id.trim().isEmpty() || taskId == null || scheduledOn == null
                || slot == null || state == null || kind == null)
            throw new IllegalArgumentException("Occurrence identity, task, date and state are required");
        if (state.isHarvested() && completedOn == null)
            throw new IllegalArgumentException("Completed occurrence needs a completion date");
        if (sourceKey == null || sourceKey.trim().isEmpty())
            throw new IllegalArgumentException("Occurrence source key is required");
        if (flowExecutionSequence < 0)
            throw new IllegalArgumentException("Flow execution sequence must not be negative");
        if (kind == OccurrenceKind.FLOW_STEP && (flowRunId == null || flowRunId.isEmpty()))
            throw new IllegalArgumentException("Flow step occurrence needs its run");
        this.id = id;
        this.taskId = taskId;
        this.scheduledOn = scheduledOn;
        this.slot = slot;
        this.state = state;
        this.sortOrder = sortOrder;
        this.completedOn = completedOn;
        this.kind = kind;
        this.sourceKey = sourceKey;
        this.flowRunId = flowRunId == null || flowRunId.isEmpty() ? null : flowRunId;
        this.flowExecutionSequence = flowExecutionSequence;
    }

    public Occurrence complete(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date, kind, sourceKey, flowRunId,
                flowExecutionSequence);
    }

    public Occurrence harvestedWithMissedSteps(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.HARVESTED_WITH_MISSED_STEPS, sortOrder, date, kind,
                sourceKey, flowRunId, flowExecutionSequence);
    }

    public Occurrence missed() {
        if (state != OccurrenceState.OPEN)
            throw new IllegalStateException("Only an open occurrence can be missed");
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.MISSED, sortOrder, null, kind, sourceKey, flowRunId,
                flowExecutionSequence);
    }

    public Occurrence reopen() {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.OPEN, sortOrder, null, kind, sourceKey, flowRunId,
                flowExecutionSequence);
    }

    public Occurrence moveTo(int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, slot, state, newSortOrder, completedOn,
                kind, sourceKey, flowRunId, flowExecutionSequence);
    }

    public Occurrence moveTo(TaskSlot newSlot, int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, newSlot, state,
                newSortOrder, completedOn, kind, sourceKey, flowRunId, flowExecutionSequence);
    }

    public static Occurrence flowStep(String id, TaskId taskId, LocalDate scheduledOn,
                                      TaskSlot slot, int sortOrder, String runId,
                                      int executionSequence) {
        String key = "flow-step:" + runId + ':' + executionSequence;
        return new Occurrence(id, taskId, scheduledOn, slot, OccurrenceState.OPEN, sortOrder,
                null, OccurrenceKind.FLOW_STEP, key, runId, executionSequence);
    }

    private static String defaultSourceKey(TaskId taskId, LocalDate date, TaskSlot slot,
                                           OccurrenceKind kind) {
        if (taskId == null || date == null || slot == null || kind == null) return "invalid";
        if (kind == OccurrenceKind.CONDITION) return "condition:" + taskId.value;
        return "scheduled:" + taskId.value + ':' + date + ':' + slot.storageCode;
    }
}

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
    public final int flowSheetSequence;

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
                      int flowSheetSequence) {
        if (id == null || id.trim().isEmpty() || taskId == null || scheduledOn == null
                || slot == null || state == null || kind == null)
            throw new IllegalArgumentException("Occurrence identity, task, date and state are required");
        if (state.isHarvested() && completedOn == null)
            throw new IllegalArgumentException("Completed occurrence needs a completion date");
        if (sourceKey == null || sourceKey.trim().isEmpty())
            throw new IllegalArgumentException("Occurrence source key is required");
        if (flowSheetSequence < 0)
            throw new IllegalArgumentException("Flow sheet sequence must not be negative");
        if (kind == OccurrenceKind.FLOW_SHEET && (flowRunId == null || flowRunId.isEmpty()))
            throw new IllegalArgumentException("Flow sheet occurrence needs its run");
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
        this.flowSheetSequence = flowSheetSequence;
    }

    public Occurrence complete(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.COMPLETED, sortOrder, date, kind, sourceKey, flowRunId,
                flowSheetSequence);
    }

    public Occurrence harvestedWithMissedSteps(LocalDate date) {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.HARVESTED_WITH_MISSED_STEPS, sortOrder, date, kind,
                sourceKey, flowRunId, flowSheetSequence);
    }

    public Occurrence missed() {
        if (state != OccurrenceState.OPEN)
            throw new IllegalStateException("Only an open occurrence can be missed");
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.MISSED, sortOrder, null, kind, sourceKey, flowRunId,
                flowSheetSequence);
    }

    public Occurrence reopen() {
        return new Occurrence(id, taskId, scheduledOn, slot,
                OccurrenceState.OPEN, sortOrder, null, kind, sourceKey, flowRunId,
                flowSheetSequence);
    }

    public Occurrence moveTo(int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, slot, state, newSortOrder, completedOn,
                kind, sourceKey, flowRunId, flowSheetSequence);
    }

    public Occurrence moveTo(TaskSlot newSlot, int newSortOrder) {
        return new Occurrence(id, taskId, scheduledOn, newSlot, state,
                newSortOrder, completedOn, kind, sourceKey, flowRunId, flowSheetSequence);
    }

    public static Occurrence flowSheet(String id, TaskId taskId, LocalDate scheduledOn,
                                       TaskSlot slot, int sortOrder, String runId,
                                       int sheetSequence) {
        String key = "flow-sheet:" + runId + ':' + sheetSequence;
        return new Occurrence(id, taskId, scheduledOn, slot, OccurrenceState.OPEN, sortOrder,
                null, OccurrenceKind.FLOW_SHEET, key, runId, sheetSequence);
    }

    private static String defaultSourceKey(TaskId taskId, LocalDate date, TaskSlot slot,
                                           OccurrenceKind kind) {
        if (taskId == null || date == null || slot == null || kind == null) return "invalid";
        if (kind == OccurrenceKind.CONDITION) return "condition:" + taskId.value;
        return "scheduled:" + taskId.value + ':' + date + ':' + slot.storageCode;
    }
}

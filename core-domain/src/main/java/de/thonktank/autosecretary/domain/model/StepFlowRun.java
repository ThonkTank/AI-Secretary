package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Durable cursor for one resolved flow path. */
public final class StepFlowRun {
    public final String id;
    public final TaskId taskId;
    public final String seedStepId;
    public final String sourceKey;
    public final LocalDate scheduledOn;
    public final TaskSlot slot;
    public final StepFlowRunState state;
    public final int currentPosition;
    public final Long readyAtEpochMillis;
    public final String currentSheetOccurrenceId;
    public final long queueOrder;
    public final int nextSheetSequence;
    public final long createdAtEpochMillis;
    public final long updatedAtEpochMillis;

    public StepFlowRun(String id, TaskId taskId, String seedStepId, String sourceKey,
                       LocalDate scheduledOn, TaskSlot slot, StepFlowRunState state,
                       int currentPosition, Long readyAtEpochMillis,
                       String currentSheetOccurrenceId, long queueOrder, int nextSheetSequence,
                       long createdAtEpochMillis, long updatedAtEpochMillis) {
        if (blank(id) || taskId == null || blank(seedStepId) || blank(sourceKey)
                || scheduledOn == null || slot == null || state == null)
            throw new IllegalArgumentException("Flow run is incomplete");
        if (currentPosition < 0 || nextSheetSequence < 0 || queueOrder < 0L
                || createdAtEpochMillis < 0L || updatedAtEpochMillis < createdAtEpochMillis)
            throw new IllegalArgumentException("Flow run cursor or timestamps are invalid");
        if (state == StepFlowRunState.WAITING_TIME && readyAtEpochMillis == null)
            throw new IllegalArgumentException("A timed wait needs a ready timestamp");
        this.id = id;
        this.taskId = taskId;
        this.seedStepId = seedStepId;
        this.sourceKey = sourceKey;
        this.scheduledOn = scheduledOn;
        this.slot = slot;
        this.state = state;
        this.currentPosition = currentPosition;
        this.readyAtEpochMillis = readyAtEpochMillis;
        this.currentSheetOccurrenceId = emptyToNull(currentSheetOccurrenceId);
        this.queueOrder = queueOrder;
        this.nextSheetSequence = nextSheetSequence;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.updatedAtEpochMillis = updatedAtEpochMillis;
    }

    public StepFlowRun withState(StepFlowRunState next, Long readyAt, long now) {
        return new StepFlowRun(id, taskId, seedStepId, sourceKey, scheduledOn, slot, next,
                currentPosition, readyAt, currentSheetOccurrenceId, queueOrder,
                nextSheetSequence, createdAtEpochMillis, now);
    }

    public StepFlowRun offerOnSheet(String occurrenceId, int sheetSequence, long now) {
        return new StepFlowRun(id, taskId, seedStepId, sourceKey, scheduledOn, slot,
                StepFlowRunState.OFFERED, currentPosition, null, occurrenceId, queueOrder,
                sheetSequence + 1, createdAtEpochMillis, now);
    }

    public StepFlowRun advance(int position, StepFlowRunState next, Long readyAt, long now) {
        if (position <= currentPosition)
            throw new IllegalArgumentException("Flow run must advance to a later step");
        return new StepFlowRun(id, taskId, seedStepId, sourceKey, scheduledOn, slot, next,
                position, readyAt, null, queueOrder, nextSheetSequence,
                createdAtEpochMillis, now);
    }

    public StepFlowRun reorder(long order, long now) {
        return new StepFlowRun(id, taskId, seedStepId, sourceKey, scheduledOn, slot, state,
                currentPosition, readyAtEpochMillis, currentSheetOccurrenceId, order,
                nextSheetSequence, createdAtEpochMillis, now);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof StepFlowRun)) return false;
        StepFlowRun value = (StepFlowRun) other;
        return id.equals(value.id) && taskId.equals(value.taskId)
                && seedStepId.equals(value.seedStepId) && sourceKey.equals(value.sourceKey)
                && scheduledOn.equals(value.scheduledOn) && slot == value.slot
                && state == value.state && currentPosition == value.currentPosition
                && Objects.equals(readyAtEpochMillis, value.readyAtEpochMillis)
                && Objects.equals(currentSheetOccurrenceId, value.currentSheetOccurrenceId)
                && queueOrder == value.queueOrder && nextSheetSequence == value.nextSheetSequence
                && createdAtEpochMillis == value.createdAtEpochMillis
                && updatedAtEpochMillis == value.updatedAtEpochMillis;
    }

    @Override public int hashCode() {
        return Objects.hash(id, taskId, seedStepId, sourceKey, scheduledOn, slot, state,
                currentPosition, readyAtEpochMillis, currentSheetOccurrenceId, queueOrder,
                nextSheetSequence, createdAtEpochMillis, updatedAtEpochMillis);
    }
}

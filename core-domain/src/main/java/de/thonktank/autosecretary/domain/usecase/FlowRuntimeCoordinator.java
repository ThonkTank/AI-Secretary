package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.MomentSource;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single transactional owner of admission, capacity, waits and sheet hand-off for flow flows.
 * Waiting runs have no occurrence step and therefore cannot block the normal Today focus.
 */
public final class FlowRuntimeCoordinator implements FlowProgression {
    private static final long REQUEUE_GAP = 1_000L;

    private final StepRepository steps;
    private final TodayRepository today;
    private final FlowRepository flows;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final MomentSource moments;
    private final IdGenerator ids;
    private final StepSnapshotFactory snapshots;

    public FlowRuntimeCoordinator(StepRepository steps, TodayRepository today,
                                  FlowRepository flows,
                                  TransactionRunner transactions, Clock clock,
                                  MomentSource moments, IdGenerator ids) {
        this.steps = steps;
        this.today = today;
        this.flows = flows;
        this.transactions = transactions;
        this.clock = clock;
        this.moments = moments;
        this.ids = ids;
        snapshots = new StepSnapshotFactory(ids);
    }

    public boolean activateReady() {
        return transactions.inTransaction(() -> activateReadyInside(moments.nowEpochMillis()));
    }

    public Long nextReadyAtEpochMillis() {
        Long result = null;
        for (StepFlowRun run : flows.activeFlowRuns())
            if (run.state == StepFlowRunState.WAITING_TIME && run.readyAtEpochMillis != null
                    && (result == null || run.readyAtEpochMillis < result))
                result = run.readyAtEpochMillis;
        return result;
    }

    public boolean defer(String runId) {
        return transactions.inTransaction(() -> {
            StepFlowRun run = flows.findFlowRun(runId);
            if (run == null || run.state != StepFlowRunState.OFFERED) return false;
            long now = moments.nowEpochMillis();
            String sheet = removeUntouchedOffer(run);
            resetReservationsAt(run.id, run.currentPosition, now);
            long last = run.queueOrder;
            for (StepFlowRun value : flows.activeFlowRuns())
                last = Math.max(last, value.queueOrder);
            StepFlowRun queued = run.withState(StepFlowRunState.WAITING_RESOURCE, null, now)
                    .reorder(last + REQUEUE_GAP, now);
            if (sheet == null) queued = queued.clearCurrentSheet(now);
            flows.updateFlowRun(queued);
            activateReadyInside(now);
            return true;
        });
    }

    public boolean cancel(String runId) {
        return transactions.inTransaction(() -> {
            StepFlowRun run = flows.findFlowRun(runId);
            if (run == null || !run.state.active()) return false;
            long now = moments.nowEpochMillis();
            if (run.state == StepFlowRunState.OFFERED) removeUntouchedOffer(run);
            for (FlowRunResourceSnapshot resource : flows.flowRunResources(run.id))
                if (resource.state != FlowResourceState.RELEASED)
                    flows.updateFlowRunResource(resourceState(resource,
                            FlowResourceState.RELEASED, now));
            flows.updateFlowRun(run.withState(StepFlowRunState.CANCELLED, null, now)
                    .clearCurrentSheet(now));
            activateReadyInside(now);
            return true;
        });
    }

    public boolean adjustReadyAt(String runId, long readyAtEpochMillis) {
        if (readyAtEpochMillis < 0L)
            throw new IllegalArgumentException("Ready timestamp must not be negative");
        return transactions.inTransaction(() -> {
            StepFlowRun run = flows.findFlowRun(runId);
            if (run == null || run.state != StepFlowRunState.WAITING_TIME) return false;
            long now = moments.nowEpochMillis();
            StepFlowRun changed = run.withState(readyAtEpochMillis <= now
                    ? StepFlowRunState.WAITING_RESOURCE : StepFlowRunState.WAITING_TIME,
                    readyAtEpochMillis <= now ? null : readyAtEpochMillis, now);
            flows.updateFlowRun(changed);
            activateReadyInside(now);
            return true;
        });
    }

    public boolean reorder(String runId, long queueOrder) {
        if (queueOrder < 0L) throw new IllegalArgumentException("Queue order must not be negative");
        return transactions.inTransaction(() -> {
            StepFlowRun run = flows.findFlowRun(runId);
            if (run == null || !run.state.active()) return false;
            long now = moments.nowEpochMillis();
            flows.updateFlowRun(run.reorder(queueOrder, now));
            activateReadyInside(now);
            return true;
        });
    }

    public boolean reorderBefore(String runId, String beforeRunId) {
        return transactions.inTransaction(() -> {
            List<StepFlowRun> active = new ArrayList<>(flows.activeFlowRuns());
            StepFlowRun moving = null;
            for (StepFlowRun run : active) if (run.id.equals(runId)) moving = run;
            if (moving == null) return false;
            active.remove(moving);
            int target = active.size();
            if (beforeRunId != null) {
                target = -1;
                for (int index = 0; index < active.size(); index++)
                    if (active.get(index).id.equals(beforeRunId)) {
                        target = index;
                        break;
                    }
                if (target < 0) return false;
            }
            active.add(target, moving);
            long now = moments.nowEpochMillis();
            boolean changed = false;
            for (int index = 0; index < active.size(); index++) {
                long order = (index + 1L) * REQUEUE_GAP;
                StepFlowRun run = active.get(index);
                if (run.queueOrder != order) {
                    flows.updateFlowRun(run.reorder(order, now));
                    changed = true;
                }
            }
            activateReadyInside(now);
            return changed;
        });
    }

    @Override public void onStepCompleted(Occurrence occurrence, OccurrenceStep step,
                                          Long chosenDelayMillis) {
        if (!flowSheet(occurrence) || step == null) return;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null || run.state != StepFlowRunState.OFFERED) return;
        FlowRunStepSnapshot current = stepAt(run.id, run.currentPosition);
        if (current == null || !current.sourceTemplateId.equals(step.sourceTemplateId)) return;
        long now = moments.nowEpochMillis();
        activateAcquiredResources(run.id, run.currentPosition, now);
        releaseResources(run.id, run.currentPosition, now);
        List<FlowRunStepSnapshot> path = flows.flowRunSteps(run.id);
        if (run.currentPosition + 1 >= path.size()) {
            flows.updateFlowRun(run.withState(StepFlowRunState.COMPLETED, null, now));
            activateReadyInside(now);
            return;
        }
        long delayMillis = current.delayAfter == null ? 0L
                : current.delayAfter.choose(chosenDelayMillis);
        if (current.delayAfter != null) {
            flows.updateFlowRunStep(current.chooseDelay(delayMillis));
            rememberDelay(run, current, path.get(run.currentPosition + 1), delayMillis);
        }
        Long readyAt = delayMillis == 0L ? null : safeAdd(now, delayMillis);
        flows.updateFlowRun(run.advance(run.currentPosition + 1,
                readyAt == null ? StepFlowRunState.WAITING_RESOURCE
                        : StepFlowRunState.WAITING_TIME, readyAt, now));
        activateReadyInside(now);
    }

    @Override public boolean canReopenStep(Occurrence occurrence, OccurrenceStep step) {
        if (!flowSheet(occurrence)) return true;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null || step == null || step.sourceTemplateId == null) return false;
        int position = positionOf(run.id, step.sourceTemplateId);
        if (position < 0) return false;
        if (run.state == StepFlowRunState.COMPLETED) return run.currentPosition == position;
        if (run.currentPosition != position + 1) return false;
        if (run.state == StepFlowRunState.WAITING_TIME
                || run.state == StepFlowRunState.WAITING_RESOURCE) return true;
        if (run.state != StepFlowRunState.OFFERED || run.currentSheetOccurrenceId == null)
            return false;
        Occurrence nextSheet = today.findOccurrence(run.currentSheetOccurrenceId);
        if (nextSheet == null || nextSheet.state != OccurrenceState.OPEN) return false;
        OccurrenceStep offered = offeredStep(run, nextSheet.id);
        return offered != null && !offered.done;
    }

    @Override public void onStepReopened(Occurrence occurrence, OccurrenceStep step) {
        if (!flowSheet(occurrence) || step == null || step.sourceTemplateId == null) return;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null) return;
        int position = positionOf(run.id, step.sourceTemplateId);
        if (position < 0) return;
        long now = moments.nowEpochMillis();
        if (run.state == StepFlowRunState.COMPLETED && run.currentPosition == position) {
            restoreResourcesAfterUndo(run.id, position, -1, now);
            flows.updateFlowRun(run.withState(StepFlowRunState.OFFERED, null, now)
                    .withCurrentSheet(occurrence.id, now));
            return;
        }
        if (run.currentPosition != position + 1) return;
        if (run.state == StepFlowRunState.OFFERED) removeUntouchedOffer(run);
        restoreResourcesAfterUndo(run.id, position, position + 1, now);
        flows.updateFlowRun(run.rewind(position, occurrence.id, now));
    }

    @Override public void onOccurrenceHarvested(Occurrence occurrence) {
        if (!flowSheet(occurrence)) return;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null) return;
        long now = moments.nowEpochMillis();
        if (run.state == StepFlowRunState.OFFERED) {
            OccurrenceStep open = offeredStep(run, occurrence.id);
            if (open != null && !open.done) {
                Occurrence replacement = Occurrence.flowSheet(ids.nextId(), occurrence.taskId,
                        clock.today(), occurrence.slot, occurrence.sortOrder, run.id,
                        run.nextSheetSequence);
                today.insertOccurrence(replacement);
                OccurrenceStep carried = snapshots.carryForward(open, replacement.id, 0,
                        occurrence.id);
                steps.insertOccurrenceSteps(Collections.singletonList(carried));
                flows.updateFlowRun(run.offerOnSheet(replacement.id,
                        run.nextSheetSequence, now));
                return;
            }
        }
        flows.updateFlowRun(run.clearCurrentSheet(now));
    }

    @Override public boolean canReopenOccurrence(Occurrence occurrence) {
        if (!flowSheet(occurrence)) return true;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null) return false;
        if (run.currentSheetOccurrenceId == null || run.currentSheetOccurrenceId.equals(occurrence.id))
            return true;
        if (run.state != StepFlowRunState.OFFERED) return false;
        Occurrence replacement = today.findOccurrence(run.currentSheetOccurrenceId);
        if (replacement == null || replacement.state != OccurrenceState.OPEN) return false;
        List<OccurrenceStep> snapshots = steps.occurrenceSteps(replacement.id);
        return snapshots.size() == 1 && !snapshots.get(0).done;
    }

    @Override public void onOccurrenceReopened(Occurrence occurrence) {
        if (!flowSheet(occurrence)) return;
        StepFlowRun run = flows.findFlowRun(occurrence.flowRunId);
        if (run == null) return;
        long now = moments.nowEpochMillis();
        if (run.currentSheetOccurrenceId != null
                && !run.currentSheetOccurrenceId.equals(occurrence.id))
            today.deleteOccurrence(run.currentSheetOccurrenceId);
        flows.updateFlowRun(run.withCurrentSheet(occurrence.id, now));
    }

    private boolean activateReadyInside(long now) {
        boolean changed = false;
        List<StepFlowRun> active = flows.activeFlowRuns();
        for (StepFlowRun stale : active) {
            StepFlowRun run = flows.findFlowRun(stale.id);
            if (run == null || !run.state.active()) continue;
            if (run.state == StepFlowRunState.WAITING_TIME
                    && run.readyAtEpochMillis != null && run.readyAtEpochMillis <= now) {
                run = run.withState(StepFlowRunState.WAITING_RESOURCE, null, now);
                flows.updateFlowRun(run);
                changed = true;
            }
            if (run.state != StepFlowRunState.WAITING_RESOURCE || !hasCapacity(run)) continue;
            reserveRequired(run, now);
            offer(run, now);
            changed = true;
        }
        return changed;
    }

    private boolean hasCapacity(StepFlowRun run) {
        Map<String, Integer> required = new HashMap<>();
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(run.id))
            if (resource.state == FlowResourceState.PLANNED
                    && resource.acquirePosition == run.currentPosition)
                required.put(resource.resourceId,
                        required.getOrDefault(resource.resourceId, 0) + resource.units);
        if (required.isEmpty()) return true;
        Map<String, Integer> used = new HashMap<>();
        for (FlowRunResourceSnapshot resource : flows.consumingFlowResources())
            used.put(resource.resourceId,
                    used.getOrDefault(resource.resourceId, 0) + resource.units);
        for (Map.Entry<String, Integer> value : required.entrySet()) {
            int capacity = capacity(value.getKey(), run.id);
            if (used.getOrDefault(value.getKey(), 0) + value.getValue() > capacity) return false;
        }
        return true;
    }

    private int capacity(String resourceId, String runId) {
        CapacityResource current = flows.findCapacityResource(resourceId);
        if (current != null) return current.capacity;
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(runId))
            if (resource.resourceId.equals(resourceId)) return resource.capacityAtCreation;
        return 0;
    }

    private void reserveRequired(StepFlowRun run, long now) {
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(run.id))
            if (resource.state == FlowResourceState.PLANNED
                    && resource.acquirePosition == run.currentPosition)
                flows.updateFlowRunResource(resourceState(resource,
                        FlowResourceState.RESERVED, now));
    }

    private void offer(StepFlowRun run, long now) {
        FlowRunStepSnapshot snapshot = stepAt(run.id, run.currentPosition);
        if (snapshot == null) return;
        Occurrence sheet = run.currentSheetOccurrenceId == null ? null
                : today.findOccurrence(run.currentSheetOccurrenceId);
        boolean existingSheet = sheet != null && sheet.state == OccurrenceState.OPEN;
        if (!existingSheet) {
            int order = (int) Math.max(0L, Math.min(Integer.MAX_VALUE,
                    run.queueOrder / 1_000_000_000L));
            sheet = Occurrence.flowSheet(ids.nextId(), run.taskId, clock.today(), run.slot,
                    order, run.id, run.nextSheetSequence);
            today.insertOccurrence(sheet);
        }
        if (offeredStep(run, sheet.id) == null) {
            int position = 0;
            for (OccurrenceStep value : steps.occurrenceSteps(sheet.id))
                position = Math.max(position, value.position + 1);
            OccurrenceStep step = snapshots.fromFlow(snapshot, sheet.id, position);
            steps.insertOccurrenceSteps(Collections.singletonList(step));
        }
        flows.updateFlowRun(existingSheet
                ? run.offerOnExistingSheet(sheet.id, now)
                : run.offerOnSheet(sheet.id, run.nextSheetSequence, now));
    }

    private void activateAcquiredResources(String runId, int position, long now) {
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(runId))
            if (resource.acquirePosition == position
                    && resource.state == FlowResourceState.RESERVED)
                flows.updateFlowRunResource(resourceState(resource,
                        FlowResourceState.ACTIVE, now));
    }

    private void releaseResources(String runId, int position, long now) {
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(runId))
            if (resource.releasePosition == position && resource.state.consumesCapacity())
                flows.updateFlowRunResource(resourceState(resource,
                        FlowResourceState.RELEASED, now));
    }

    private void resetReservationsAt(String runId, int position, long now) {
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(runId))
            if (resource.acquirePosition == position
                    && resource.state == FlowResourceState.RESERVED)
                flows.updateFlowRunResource(resourceState(resource,
                        FlowResourceState.PLANNED, now));
    }

    private void restoreResourcesAfterUndo(String runId, int reopenedPosition,
                                           int successorPosition, long now) {
        for (FlowRunResourceSnapshot resource : flows.flowRunResources(runId)) {
            FlowResourceState next = null;
            if (successorPosition >= 0 && resource.acquirePosition == successorPosition
                    && resource.state == FlowResourceState.RESERVED)
                next = FlowResourceState.PLANNED;
            else if (resource.releasePosition == reopenedPosition
                    && resource.state == FlowResourceState.RELEASED)
                next = FlowResourceState.ACTIVE;
            else if (resource.acquirePosition == reopenedPosition
                    && resource.state == FlowResourceState.ACTIVE)
                next = FlowResourceState.RESERVED;
            if (next != null) flows.updateFlowRunResource(resourceState(resource, next, now));
        }
    }

    private String removeUntouchedOffer(StepFlowRun run) {
        if (run.currentSheetOccurrenceId == null) return null;
        Occurrence sheet = today.findOccurrence(run.currentSheetOccurrenceId);
        if (sheet == null || sheet.state != OccurrenceState.OPEN) return null;
        OccurrenceStep offered = offeredStep(run, sheet.id);
        if (offered != null && !offered.done) steps.deleteOccurrenceStep(offered.id);
        if (steps.occurrenceSteps(sheet.id).isEmpty()) {
            today.deleteOccurrence(sheet.id);
            return null;
        }
        return sheet.id;
    }

    private OccurrenceStep offeredStep(StepFlowRun run, String occurrenceId) {
        FlowRunStepSnapshot current = stepAt(run.id, run.currentPosition);
        if (current == null) return null;
        for (OccurrenceStep step : steps.occurrenceSteps(occurrenceId))
            if (!step.done && current.sourceTemplateId.equals(step.sourceTemplateId)) return step;
        return null;
    }

    private FlowRunStepSnapshot stepAt(String runId, int position) {
        for (FlowRunStepSnapshot step : flows.flowRunSteps(runId))
            if (step.position == position) return step;
        return null;
    }

    private int positionOf(String runId, String sourceTemplateId) {
        for (FlowRunStepSnapshot step : flows.flowRunSteps(runId))
            if (sourceTemplateId.equals(step.sourceTemplateId)) return step.position;
        return -1;
    }

    private void rememberDelay(StepFlowRun run, FlowRunStepSnapshot current,
                               FlowRunStepSnapshot next, long chosen) {
        for (StepTransition transition : flows.stepTransitions(run.taskId))
            if (transition.sourceStepId.equals(current.sourceTemplateId)
                    && transition.targetStepId.equals(next.sourceTemplateId)) {
                flows.updateStepTransition(new StepTransition(transition.sourceStepId,
                        transition.targetStepId, transition.delay.remember(chosen)));
                return;
            }
    }

    private static FlowRunResourceSnapshot resourceState(FlowRunResourceSnapshot resource,
                                                         FlowResourceState state, long now) {
        Long reserved = resource.reservedAtEpochMillis;
        Long activated = resource.activatedAtEpochMillis;
        Long released = resource.releasedAtEpochMillis;
        if (state == FlowResourceState.PLANNED) {
            reserved = null;
            activated = null;
            released = null;
        } else if (state == FlowResourceState.RESERVED) {
            reserved = reserved == null ? now : reserved;
            activated = null;
            released = null;
        } else if (state == FlowResourceState.ACTIVE) {
            reserved = reserved == null ? now : reserved;
            activated = activated == null ? now : activated;
            released = null;
        } else if (state == FlowResourceState.RELEASED) {
            released = released == null ? now : released;
        }
        return new FlowRunResourceSnapshot(resource.id, resource.runId, resource.sourceLeaseId,
                resource.resourceId, resource.resourceName, resource.capacityAtCreation,
                resource.units, resource.acquirePosition, resource.releasePosition, state,
                reserved, activated, released);
    }

    private static boolean flowSheet(Occurrence occurrence) {
        return occurrence != null && occurrence.kind == OccurrenceKind.FLOW_SHEET;
    }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}

package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingObservation;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.ComboObligationRepository;
import de.thonktank.autosecretary.domain.schedule.ScheduleProjector;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.domain.today.TodayStepOrder;
import de.thonktank.autosecretary.domain.today.TodayOccurrenceSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Transactional owner of step progress, step completion rewards and execution ordering. */
public final class StepExecutionService {
    private final OccurrenceExecutionRepository occurrences;
    private final RewardLedgerRepository ledger;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final RewardCalculator rewards;
    private final CompletionStateMachine states;
    private final ComboObligationResolver obligationResolver;
    private final FlowProgression flows;

    StepExecutionService(OccurrenceExecutionRepository occurrences,
                         RewardLedgerRepository ledger, ComboObligationRepository obligations,
                         TransactionRunner transactions,
                         Clock clock) {
        this(occurrences, ledger, obligations, transactions, clock, new RewardCalculator(),
                new CompletionStateMachine(), FlowProgression.NONE);
    }

    StepExecutionService(OccurrenceExecutionRepository occurrences,
                         RewardLedgerRepository ledger, ComboObligationRepository obligations,
                         TransactionRunner transactions, Clock clock,
                         ComboPolicySource policies) {
        this(occurrences, ledger, obligations, transactions, clock,
                new RewardCalculator(policies),
                new CompletionStateMachine(), FlowProgression.NONE);
    }

    StepExecutionService(OccurrenceExecutionRepository occurrences,
                         RewardLedgerRepository ledger, ComboObligationRepository obligations,
                         TransactionRunner transactions, Clock clock,
                         ComboPolicySource policies,
                         FlowRuntimeCoordinator flows) {
        this(occurrences, ledger, obligations, transactions, clock,
                new RewardCalculator(policies),
                new CompletionStateMachine(), flows);
    }

    StepExecutionService(OccurrenceExecutionRepository occurrences,
                         RewardLedgerRepository ledger, ComboObligationRepository obligations,
                         TransactionRunner transactions,
                         Clock clock, RewardCalculator rewards,
                         CompletionStateMachine states, FlowProgression flows) {
        this.occurrences = occurrences;
        this.ledger = ledger;
        this.transactions = transactions;
        this.clock = clock;
        this.rewards = rewards;
        this.states = states;
        this.obligationResolver = new ComboObligationResolver(obligations);
        this.flows = flows == null ? FlowProgression.NONE : flows;
    }

    public RewardReceipt toggleStep(String stepId) {
        return toggleStep(stepId, null);
    }

    public RewardReceipt toggleStep(String stepId, Long chosenDelayMillis) {
        return transactions.inTransaction(() -> {
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step == null || step.repetitionProgress != null)
                return RewardReceipt.none();
            Occurrence occurrence = occurrences.findOccurrence(step.occurrenceId);
            return step.done ? undoStep(occurrence, step) : completeStep(
                    occurrence, step, newId(), chosenDelayMillis);
        });
    }

    public StepExecutionResult recordRepetitionResult(String stepId, int repetitions) {
        return recordSetResult(stepId, SetResult.repetitions(repetitions));
    }

    public StepExecutionResult recordSetResult(String stepId, SetResult value) {
        return transactions.inTransaction(() -> recordSetResultInsideTransaction(stepId, value));
    }

    StepExecutionResult recordSetResultInsideTransaction(String stepId, SetResult value) {
        OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
        if (step == null)
            return result(StepExecutionResult.Status.INVALID_STEP, null, RewardReceipt.none());
        Occurrence occurrence = occurrences.findOccurrence(step.occurrenceId);
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
            return result(StepExecutionResult.Status.OCCURRENCE_CLOSED, step,
                    RewardReceipt.none());
        if (step.done || step.repetitionProgress == null)
            return result(StepExecutionResult.Status.UNSUPPORTED, step, RewardReceipt.none());
        OccurrenceStep changed = step.recordSetResult(value);
        occurrences.updateOccurrenceStep(changed);
        RewardReceipt reward = adjustQuantitativeReward(occurrence, changed, newId());
        if (changed.done) {
            obligationResolver.resolve(changed.comboOwnerId,
                    occurrences.findTask(occurrence.taskId), occurrence, clock.today());
            flows.onStepCompleted(occurrence, changed, null);
            finalizeZeroOccurrenceIfComplete(occurrence);
            return result(StepExecutionResult.Status.COMPLETED,
                    occurrences.findOccurrenceStep(stepId), reward);
        }
        return result(StepExecutionResult.Status.RECORDED, changed, reward);
    }

    public StepExecutionResult correctRepetitionResult(String stepId, int index, int repetitions) {
        return transactions.inTransaction(() -> {
            OccurrenceStep current = occurrences.findOccurrenceStep(stepId);
            if (current == null)
                return result(StepExecutionResult.Status.INVALID_STEP, null,
                        RewardReceipt.none());
            TrainingObservation training = current.repetitionProgress == null
                    || index < 0 || index >= current.repetitionProgress.results.size() ? null
                    : current.repetitionProgress.results.get(index).training;
            return correctSetResultInsideTransaction(stepId, index,
                    new SetResult(repetitions, training));
        });
    }

    public StepExecutionResult correctSetResult(String stepId, int index, SetResult value) {
        return transactions.inTransaction(
                () -> correctSetResultInsideTransaction(stepId, index, value));
    }

    StepExecutionResult correctSetResultInsideTransaction(String stepId, int index,
                                                          SetResult value) {
        OccurrenceStep current = occurrences.findOccurrenceStep(stepId);
        if (current == null)
            return result(StepExecutionResult.Status.INVALID_STEP, null, RewardReceipt.none());
        Occurrence occurrence = occurrences.findOccurrence(current.occurrenceId);
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
            return result(StepExecutionResult.Status.OCCURRENCE_CLOSED, current,
                    RewardReceipt.none());
        if (current.repetitionProgress == null)
            return result(StepExecutionResult.Status.UNSUPPORTED, current,
                    RewardReceipt.none());
        OccurrenceStep changed = current.correctSetResult(index, value);
        if (current.done && !changed.done && !flows.canReopenStep(occurrence, current))
            return result(StepExecutionResult.Status.UNSUPPORTED, current,
                    RewardReceipt.none());
        occurrences.updateOccurrenceStep(changed);
        RewardReceipt reward = adjustQuantitativeReward(occurrence, changed, newId());
        if (!current.done && changed.done) flows.onStepCompleted(occurrence, changed, null);
        else if (current.done && !changed.done) flows.onStepReopened(occurrence, changed);
        if (changed.done) finalizeZeroOccurrenceIfComplete(occurrence);
        return result(StepExecutionResult.Status.CORRECTED, changed, reward);
    }

    public AdvanceTodayStepResult advanceStepWithPlannedResult(String stepId) {
        return transactions.inTransaction(() -> {
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step == null)
                return advance(AdvanceTodayStepResult.Status.INVALID_STEP, null,
                        Collections.emptyList(), RewardReceipt.none());
            if (step.done)
                return advance(AdvanceTodayStepResult.Status.STEP_ALREADY_DONE, null,
                        openIds(step.occurrenceId), RewardReceipt.none());
            Occurrence occurrence = occurrences.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return advance(AdvanceTodayStepResult.Status.OCCURRENCE_CLOSED, null,
                        openIds(step.occurrenceId), RewardReceipt.none());
            if (step.repetitionProgress == null) {
                RewardReceipt reward = completeStep(occurrence, step, newId());
                return advance(AdvanceTodayStepResult.Status.STEP_COMPLETED, null,
                        openIds(step.occurrenceId), reward);
            }

            Integer planned = plannedValue(step.amount);
            if (planned == null)
                return advance(AdvanceTodayStepResult.Status.NO_PLANNED_VALUE, null,
                        openIds(step.occurrenceId), RewardReceipt.none());
            OccurrenceStep changed = step.recordRepetitionResult(planned);
            occurrences.updateOccurrenceStep(changed);
            RewardReceipt reward = adjustQuantitativeReward(occurrence, changed, newId());
            if (changed.done) {
                flows.onStepCompleted(occurrence, changed, null);
                return advance(AdvanceTodayStepResult.Status.STEP_COMPLETED, planned,
                        openIds(step.occurrenceId), reward);
            }
            moveToFirstOpen(occurrence, changed.id);
            return advance(AdvanceTodayStepResult.Status.PROGRESS_RECORDED, planned,
                    openIds(step.occurrenceId), reward);
        });
    }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step,
                               String transactionId) {
        return completeStep(occurrence, step, transactionId, null);
    }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step,
                               String transactionId, Long chosenDelayMillis) {
        if (step != null && step.repetitionProgress != null) {
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            OccurrenceStep completed = step.done ? step : step.complete();
            occurrences.updateOccurrenceStep(completed);
            RewardReceipt receipt = adjustQuantitativeReward(occurrence, completed, transactionId);
            obligationResolver.resolve(completed.comboOwnerId,
                    occurrences.findTask(occurrence.taskId), occurrence, clock.today());
            if (!step.done && completed.done)
                flows.onStepCompleted(occurrence, completed, chosenDelayMillis);
            finalizeZeroOccurrenceIfComplete(occurrence);
            return receipt;
        }
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step == null
                || activeOriginal(occurrence.id, step.id, RewardBooking.Target.VESSEL) != null)
            return RewardReceipt.none();
        ComboProgress settled = combo(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP);
        RewardCalculator.StepReward calculated = rewards.step(settled,
                occurrence.scheduledOn.equals(clock.today()));
        ComboProgress.Change change = settled.change(calculated.requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(newId(), transactionId, occurrence.id, step.id,
                step.comboOwnerId, RewardBooking.Kind.STEP_EARNED, RewardBooking.Target.VESSEL,
                calculated.xp, change.appliedDelta, clock.today(), null);
        ledger.putCombo(change.progress);
        ledger.insertRewardBooking(booking);
        OccurrenceStep completed = states.completeStep(occurrence, step);
        occurrences.updateOccurrenceStep(completed);
        obligationResolver.resolve(step.comboOwnerId, occurrences.findTask(occurrence.taskId),
                occurrence, clock.today());
        flows.onStepCompleted(occurrence, completed, chosenDelayMillis);
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.VESSEL);
    }

    RewardReceipt completeWithPlannedResults(Occurrence occurrence, OccurrenceStep step,
                                             String transactionId) {
        if (step == null || step.repetitionProgress == null)
            return completeStep(occurrence, step, transactionId);
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step.done)
            return RewardReceipt.none();
        Integer planned = plannedValue(step.amount);
        if (planned == null) return completeStep(occurrence, step, transactionId);
        OccurrenceStep changed = step;
        while (!changed.done) changed = changed.recordRepetitionResult(planned);
        occurrences.updateOccurrenceStep(changed);
        RewardReceipt receipt = adjustQuantitativeReward(occurrence, changed, transactionId);
        obligationResolver.resolve(changed.comboOwnerId,
                occurrences.findTask(occurrence.taskId), occurrence, clock.today());
        flows.onStepCompleted(occurrence, changed, null);
        return receipt;
    }

    private RewardReceipt adjustQuantitativeReward(Occurrence occurrence, OccurrenceStep step,
                                                    String transactionId) {
        if (step.repetitionProgress == null) return RewardReceipt.none();
        List<RewardBooking> history = ledger.rewardBookings(occurrence.id);
        int currentXp = 0;
        int currentCombo = 0;
        Integer frozenPlan = null;
        boolean hasPlanBooking = false;
        for (RewardBooking booking : history) {
            if (!same(step.id, booking.occurrenceStepId)
                    || booking.target != RewardBooking.Target.VESSEL) continue;
            currentXp += booking.xpDelta;
            currentCombo += booking.comboPointDelta;
            if (booking.plannedXp != null) {
                frozenPlan = booking.plannedXp;
                hasPlanBooking = true;
            }
        }
        ComboProgress settled = combo(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP);
        RewardCalculator.StepReward calculated = rewards.step(settled,
                occurrence.scheduledOn.equals(clock.today()));
        int plannedXp = frozenPlan == null ? calculated.xp : frozenPlan;
        long numerator = 0;
        for (Integer actual : step.repetitionProgress.actualRepetitions) numerator += actual;
        long denominator = plannedTotal(step.amount);
        int desiredXp = numerator == 0 || denominator <= 0 ? 0
                : Math.toIntExact((plannedXp * numerator + denominator - 1) / denominator);
        int desiredCombo = desiredXp <= 0 ? 0
                : hasPlanBooking ? currentCombo : calculated.requestedComboDelta;
        int xpDelta = desiredXp - currentXp;
        int requestedComboDelta = desiredCombo - currentCombo;
        if (xpDelta == 0 && requestedComboDelta == 0) {
            if (desiredXp > 0) obligationResolver.resolve(step.comboOwnerId,
                    occurrences.findTask(occurrence.taskId), occurrence, clock.today());
            else obligationResolver.reopen(step.comboOwnerId, occurrence, clock.today());
            return RewardReceipt.none();
        }
        ComboProgress.Change comboChange = settled.change(requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(newId(), transactionId, occurrence.id, step.id,
                step.comboOwnerId, hasPlanBooking ? RewardBooking.Kind.STEP_ADJUSTMENT
                : RewardBooking.Kind.STEP_EARNED, RewardBooking.Target.VESSEL,
                xpDelta, comboChange.appliedDelta, clock.today(), null, plannedXp);
        ledger.putCombo(comboChange.progress);
        ledger.insertRewardBooking(booking);
        if (desiredXp > 0) obligationResolver.resolve(step.comboOwnerId,
                occurrences.findTask(occurrence.taskId), occurrence, clock.today());
        else obligationResolver.reopen(step.comboOwnerId, occurrence, clock.today());
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.VESSEL);
    }

    private static long plannedTotal(StepAmount amount) {
        if (amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) amount;
            return (long) value.sets * value.repetitions;
        }
        if (amount instanceof StepAmount.Repetitions)
            return ((StepAmount.Repetitions) amount).repetitions;
        return 0;
    }

    private void finalizeZeroOccurrenceIfComplete(Occurrence occurrence) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
        for (OccurrenceStep value : occurrences.occurrenceSteps(occurrence.id))
            if (!value.done) return;
        int vesselXp = 0;
        for (RewardBooking booking : ledger.rewardBookings(occurrence.id))
            if (booking.target == RewardBooking.Target.VESSEL) vesselXp += booking.xpDelta;
        if (vesselXp != 0) return;
        if (occurrence.kind == OccurrenceKind.FLOW_SHEET) return;
        de.thonktank.autosecretary.domain.model.Task task =
                occurrences.findTask(occurrence.taskId);
        if (task == null) return;
        obligationResolver.resolve(ComboProgress.taskOwner(task.id), task, occurrence,
                clock.today());
        occurrences.updateOccurrence(states.completeOccurrence(occurrence, clock.today()));
        occurrences.updateTask(new ScheduleProjector().project(task, new ScheduleProjector.Input(
                occurrences.earliestOpenOccurrence(task.id),
                occurrences.latestCompletedOccurrence(task.id))));
    }

    private RewardReceipt undoStep(Occurrence occurrence, OccurrenceStep step) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || !step.done)
            return RewardReceipt.none();
        if (!flows.canReopenStep(occurrence, step)) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, step.id,
                RewardBooking.Target.VESSEL);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, occurrence.taskId, ComboProgress.Kind.STEP);
        ledger.putCombo(current.undo(original.comboPointDelta, clock.today()));
        ledger.insertRewardBooking(reversal);
        OccurrenceStep reopened = states.reopenStep(occurrence, step);
        occurrences.updateOccurrenceStep(reopened);
        obligationResolver.reopen(original.ownerId, occurrence, clock.today());
        flows.onStepReopened(occurrence, reopened);
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.VESSEL);
    }

    private void moveToFirstOpen(Occurrence occurrence, String stepId) {
        List<OccurrenceStep> all = occurrences.occurrenceSteps(occurrence.id);
        OccurrenceStep moving = null;
        OccurrenceStep first = null;
        for (OccurrenceStep value : all) {
            if (value.id.equals(stepId)) moving = value;
            else if (!value.done && first == null) first = value;
        }
        if (moving == null || first == null) return;
        TodayStepMoveResult move = TodayStepOrder.move(
                new TodayOccurrenceSnapshot(occurrence, all, first), stepId, first.id);
        if (move.moved()) occurrences.updateOccurrenceStepPositions(move.positionUpdates);
    }

    private List<String> openIds(String occurrenceId) {
        List<String> result = new ArrayList<>();
        for (OccurrenceStep value : occurrences.occurrenceSteps(occurrenceId))
            if (!value.done) result.add(value.id);
        return result;
    }

    private RewardBooking activeOriginal(String occurrenceId, String stepId,
                                         RewardBooking.Target target) {
        List<RewardBooking> bookings = ledger.rewardBookings(occurrenceId);
        Set<String> reversed = new HashSet<>();
        for (RewardBooking booking : bookings)
            if (booking.reversesBookingId != null) reversed.add(booking.reversesBookingId);
        RewardBooking match = null;
        for (RewardBooking booking : bookings) {
            if (booking.reversesBookingId != null || reversed.contains(booking.id)
                    || booking.target != target || !same(stepId, booking.occurrenceStepId)) continue;
            match = booking;
        }
        return match;
    }

    private ComboProgress combo(String owner, de.thonktank.autosecretary.domain.model.TaskId taskId,
                                ComboProgress.Kind kind) {
        ComboProgress combo = ledger.combo(owner);
        if (combo != null) return combo;
        combo = ComboProgress.fresh(owner, taskId, kind);
        ledger.putCombo(combo);
        return combo;
    }

    private static Integer plannedValue(StepAmount amount) {
        if (amount instanceof StepAmount.SetsReps)
            return ((StepAmount.SetsReps) amount).repetitions;
        if (amount instanceof StepAmount.Repetitions)
            return ((StepAmount.Repetitions) amount).repetitions;
        return null;
    }

    private static StepExecutionResult result(StepExecutionResult.Status status,
                                              OccurrenceStep step, RewardReceipt reward) {
        return new StepExecutionResult(status, step, reward);
    }

    private static AdvanceTodayStepResult advance(AdvanceTodayStepResult.Status status,
                                                  Integer planned, List<String> open,
                                                  RewardReceipt reward) {
        return new AdvanceTodayStepResult(status, planned, open, reward);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String newId() { return UUID.randomUUID().toString(); }
}

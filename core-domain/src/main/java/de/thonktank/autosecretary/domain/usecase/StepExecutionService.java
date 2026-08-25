package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
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

/** Transactional owner of step progress, step completion rewards and execution ordering. */
public final class StepExecutionService {
    private final OccurrenceExecutionRepository occurrences;
    private final RewardLedgerRepository ledger;
    private final Clock clock;
    private final RewardCalculator rewards;
    private final CompletionStateMachine states;
    private final ComboObligationResolver obligationResolver;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    StepExecutionService(T repository, Clock clock) {
        this(repository, repository, clock, new RewardCalculator(),
                new CompletionStateMachine());
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    StepExecutionService(T repository, Clock clock, ComboPolicySource policies) {
        this(repository, repository, clock, new RewardCalculator(policies),
                new CompletionStateMachine());
    }

    StepExecutionService(OccurrenceExecutionRepository occurrences,
                         RewardLedgerRepository ledger, Clock clock, RewardCalculator rewards,
                         CompletionStateMachine states) {
        this.occurrences = occurrences;
        this.ledger = ledger;
        this.clock = clock;
        this.rewards = rewards;
        this.states = states;
        this.obligationResolver = new ComboObligationResolver(occurrences);
    }

    public RewardReceipt toggleStep(String stepId) {
        return occurrences.inTransaction(() -> {
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step == null || step.amount instanceof StepAmount.SetsReps)
                return RewardReceipt.none();
            Occurrence occurrence = occurrences.findOccurrence(step.occurrenceId);
            return step.done ? undoStep(occurrence, step) : completeStep(
                    occurrence, step, newId());
        });
    }

    public StepExecutionResult recordRepetitionResult(String stepId, int repetitions) {
        return occurrences.inTransaction(() -> {
            OccurrenceStep step = occurrences.findOccurrenceStep(stepId);
            if (step == null)
                return result(StepExecutionResult.Status.INVALID_STEP, null,
                        RewardReceipt.none());
            Occurrence occurrence = occurrences.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return result(StepExecutionResult.Status.OCCURRENCE_CLOSED, step,
                        RewardReceipt.none());
            if (step.done || step.repetitionProgress == null)
                return result(StepExecutionResult.Status.UNSUPPORTED, step,
                        RewardReceipt.none());
            OccurrenceStep changed = step.recordRepetitionResult(repetitions);
            occurrences.updateOccurrenceStep(changed);
            if (changed.done) {
                RewardReceipt reward = completeStep(occurrence, changed, newId());
                return result(StepExecutionResult.Status.COMPLETED,
                        occurrences.findOccurrenceStep(stepId), reward);
            }
            return result(StepExecutionResult.Status.RECORDED, changed, RewardReceipt.none());
        });
    }

    public StepExecutionResult correctRepetitionResult(String stepId, int index, int repetitions) {
        return occurrences.inTransaction(() -> {
            OccurrenceStep current = occurrences.findOccurrenceStep(stepId);
            if (current == null)
                return result(StepExecutionResult.Status.INVALID_STEP, null,
                        RewardReceipt.none());
            Occurrence occurrence = occurrences.findOccurrence(current.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return result(StepExecutionResult.Status.OCCURRENCE_CLOSED, current,
                        RewardReceipt.none());
            if (current.repetitionProgress == null)
                return result(StepExecutionResult.Status.UNSUPPORTED, current,
                        RewardReceipt.none());
            OccurrenceStep changed = current.correctRepetitionResult(index, repetitions);
            occurrences.updateOccurrenceStep(changed);
            return result(StepExecutionResult.Status.CORRECTED, changed, RewardReceipt.none());
        });
    }

    public AdvanceTodayStepResult advanceStepWithPlannedResult(String stepId) {
        return occurrences.inTransaction(() -> {
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
            if (changed.done) {
                RewardReceipt reward = completeStep(occurrence, changed, newId());
                return advance(AdvanceTodayStepResult.Status.STEP_COMPLETED, planned,
                        openIds(step.occurrenceId), reward);
            }
            moveToFirstOpen(occurrence, changed.id);
            return advance(AdvanceTodayStepResult.Status.PROGRESS_RECORDED, planned,
                    openIds(step.occurrenceId), RewardReceipt.none());
        });
    }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step,
                               String transactionId) {
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
        occurrences.updateOccurrenceStep(states.completeStep(occurrence, step));
        obligationResolver.resolve(step.comboOwnerId, occurrences.findTask(occurrence.taskId),
                occurrence, clock.today());
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.VESSEL);
    }

    private RewardReceipt undoStep(Occurrence occurrence, OccurrenceStep step) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || !step.done)
            return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, step.id,
                RewardBooking.Target.VESSEL);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, occurrence.taskId, ComboProgress.Kind.STEP);
        ledger.putCombo(current.undo(original.comboPointDelta, clock.today()));
        ledger.insertRewardBooking(reversal);
        occurrences.updateOccurrenceStep(states.reopenStep(occurrence, step));
        obligationResolver.reopen(original.ownerId, occurrence, clock.today());
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

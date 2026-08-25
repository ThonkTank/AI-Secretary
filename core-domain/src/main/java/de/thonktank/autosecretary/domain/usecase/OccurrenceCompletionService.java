package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.schedule.ScheduleProjector;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.OccurrenceExecutionRepository;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Transactional orchestrator for completion, reward projection, undo and scheduling. */
public final class OccurrenceCompletionService {
    private final OccurrenceExecutionRepository occurrences;
    private final RewardLedgerRepository ledger;
    private final Clock clock;
    private final RewardCalculator rewards;
    private final CompletionStateMachine states;
    private final ScheduleProjector schedules;
    private final StepExecutionService stepExecution;
    private final ComboObligationResolver obligationResolver;

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    OccurrenceCompletionService(T repository, Clock clock) {
        this(repository, repository, clock, new RewardCalculator(),
                new CompletionStateMachine(), new ScheduleProjector());
    }

    public <T extends OccurrenceExecutionRepository & RewardLedgerRepository>
    OccurrenceCompletionService(T repository, Clock clock, ComboPolicySource policies) {
        this(repository, repository, clock, new RewardCalculator(policies),
                new CompletionStateMachine(), new ScheduleProjector());
    }

    OccurrenceCompletionService(OccurrenceExecutionRepository occurrences,
                      RewardLedgerRepository ledger, Clock clock, RewardCalculator rewards,
                      CompletionStateMachine states, ScheduleProjector schedules) {
        this.occurrences = occurrences;
        this.ledger = ledger;
        this.clock = clock;
        this.rewards = rewards;
        this.states = states;
        this.schedules = schedules;
        this.stepExecution = new StepExecutionService(occurrences, ledger, clock,
                rewards, states);
        this.obligationResolver = new ComboObligationResolver(occurrences);
    }

    public RewardReceipt completeRemainingSteps(String occurrenceId) {
        return occurrences.inTransaction(() -> {
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            Occurrence occurrence = occurrences.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            for (OccurrenceStep step : occurrences.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(stepExecution.completeWithPlannedResults(
                        occurrence, step,
                        transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.VESSEL);
        });
    }

    public RewardReceipt completeOccurrence(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return RewardReceipt.none();
        return occurrences.inTransaction(() -> {
            Occurrence occurrence = occurrences.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            Task task = occurrences.findTask(occurrence.taskId);
            if (task == null) return RewardReceipt.none();
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            for (OccurrenceStep step : occurrences.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(stepExecution.completeWithPlannedResults(
                        occurrence, step,
                        transactionId).bookings);
            bookings.addAll(harvest(occurrences.findOccurrence(occurrenceId), task,
                    transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.HEAD);
        });
    }

    public RewardReceipt harvestOccurrence(String occurrenceId) {
        return occurrences.inTransaction(() -> {
            Occurrence occurrence = occurrences.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : occurrences.findTask(occurrence.taskId);
            return harvest(occurrence, task, newId());
        });
    }

    public RewardReceipt undoOccurrence(String occurrenceId) {
        return occurrences.inTransaction(() -> {
            Occurrence occurrence = occurrences.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : occurrences.findTask(occurrence.taskId);
            return undoHarvest(occurrence, task);
        });
    }

    public RewardReceipt closeCondition(TaskId taskId) {
        return occurrences.inTransaction(() -> {
            Task task = occurrences.findTask(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty()
                    || task.conditionDone) return RewardReceipt.none();
            Occurrence open = occurrences.openOccurrence(task.id);
            if (open == null) {
                de.thonktank.autosecretary.domain.model.TaskSlot slot = new TaskSchedule(
                        occurrences.scheduleEntries(task.id)).primary(task.id).slot;
                open = new Occurrence("condition:" + task.id.value + ":" + clock.today(),
                        task.id, clock.today(), slot, OccurrenceState.OPEN,
                        Integer.MAX_VALUE, null, OccurrenceKind.CONDITION);
                occurrences.insertOccurrence(open);
            } else if (open.kind != OccurrenceKind.CONDITION) {
                open = new Occurrence(open.id, open.taskId, open.scheduledOn, open.slot,
                        open.state, open.sortOrder, open.completedOn, OccurrenceKind.CONDITION);
            }
            RewardReceipt receipt = harvest(open, task, newId());
            Task projected = occurrences.findTask(task.id);
            occurrences.updateTask(projected.closeCondition(clock.today()));
            return receipt;
        });
    }

    private RewardReceipt harvest(Occurrence occurrence, Task task, String transactionId) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.OPEN
                || activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD) != null)
            return RewardReceipt.none();
        List<OccurrenceStep> steps = occurrences.occurrenceSteps(occurrence.id);
        int collected = netXp(ledger.rewardBookings(occurrence.id), RewardBooking.Target.VESSEL);
        boolean routine = !steps.isEmpty();
        if (routine && collected <= 0) return RewardReceipt.none();
        boolean hasMissedSteps = false;
        for (OccurrenceStep step : steps) if (!step.done) {
            hasMissedSteps = true;
            break;
        }
        ComboProgress settled = combo(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK);
        RewardCalculator.HarvestReward calculated = rewards.harvest(task, occurrence, routine,
                collected, settled, clock.today());
        ComboProgress.Change change = settled.change(calculated.requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(newId(), transactionId, occurrence.id, null,
                ComboProgress.taskOwner(task.id), calculated.kind, RewardBooking.Target.HEAD,
                calculated.xp, change.appliedDelta, clock.today(), null);
        ledger.putCombo(change.progress);
        ledger.setXp(ledger.xp() + booking.xpDelta);
        ledger.insertRewardBooking(booking);
        obligationResolver.resolve(ComboProgress.taskOwner(task.id), task, occurrence,
                clock.today());
        occurrences.updateOccurrence(hasMissedSteps
                ? states.harvestWithMissedSteps(occurrence, clock.today())
                : states.completeOccurrence(occurrence, clock.today()));
        projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.HEAD);
    }

    private RewardReceipt undoHarvest(Occurrence occurrence, Task task) {
        if (occurrence == null || task == null || !occurrence.state.isHarvested()
                || !clock.today().equals(occurrence.completedOn)) return RewardReceipt.none();
        Occurrence otherOpen = occurrences.earliestOpenOccurrence(task.id);
        if (otherOpen != null && !otherOpen.id.equals(occurrence.id)) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, task.id, ComboProgress.Kind.TASK);
        ledger.putCombo(current.undo(original.comboPointDelta, clock.today()));
        ledger.setXp(ledger.xp() + reversal.xpDelta);
        ledger.insertRewardBooking(reversal);
        obligationResolver.reopen(original.ownerId, occurrence, clock.today());
        occurrences.updateOccurrence(states.reopenOccurrence(occurrence));
        if (task.ongoing && task.conditionDone) occurrences.updateTask(task.reopenCondition());
        projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.HEAD);
    }

    private void projectSchedule(TaskId taskId) {
        Task task = occurrences.findTask(taskId);
        if (task == null) return;
        occurrences.updateTask(schedules.project(task, new ScheduleProjector.Input(
                occurrences.earliestOpenOccurrence(taskId),
                occurrences.latestCompletedOccurrence(taskId))));
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
                    || booking.kind == RewardBooking.Kind.COMBO_DECAY
                    || booking.target != target || !same(stepId, booking.occurrenceStepId)) continue;
            match = booking;
        }
        return match;
    }

    private static int netXp(List<RewardBooking> bookings, RewardBooking.Target target) {
        int total = 0;
        for (RewardBooking booking : bookings) if (booking.target == target) total += booking.xpDelta;
        return total;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private ComboProgress combo(String owner, TaskId taskId, ComboProgress.Kind kind) {
        ComboProgress combo = ledger.combo(owner);
        if (combo != null) return combo;
        combo = ComboProgress.fresh(owner, taskId, kind);
        ledger.putCombo(combo);
        return combo;
    }

    private static String newId() { return UUID.randomUUID().toString(); }
}

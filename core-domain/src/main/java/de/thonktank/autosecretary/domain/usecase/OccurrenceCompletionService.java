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
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Transactional orchestrator for completion, reward projection, undo and scheduling. */
public final class OccurrenceCompletionService {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final RewardCalculator rewards;
    private final CompletionStateMachine states;
    private final ScheduleProjector schedules;
    private final StepExecutionService stepExecution;
    private final ComboObligationResolver obligationResolver;
    private final FlowProgression flows;

    OccurrenceCompletionService(CatalogRepository catalog, StepRepository steps,
                                TodayRepository today,
                                TransactionRunner transactions, Clock clock) {
        this(catalog, steps, today, transactions, clock, new RewardCalculator(),
                new CompletionStateMachine(), new ScheduleProjector(),
                FlowProgression.NONE);
    }

    OccurrenceCompletionService(CatalogRepository catalog, StepRepository steps,
                                TodayRepository today,
                                TransactionRunner transactions, Clock clock,
                                ComboPolicySource policies) {
        this(catalog, steps, today, transactions, clock,
                new RewardCalculator(policies),
                new CompletionStateMachine(), new ScheduleProjector(),
                FlowProgression.NONE);
    }

    OccurrenceCompletionService(CatalogRepository catalog, StepRepository steps,
                                TodayRepository today,
                                TransactionRunner transactions, Clock clock,
                                ComboPolicySource policies,
                                FlowRuntimeCoordinator flows) {
        this(catalog, steps, today, transactions, clock,
                new RewardCalculator(policies),
                new CompletionStateMachine(), new ScheduleProjector(), flows);
    }

    OccurrenceCompletionService(CatalogRepository catalog, StepRepository steps,
                      TodayRepository today,
                      TransactionRunner transactions,
                      Clock clock, RewardCalculator rewards,
                      CompletionStateMachine states, ScheduleProjector schedules,
                      FlowProgression flows) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
        this.clock = clock;
        this.rewards = rewards;
        this.states = states;
        this.schedules = schedules;
        this.flows = flows == null ? FlowProgression.NONE : flows;
        this.stepExecution = new StepExecutionService(catalog, steps, today,
                transactions, clock, rewards, states, this.flows);
        this.obligationResolver = new ComboObligationResolver(today);
    }

    public RewardReceipt completeRemainingSteps(String occurrenceId) {
        return transactions.inTransaction(() -> {
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            Occurrence occurrence = today.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            if (occurrence.kind == OccurrenceKind.FLOW_SHEET) return RewardReceipt.none();
            for (OccurrenceStep step : steps.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(stepExecution.completeWithPlannedResults(
                        occurrence, step,
                        transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.VESSEL);
        });
    }

    public RewardReceipt completeOccurrence(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return RewardReceipt.none();
        return transactions.inTransaction(() -> {
            Occurrence occurrence = today.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            if (occurrence.kind == OccurrenceKind.FLOW_SHEET) return RewardReceipt.none();
            Task task = catalog.findTask(occurrence.taskId);
            if (task == null) return RewardReceipt.none();
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            for (OccurrenceStep step : steps.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(stepExecution.completeWithPlannedResults(
                        occurrence, step,
                        transactionId).bookings);
            bookings.addAll(harvest(today.findOccurrence(occurrenceId), task,
                    transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.HEAD);
        });
    }

    public RewardReceipt harvestOccurrence(String occurrenceId) {
        return transactions.inTransaction(() -> {
            Occurrence occurrence = today.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : catalog.findTask(occurrence.taskId);
            return harvest(occurrence, task, newId());
        });
    }

    public RewardReceipt undoOccurrence(String occurrenceId) {
        return transactions.inTransaction(() -> {
            Occurrence occurrence = today.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : catalog.findTask(occurrence.taskId);
            return undoHarvest(occurrence, task);
        });
    }

    public RewardReceipt closeCondition(TaskId taskId) {
        return transactions.inTransaction(() -> {
            Task task = catalog.findTask(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty()
                    || task.conditionDone) return RewardReceipt.none();
            Occurrence open = today.openOccurrence(task.id);
            if (open == null) {
                de.thonktank.autosecretary.domain.model.TaskSlot slot = new TaskSchedule(
                        catalog.scheduleEntries(task.id)).primary(task.id).slot;
                open = new Occurrence("condition:" + task.id.value + ":" + clock.today(),
                        task.id, clock.today(), slot, OccurrenceState.OPEN,
                        Integer.MAX_VALUE, null, OccurrenceKind.CONDITION);
                today.insertOccurrence(open);
            } else if (open.kind != OccurrenceKind.CONDITION) {
                open = new Occurrence(open.id, open.taskId, open.scheduledOn, open.slot,
                        open.state, open.sortOrder, open.completedOn, OccurrenceKind.CONDITION);
            }
            RewardReceipt receipt = harvest(open, task, newId());
            Task projected = catalog.findTask(task.id);
            catalog.updateTask(projected.closeCondition(clock.today()));
            return receipt;
        });
    }

    private RewardReceipt harvest(Occurrence occurrence, Task task, String transactionId) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.OPEN
                || activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD) != null)
            return RewardReceipt.none();
        List<OccurrenceStep> occurrenceSteps = steps.occurrenceSteps(occurrence.id);
        int collected = netXp(today.rewardBookings(occurrence.id), RewardBooking.Target.VESSEL);
        boolean routine = !occurrenceSteps.isEmpty();
        if (routine && collected <= 0) return RewardReceipt.none();
        boolean hasMissedSteps = false;
        for (OccurrenceStep step : occurrenceSteps) if (!step.done) {
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
        today.putCombo(change.progress);
        today.setXp(today.xp() + booking.xpDelta);
        today.insertRewardBooking(booking);
        obligationResolver.resolve(ComboProgress.taskOwner(task.id), task, occurrence,
                clock.today());
        Occurrence harvested = hasMissedSteps
                ? states.harvestWithMissedSteps(occurrence, clock.today())
                : states.completeOccurrence(occurrence, clock.today());
        today.updateOccurrence(harvested);
        if (occurrence.kind == OccurrenceKind.FLOW_SHEET)
            flows.onOccurrenceHarvested(harvested);
        else projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.HEAD);
    }

    private RewardReceipt undoHarvest(Occurrence occurrence, Task task) {
        if (occurrence == null || task == null || !occurrence.state.isHarvested()
                || !clock.today().equals(occurrence.completedOn)) return RewardReceipt.none();
        if (!flows.canReopenOccurrence(occurrence)) return RewardReceipt.none();
        Occurrence otherOpen = today.earliestOpenOccurrence(task.id);
        if (occurrence.kind != OccurrenceKind.FLOW_SHEET && otherOpen != null
                && !otherOpen.id.equals(occurrence.id)) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, task.id, ComboProgress.Kind.TASK);
        today.putCombo(current.undo(original.comboPointDelta, clock.today()));
        today.setXp(today.xp() + reversal.xpDelta);
        today.insertRewardBooking(reversal);
        obligationResolver.reopen(original.ownerId, occurrence, clock.today());
        Occurrence reopened = states.reopenOccurrence(occurrence);
        today.updateOccurrence(reopened);
        flows.onOccurrenceReopened(reopened);
        if (task.ongoing && task.conditionDone) catalog.updateTask(task.reopenCondition());
        if (occurrence.kind != OccurrenceKind.FLOW_SHEET) projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.HEAD);
    }

    private void projectSchedule(TaskId taskId) {
        Task task = catalog.findTask(taskId);
        if (task == null) return;
        catalog.updateTask(schedules.project(task, new ScheduleProjector.Input(
                today.earliestOpenOccurrence(taskId),
                today.latestCompletedOccurrence(taskId))));
    }

    private RewardBooking activeOriginal(String occurrenceId, String stepId,
                                         RewardBooking.Target target) {
        List<RewardBooking> bookings = today.rewardBookings(occurrenceId);
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
        ComboProgress combo = today.combo(owner);
        if (combo != null) return combo;
        combo = ComboProgress.fresh(owner, taskId, kind);
        today.putCombo(combo);
        return combo;
    }

    private static String newId() { return UUID.randomUUID().toString(); }
}

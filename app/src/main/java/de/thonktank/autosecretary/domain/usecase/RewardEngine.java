package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardPolicy;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RewardEngine {
    private final TaskRepository repository;
    private final Clock clock;

    RewardEngine(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    static String newTransactionId() { return UUID.randomUUID().toString(); }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step) {
        return completeStep(occurrence, step, newTransactionId());
    }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step, String transactionId) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step == null
                || activeOriginal(occurrence.id, step.id, RewardBooking.Target.VESSEL) != null)
            return RewardReceipt.none();
        ComboProgress settled = combo(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP).settle(clock.today());
        int requested = occurrence.scheduledOn.equals(clock.today()) ? 1 : 0;
        ComboProgress.Change change = settled.change(requested, clock.today());
        RewardBooking booking = new RewardBooking(newTransactionId(), transactionId,
                occurrence.id, step.id, step.comboOwnerId, RewardBooking.Kind.STEP_EARNED,
                RewardBooking.Target.VESSEL, RewardPolicy.stepXp(settled),
                change.appliedDelta, clock.today(), null);
        repository.putCombo(change.progress);
        repository.insertRewardBooking(booking);
        repository.updateOccurrenceStep(step.complete());
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.VESSEL);
    }

    RewardReceipt undoStep(Occurrence occurrence, OccurrenceStep step) {
        String transactionId = newTransactionId();
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step == null
                || !step.done) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, step.id,
                RewardBooking.Target.VESSEL);
        if (original == null) return RewardReceipt.none();
        RewardBooking reversal = original.reverse(newTransactionId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, occurrence.taskId, ComboProgress.Kind.STEP);
        repository.putCombo(current.undo(original.comboPointDelta, clock.today()));
        repository.insertRewardBooking(reversal);
        repository.updateOccurrenceStep(step.reopen());
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.VESSEL);
    }

    RewardReceipt harvest(Occurrence occurrence, Task task) {
        return harvest(occurrence, task, newTransactionId());
    }

    RewardReceipt harvest(Occurrence occurrence, Task task, String transactionId) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.OPEN
                || activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD) != null)
            return RewardReceipt.none();
        List<OccurrenceStep> steps = repository.occurrenceSteps(occurrence.id);
        for (OccurrenceStep step : steps) if (!step.done) return RewardReceipt.none();
        int collected = netXp(repository.rewardBookings(occurrence.id), RewardBooking.Target.VESSEL);
        ComboProgress settled = combo(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK).settle(clock.today());
        boolean routine = !steps.isEmpty();
        int xp;
        int requestedDelta;
        RewardBooking.Kind kind;
        if (routine) {
            if (collected <= 0) return RewardReceipt.none();
            xp = RewardPolicy.routineXp(collected, settled);
            requestedDelta = 3;
            kind = RewardBooking.Kind.ROUTINE_HARVEST;
        } else {
            long late = RewardPolicy.lateDays(task, occurrence, clock.today());
            xp = RewardPolicy.singleTaskXp(late, settled);
            requestedDelta = late == 0 ? 3 : -2;
            kind = RewardBooking.Kind.SINGLE_COMPLETION;
        }
        ComboProgress.Change change = settled.change(requestedDelta, clock.today());
        RewardBooking booking = new RewardBooking(newTransactionId(), transactionId,
                occurrence.id, null, ComboProgress.taskOwner(task.id), kind,
                RewardBooking.Target.HEAD, xp, change.appliedDelta, clock.today(), null);
        repository.putCombo(change.progress);
        repository.setXp(repository.xp() + booking.xpDelta);
        repository.insertRewardBooking(booking);
        repository.updateOccurrence(occurrence.complete(clock.today()));
        reconcile(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.HEAD);
    }

    RewardReceipt undoHarvest(Occurrence occurrence, Task task) {
        String transactionId = newTransactionId();
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.COMPLETED
                || !clock.today().equals(occurrence.completedOn)) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD);
        if (original == null) return RewardReceipt.none();
        RewardBooking reversal = original.reverse(newTransactionId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, task.id, ComboProgress.Kind.TASK);
        repository.putCombo(current.undo(original.comboPointDelta, clock.today()));
        repository.setXp(repository.xp() + reversal.xpDelta);
        repository.insertRewardBooking(reversal);
        repository.updateOccurrence(occurrence.reopen());
        if (task.ongoing && task.conditionDone) repository.updateTask(task.reopenCondition());
        reconcile(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.HEAD);
    }

    private RewardBooking activeOriginal(String occurrenceId, String stepId,
                                         RewardBooking.Target target) {
        List<RewardBooking> bookings = repository.rewardBookings(occurrenceId);
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

    private static int netXp(List<RewardBooking> bookings, RewardBooking.Target target) {
        int total = 0;
        for (RewardBooking booking : bookings) if (booking.target == target) total += booking.xpDelta;
        return total;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private ComboProgress combo(String owner, TaskId taskId, ComboProgress.Kind kind) {
        ComboProgress combo = repository.combo(owner);
        if (combo != null) return combo;
        combo = ComboProgress.fresh(owner, taskId, kind);
        repository.putCombo(combo);
        return combo;
    }

    private void reconcile(TaskId taskId) {
        Task task = repository.findTask(taskId);
        if (task == null) return;
        List<Occurrence> values = repository.occurrences(taskId);
        Occurrence earliestOpen = values.stream()
                .filter(value -> value.state == OccurrenceState.OPEN)
                .min(Comparator.comparing(value -> value.scheduledOn)).orElse(null);
        Occurrence latestDone = values.stream()
                .filter(value -> value.state == OccurrenceState.COMPLETED)
                .max(Comparator.comparing((Occurrence value) -> value.completedOn)
                        .thenComparing(value -> value.scheduledOn)).orElse(null);
        LocalDate next = earliestOpen != null ? earliestOpen.scheduledOn
                : latestDone == null ? task.nextDueOn
                : ScheduleCalculator.nextDue(task, latestDone.completedOn);
        boolean archived = task.recurrence == Recurrence.ONCE
                && earliestOpen == null && latestDone != null;
        repository.updateTask(task.withOccurrenceState(archived, next,
                latestDone == null ? null : latestDone.scheduledOn,
                latestDone == null ? null : latestDone.completedOn,
                latestDone != null));
    }
}

package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Transactional orchestrator for completion, reward projection, undo and scheduling. */
public final class CompletionService {
    private final TaskRepository repository;
    private final Clock clock;
    private final RewardCalculator rewards;
    private final CompletionStateMachine states;
    private final ScheduleProjector schedules;

    public CompletionService(TaskRepository repository, Clock clock) {
        this(repository, clock, new RewardCalculator(), new CompletionStateMachine(),
                new ScheduleProjector());
    }

    CompletionService(TaskRepository repository, Clock clock, RewardCalculator rewards,
                      CompletionStateMachine states, ScheduleProjector schedules) {
        this.repository = repository;
        this.clock = clock;
        this.rewards = rewards;
        this.states = states;
        this.schedules = schedules;
    }

    public RewardReceipt toggleStep(String stepId) {
        return repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null || step.amount instanceof StepAmount.SetsReps)
                return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            return step.done ? undoStep(occurrence, step) : completeStep(occurrence, step,
                    newId());
        });
    }

    public RewardReceipt confirmSet(String stepId, int repetitions) {
        return repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            OccurrenceStep changed = step.confirmSet(repetitions);
            repository.updateOccurrenceStep(changed);
            return !step.done && changed.done
                    ? completeStep(occurrence, changed, newId()) : RewardReceipt.none();
        });
    }

    public RewardReceipt finishExercise(String stepId) {
        return repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            Occurrence occurrence = step == null ? null : repository.findOccurrence(step.occurrenceId);
            return completeStep(occurrence, step, newId());
        });
    }

    public RewardReceipt reopenExercise(String stepId, List<Integer> repetitions) {
        return repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null || !(step.amount instanceof StepAmount.SetsReps) || !step.done)
                return RewardReceipt.none();
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            OccurrenceStep edited = step.withActualRepetitions(repetitions);
            repository.updateOccurrenceStep(edited);
            return undoStep(occurrence, edited);
        });
    }

    public RewardReceipt completeRemainingSteps(String occurrenceId) {
        return repository.inTransaction(() -> {
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(completeStep(occurrence, step,
                        transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.VESSEL);
        });
    }

    public RewardReceipt completeOccurrence(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return RewardReceipt.none();
        return repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
                return RewardReceipt.none();
            Task task = repository.findTask(occurrence.taskId);
            if (task == null) return RewardReceipt.none();
            String transactionId = newId();
            List<RewardBooking> bookings = new ArrayList<>();
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
                if (!step.done) bookings.addAll(completeStep(occurrence, step,
                        transactionId).bookings);
            bookings.addAll(harvest(repository.findOccurrence(occurrenceId), task,
                    transactionId).bookings);
            return RewardReceipt.of(transactionId, bookings, RewardReceipt.Target.HEAD);
        });
    }

    public RewardReceipt harvestOccurrence(String occurrenceId) {
        return repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : repository.findTask(occurrence.taskId);
            return harvest(occurrence, task, newId());
        });
    }

    public RewardReceipt undoOccurrence(String occurrenceId) {
        return repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            Task task = occurrence == null ? null : repository.findTask(occurrence.taskId);
            return undoHarvest(occurrence, task);
        });
    }

    public RewardReceipt closeCondition(TaskId taskId) {
        return repository.inTransaction(() -> {
            Task task = repository.findTask(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty()
                    || task.conditionDone) return RewardReceipt.none();
            Occurrence open = repository.openOccurrence(task.id);
            if (open == null) {
                open = new Occurrence("condition:" + task.id.value + ":" + clock.today(),
                        task.id, clock.today(), task.slot, OccurrenceState.OPEN,
                        Integer.MAX_VALUE, null, OccurrenceKind.CONDITION);
                repository.insertOccurrence(open);
            } else if (open.kind != OccurrenceKind.CONDITION) {
                open = new Occurrence(open.id, open.taskId, open.scheduledOn, open.slot,
                        open.state, open.sortOrder, open.completedOn, OccurrenceKind.CONDITION);
            }
            RewardReceipt receipt = harvest(open, task, newId());
            Task projected = repository.findTask(task.id);
            repository.updateTask(projected.closeCondition(clock.today()));
            return receipt;
        });
    }

    private RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step,
                                       String transactionId) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step == null
                || activeOriginal(occurrence.id, step.id, RewardBooking.Target.VESSEL) != null)
            return RewardReceipt.none();
        ComboProgress settled = combo(step.comboOwnerId, occurrence.taskId,
                ComboProgress.Kind.STEP).settle(clock.today());
        RewardCalculator.StepReward calculated = rewards.step(settled,
                occurrence.scheduledOn.equals(clock.today()));
        ComboProgress.Change change = settled.change(calculated.requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(newId(), transactionId, occurrence.id, step.id,
                step.comboOwnerId, RewardBooking.Kind.STEP_EARNED, RewardBooking.Target.VESSEL,
                calculated.xp, change.appliedDelta, clock.today(), null);
        repository.putCombo(change.progress);
        repository.insertRewardBooking(booking);
        repository.updateOccurrenceStep(states.completeStep(occurrence, step));
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.VESSEL);
    }

    private RewardReceipt undoStep(Occurrence occurrence, OccurrenceStep step) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || step == null
                || !step.done) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, step.id,
                RewardBooking.Target.VESSEL);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, occurrence.taskId, ComboProgress.Kind.STEP);
        repository.putCombo(current.undo(original.comboPointDelta, clock.today()));
        repository.insertRewardBooking(reversal);
        repository.updateOccurrenceStep(states.reopenStep(occurrence, step));
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.VESSEL);
    }

    private RewardReceipt harvest(Occurrence occurrence, Task task, String transactionId) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.OPEN
                || activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD) != null)
            return RewardReceipt.none();
        List<OccurrenceStep> steps = repository.occurrenceSteps(occurrence.id);
        for (OccurrenceStep step : steps) if (!step.done) return RewardReceipt.none();
        int collected = netXp(repository.rewardBookings(occurrence.id), RewardBooking.Target.VESSEL);
        boolean routine = !steps.isEmpty();
        if (routine && collected <= 0) return RewardReceipt.none();
        ComboProgress settled = combo(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK).settle(clock.today());
        RewardCalculator.HarvestReward calculated = rewards.harvest(task, occurrence, routine,
                collected, settled, clock.today());
        ComboProgress.Change change = settled.change(calculated.requestedComboDelta, clock.today());
        RewardBooking booking = new RewardBooking(newId(), transactionId, occurrence.id, null,
                ComboProgress.taskOwner(task.id), calculated.kind, RewardBooking.Target.HEAD,
                calculated.xp, change.appliedDelta, clock.today(), null);
        repository.putCombo(change.progress);
        repository.setXp(repository.xp() + booking.xpDelta);
        repository.insertRewardBooking(booking);
        repository.updateOccurrence(states.completeOccurrence(occurrence, clock.today()));
        projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(booking),
                RewardReceipt.Target.HEAD);
    }

    private RewardReceipt undoHarvest(Occurrence occurrence, Task task) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.COMPLETED
                || !clock.today().equals(occurrence.completedOn)) return RewardReceipt.none();
        RewardBooking original = activeOriginal(occurrence.id, null, RewardBooking.Target.HEAD);
        if (original == null) return RewardReceipt.none();
        String transactionId = newId();
        RewardBooking reversal = original.reverse(newId(), transactionId, clock.today());
        ComboProgress current = combo(original.ownerId, task.id, ComboProgress.Kind.TASK);
        repository.putCombo(current.undo(original.comboPointDelta, clock.today()));
        repository.setXp(repository.xp() + reversal.xpDelta);
        repository.insertRewardBooking(reversal);
        repository.updateOccurrence(states.reopenOccurrence(occurrence));
        if (task.ongoing && task.conditionDone) repository.updateTask(task.reopenCondition());
        projectSchedule(task.id);
        return RewardReceipt.of(transactionId, Collections.singletonList(reversal),
                RewardReceipt.Target.HEAD);
    }

    private void projectSchedule(TaskId taskId) {
        Task task = repository.findTask(taskId);
        if (task == null) return;
        repository.updateTask(schedules.project(task,
                repository.earliestOpenOccurrence(taskId),
                repository.latestCompletedOccurrence(taskId)));
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

    private static String newId() { return UUID.randomUUID().toString(); }
}

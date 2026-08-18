package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.RewardPolicy;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

final class RewardEngine {
    private final TaskRepository repository;
    private final Clock clock;

    RewardEngine(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    RewardReceipt completeStep(Occurrence occurrence, OccurrenceStep step) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN
                || step.done && step.earnedXp > 0)
            return RewardReceipt.none();
        ComboProgress combo = combo(step.comboOwnerId, occurrence.taskId, ComboProgress.Kind.STEP);
        ComboProgress settled = combo.settle(clock.today());
        int xp = RewardPolicy.stepXp(settled);
        int requested = occurrence.scheduledOn.equals(clock.today()) ? 1 : 0;
        ComboProgress.Change change = settled.change(requested, clock.today());
        repository.putCombo(change.progress);
        repository.updateOccurrenceStep(step.award(xp, change.appliedDelta));
        return new RewardReceipt(xp, change.appliedDelta,
                RewardReceipt.Target.VESSEL, false);
    }

    RewardReceipt undoStep(Occurrence occurrence, OccurrenceStep step) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN || !step.done)
            return RewardReceipt.none();
        ComboProgress combo = combo(step.comboOwnerId, occurrence.taskId, ComboProgress.Kind.STEP);
        repository.putCombo(combo.undo(step.comboPointDelta, clock.today()));
        repository.updateOccurrenceStep(step.resetReward());
        return new RewardReceipt(step.earnedXp, -step.comboPointDelta,
                RewardReceipt.Target.VESSEL, true);
    }

    RewardReceipt harvest(Occurrence occurrence, Task task) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.OPEN)
            return RewardReceipt.none();
        List<OccurrenceStep> steps = repository.occurrenceSteps(occurrence.id);
        int collected = 0;
        for (OccurrenceStep step : steps) {
            if (!step.done) return RewardReceipt.none();
            collected += step.earnedXp;
        }
        ComboProgress combo = combo(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK).settle(clock.today());
        boolean routine = !steps.isEmpty();
        int xp;
        int requestedDelta;
        if (routine) {
            if (collected <= 0) return RewardReceipt.none();
            xp = RewardPolicy.routineXp(collected, combo);
            requestedDelta = 3;
        } else {
            long late = RewardPolicy.lateDays(task, occurrence, clock.today());
            xp = RewardPolicy.singleTaskXp(late, combo);
            requestedDelta = late == 0 ? 3 : -2;
        }
        ComboProgress.Change change = combo.change(requestedDelta, clock.today());
        repository.putCombo(change.progress);
        repository.setXp(repository.xp() + xp);
        repository.updateOccurrence(occurrence.harvest(clock.today(), xp, change.appliedDelta));
        reconcile(task.id);
        return new RewardReceipt(xp, change.appliedDelta,
                RewardReceipt.Target.HEAD, false);
    }

    RewardReceipt undoHarvest(Occurrence occurrence, Task task) {
        if (occurrence == null || task == null || occurrence.state != OccurrenceState.COMPLETED
                || !clock.today().equals(occurrence.completedOn)) return RewardReceipt.none();
        ComboProgress combo = combo(ComboProgress.taskOwner(task.id), task.id,
                ComboProgress.Kind.TASK);
        repository.putCombo(combo.undo(occurrence.comboPointDelta, clock.today()));
        repository.setXp(Math.max(0, repository.xp() - occurrence.awardedXp));
        repository.updateOccurrence(occurrence.reopen());
        if (task.ongoing && task.conditionDone) repository.updateTask(task.reopenCondition());
        reconcile(task.id);
        return new RewardReceipt(occurrence.awardedXp, -occurrence.comboPointDelta,
                RewardReceipt.Target.HEAD, true);
    }

    private ComboProgress combo(String owner, de.thonktank.autosecretary.domain.model.TaskId taskId,
                                ComboProgress.Kind kind) {
        ComboProgress combo = repository.combo(owner);
        if (combo != null) return combo;
        combo = ComboProgress.fresh(owner, taskId, kind);
        repository.putCombo(combo);
        return combo;
    }

    private void reconcile(de.thonktank.autosecretary.domain.model.TaskId taskId) {
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

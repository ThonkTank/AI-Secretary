package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.ScheduleCalculator;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RoutineProgress;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;

final class OccurrenceCompletion {
    static final int XP_PER_COMPLETION = 10;

    private final TaskRepository repository;

    OccurrenceCompletion(TaskRepository repository) {
        this.repository = repository;
    }

    void execute(Occurrence occurrence, Task task, LocalDate completedOn) {
        for (OccurrenceStep step : repository.occurrenceSteps(occurrence.id))
            if (!step.done) repository.updateOccurrenceStep(step.complete());
        repository.updateOccurrence(occurrence.complete(completedOn));
        repository.setXp(repository.xp() + XP_PER_COMPLETION);

        RoutineProgress progress = task.routineProgress;
        if (task.recurrence != Recurrence.ONCE) {
            boolean onTime = occurrence.scheduledOn.equals(completedOn);
            boolean previousOnTime = task.hasCompletedOccurrence
                    && task.lastScheduledOn != null
                    && task.lastScheduledOn.equals(task.lastCompletedOn);
            progress = progress.recordCompletion(onTime, previousOnTime, completedOn);
        }
        LocalDate next = ScheduleCalculator.nextDue(task, completedOn);
        boolean archive = !task.ongoing && task.recurrence == Recurrence.ONCE;
        repository.updateTask(task.afterOccurrence(
                occurrence.scheduledOn, completedOn, next, progress, archive));
    }
}

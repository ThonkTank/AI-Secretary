package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.OccurrenceEntity;
import de.thonktank.autosecretary.OccurrenceStepEntity;
import de.thonktank.autosecretary.TaskEntity;
import de.thonktank.autosecretary.TaskStepEntity;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RoutineProgress;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.LocalDate;

public final class TaskEntityMapper {
    public Task toDomain(TaskEntity entity) {
        return Task.restore(TaskId.of(entity.id), entity.title, TaskSlot.fromStorage(entity.slot),
                Recurrence.fromStorage(entity.recurrence), entity.intervalDays, entity.weekdayMask,
                entity.ongoing, entity.conditionText, entity.conditionDone, entity.archived,
                date(entity.nextDueOn), date(entity.lastScheduledOn), date(entity.lastCompletedOn),
                new RoutineProgress(entity.routineLevel, entity.routineStreak,
                        entity.routineStreakWeeks, date(entity.lastStreakWeek)),
                entity.displayOrder, entity.hasCompletedOccurrence);
    }

    public TaskEntity toEntity(Task task) {
        return new TaskEntity(task.id.value, task.title, task.slot.storageCode,
                task.recurrence.storageCode(), task.intervalDays, task.weekdayMask, task.ongoing,
                task.conditionText, task.conditionDone, task.archived, text(task.nextDueOn),
                text(task.lastScheduledOn), text(task.lastCompletedOn), task.routineProgress.level,
                task.routineProgress.occurrenceStreak, task.routineProgress.weekStreak,
                text(task.routineProgress.lastCountedWeek), task.displayOrder,
                task.hasCompletedOccurrence);
    }

    public Occurrence toDomain(OccurrenceEntity entity) {
        return new Occurrence(entity.id, TaskId.of(entity.taskId), LocalDate.parse(entity.scheduledOn),
                OccurrenceState.fromStorage(entity.state), entity.sortOrder, date(entity.completedOn));
    }

    public OccurrenceEntity toEntity(Occurrence occurrence) {
        return new OccurrenceEntity(occurrence.id, occurrence.taskId.value,
                occurrence.scheduledOn.toString(), occurrence.state.storageCode(),
                occurrence.sortOrder, text(occurrence.completedOn));
    }

    public TaskStepTemplate toDomain(TaskStepEntity entity) {
        return new TaskStepTemplate(entity.id, TaskId.of(entity.taskId), entity.position, entity.text);
    }

    public TaskStepEntity toEntity(TaskStepTemplate step) {
        return new TaskStepEntity(step.id, step.taskId.value, step.position, step.text);
    }

    public OccurrenceStep toDomain(OccurrenceStepEntity entity) {
        return new OccurrenceStep(entity.id, entity.occurrenceId, entity.position, entity.text, entity.done);
    }

    public OccurrenceStepEntity toEntity(OccurrenceStep step) {
        return new OccurrenceStepEntity(step.id, step.occurrenceId, step.position, step.text, step.done);
    }

    private static LocalDate date(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    private static String text(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}

package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.OccurrenceEntity;
import de.thonktank.autosecretary.OccurrenceStepEntity;
import de.thonktank.autosecretary.TaskEntity;
import de.thonktank.autosecretary.TaskStepEntity;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.RepetitionProgressCodec;

import java.time.LocalDate;

public final class TaskEntityMapper {
    public Task toDomain(TaskEntity entity) {
        TaskSlot slot = TaskSlot.fromStorage(entity.slot);
        int times = entity.timeOfDayMask;
        if (times == 0 && !"ONCE".equalsIgnoreCase(entity.recurrence))
            times = de.thonktank.autosecretary.domain.model.TimeOfDay.fromSlot(slot).bit;
        return Task.restore(TaskId.of(entity.id), entity.title, slot,
                Recurrence.fromStorage(entity.recurrence), entity.intervalDays, entity.weekdayMask,
                entity.ongoing, entity.conditionText, entity.conditionDone, entity.archived,
                date(entity.nextDueOn), date(entity.lastScheduledOn), date(entity.lastCompletedOn),
                entity.displayOrder, entity.hasCompletedOccurrence, entity.estimatedMinutes,
                times, TaskBoundKind.fromStorage(entity.boundKind),
                date(entity.boundUntilOn), entity.boundWeeks, entity.remainingCount,
                date(entity.deadlineOn), entity.note);
    }

    public TaskEntity toEntity(Task task) {
        return new TaskEntity(task.id.value, task.title, task.slot.storageCode,
                task.recurrence.storageCode(), task.intervalDays, task.weekdayMask, task.ongoing,
                task.conditionText, task.conditionDone, task.archived, text(task.nextDueOn),
                text(task.lastScheduledOn), text(task.lastCompletedOn), task.displayOrder,
                task.hasCompletedOccurrence, task.estimatedMinutes, task.timeOfDayMask,
                task.boundKind.storageCode(), text(task.boundUntilOn), task.boundWeeks,
                task.remainingCount, text(task.deadlineOn), task.note);
    }

    public Occurrence toDomain(OccurrenceEntity entity) {
        return new Occurrence(entity.id, TaskId.of(entity.taskId), LocalDate.parse(entity.scheduledOn),
                TaskSlot.fromStorage(entity.slot), OccurrenceState.fromStorage(entity.state),
                entity.sortOrder, date(entity.completedOn), entity.awardedXp,
                entity.comboPointDelta);
    }

    public OccurrenceEntity toEntity(Occurrence occurrence) {
        return new OccurrenceEntity(occurrence.id, occurrence.taskId.value,
                occurrence.scheduledOn.toString(), occurrence.state.storageCode(),
                occurrence.sortOrder, text(occurrence.completedOn), occurrence.slot.storageCode,
                occurrence.awardedXp, occurrence.comboPointDelta);
    }

    public TaskStepTemplate toDomain(TaskStepEntity entity) {
        return new TaskStepTemplate(entity.id, TaskId.of(entity.taskId), entity.position, entity.text,
                entity.weekdayMask, StepAmountKind.fromStorage(entity.amountKind),
                entity.plannedSets, entity.plannedReps, entity.plannedDurationSeconds, entity.note);
    }

    public TaskStepEntity toEntity(TaskStepTemplate step) {
        return new TaskStepEntity(step.id, step.taskId.value, step.position, step.text,
                step.weekdayMask, step.amountKind.storageCode(), step.plannedSets,
                step.plannedReps, step.plannedDurationSeconds, step.note);
    }

    public OccurrenceStep toDomain(OccurrenceStepEntity entity) {
        return new OccurrenceStep(entity.id, entity.occurrenceId, entity.position, entity.text,
                entity.done, StepAmountKind.fromStorage(entity.amountKind), entity.plannedSets,
                entity.plannedReps, entity.plannedDurationSeconds, entity.note,
                RepetitionProgressCodec.decode(entity.actualRepetitions),
                entity.sourceTemplateId, entity.comboOwnerId, entity.earnedXp,
                entity.comboPointDelta);
    }

    public OccurrenceStepEntity toEntity(OccurrenceStep step) {
        return new OccurrenceStepEntity(step.id, step.occurrenceId, step.position, step.text,
                step.done, step.amountKind.storageCode(), step.plannedSets, step.plannedReps,
                step.plannedDurationSeconds, step.note,
                RepetitionProgressCodec.encode(step.actualRepetitions), step.sourceTemplateId,
                step.comboOwnerId, step.earnedXp, step.comboPointDelta);
    }

    private static LocalDate date(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    private static String text(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}

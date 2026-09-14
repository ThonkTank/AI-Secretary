package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.CarryForwardReason;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.SetResult;

import java.time.LocalDate;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

public final class TaskEntityMapper {
    public Task toDomain(TaskEntity entity) {
        return Task.restore(TaskId.of(entity.id), entity.title,
                Recurrence.fromStorage(entity.recurrence), entity.intervalDays, entity.weekdayMask,
                entity.ongoing, entity.conditionText, entity.conditionDone, entity.archived,
                date(entity.nextDueOn), date(entity.lastScheduledOn), date(entity.lastCompletedOn),
                date(entity.cadenceAnchorOn), entity.catalogOrder,
                entity.hasCompletedOccurrence, entity.estimatedMinutes,
                TaskBoundKind.fromStorage(entity.boundKind),
                date(entity.boundUntilOn), entity.boundWeeks, entity.remainingCount,
                date(entity.deadlineOn), entity.note,
                MissedOccurrenceMode.valueOf(entity.missedOccurrenceMode))
                .withKind(de.thonktank.autosecretary.domain.model.TaskKind.valueOf(entity.taskKind));
    }

    public TaskEntity toEntity(Task task) {
        TaskEntity entity = new TaskEntity(task.id.value, task.title, task.recurrence.storageCode(),
                task.intervalDays, task.weekdayMask, task.ongoing,
                task.conditionText, task.conditionDone, task.archived, text(task.nextDueOn),
                nullableText(task.cadenceAnchorOn), nullableText(task.lastScheduledOn),
                nullableText(task.lastCompletedOn),
                task.catalogOrder, task.hasCompletedOccurrence, task.estimatedMinutes,
                task.boundKind.storageCode(), nullableText(task.boundUntilOn), task.boundWeeks,
                task.remainingCount, nullableText(task.deadlineOn), task.note,
                task.missedOccurrenceMode.name());
        entity.taskKind = task.kind.name();
        return entity;
    }

    public Occurrence toDomain(OccurrenceEntity entity) {
        return new Occurrence(entity.id, TaskId.of(entity.taskId), LocalDate.parse(entity.scheduledOn),
                TaskSlot.fromStorage(entity.slot), OccurrenceState.fromStorage(entity.state),
                entity.sortOrder, date(entity.completedOn), OccurrenceKind.fromStorage(entity.kind),
                entity.sourceKey, entity.flowRunId, entity.flowExecutionSequence);
    }

    public OccurrenceEntity toEntity(Occurrence occurrence) {
        return new OccurrenceEntity(occurrence.id, occurrence.taskId.value,
                occurrence.scheduledOn.toString(), occurrence.state.storageCode(),
                occurrence.sortOrder, nullableText(occurrence.completedOn),
                occurrence.slot.storageCode, occurrence.kind.name(), occurrence.sourceKey,
                occurrence.flowRunId, occurrence.flowExecutionSequence);
    }

    public TaskStepTemplate toDomain(TaskStepEntity entity) {
        StepAmount amount = StepAmount.fromStorage(
                StepAmountKind.fromStorage(entity.amountKind), entity.plannedSets,
                entity.plannedReps, entity.plannedDurationSeconds);
        return new TaskStepTemplate(entity.id, TaskId.of(entity.taskId), entity.position, entity.text,
                entity.weekdayMask, entity.intervalDays,
                new StepPrescription(amount,
                        RestTimerPolicy.fromStorage(entity.restTimerMode, entity.restTimerSeconds)), entity.note,
                StepActivationKind.fromStorage(entity.activationKind));
    }

    public TaskStepEntity toEntity(TaskStepTemplate step) {
        StoredAmount amount = stored(step.prescription.amount);
        TaskStepEntity entity = new TaskStepEntity(step.id, step.taskId.value, step.position, step.text,
                step.weekdayMask, step.intervalDays, amount.kind.storageCode(), amount.sets,
                amount.repetitions, amount.durationSeconds, step.prescription.rest.mode.name(),
                step.prescription.rest.customSeconds, step.note,
                step.activationKind.storageCode());
        return entity;
    }

    public TaskScheduleEntry toDomain(TaskScheduleEntity entity) {
        return new TaskScheduleEntry(entity.id, TaskId.of(entity.taskId),
                TaskSlot.fromStorage(entity.slot), entity.displayOrder);
    }

    public TaskScheduleEntity toEntity(TaskScheduleEntry entry) {
        return new TaskScheduleEntity(entry.id, entry.taskId.value,
                entry.slot.storageCode, entry.displayOrder);
    }

    public OccurrenceStep toDomain(OccurrenceStepEntity entity, List<SetResult> results) {
        return OccurrenceStep.rehydrate(entity.id, entity.occurrenceId, entity.position, entity.text,
                entity.done, new StepPrescription(
                StepAmount.fromStorage(StepAmountKind.fromStorage(entity.amountKind),
                        entity.plannedSets, entity.plannedReps,
                        entity.plannedDurationSeconds),
                RestTimerPolicy.fromStorage(entity.restTimerMode, entity.restTimerSeconds)), entity.note,
                results,
                entity.sourceTemplateId, entity.comboOwnerId,
                entity.originOccurrenceId,
                CarryForwardReason.fromStorage(entity.carryForwardReason), entity.flowRunStepId);
    }

    public OccurrenceStepEntity toEntity(OccurrenceStep step) {
        StoredAmount amount = stored(step.prescription.amount);
        OccurrenceStepEntity entity = new OccurrenceStepEntity(step.id, step.occurrenceId, step.position, step.text,
                step.done, amount.kind.storageCode(), amount.sets, amount.repetitions,
                amount.durationSeconds, step.prescription.rest.mode.name(),
                step.prescription.rest.customSeconds, step.note, "", step.sourceTemplateId,
                step.comboOwnerId, step.originOccurrenceId,
                step.carryForwardReason.storageCode());
        entity.flowRunStepId = step.flowRunStepId;
        return entity;
    }

    private static StoredAmount stored(StepAmount amount) {
        if (amount instanceof StepAmount.SetsReps) {
            StepAmount.SetsReps value = (StepAmount.SetsReps) amount;
            return new StoredAmount(amount.kind(), value.sets, value.repetitions, null);
        }
        if (amount instanceof StepAmount.Repetitions)
            return new StoredAmount(amount.kind(), null,
                    ((StepAmount.Repetitions) amount).repetitions, null);
        if (amount instanceof StepAmount.Duration)
            return new StoredAmount(amount.kind(), null, null,
                    ((StepAmount.Duration) amount).seconds);
        return new StoredAmount(StepAmountKind.NONE, null, null, null);
    }

    private static final class StoredAmount {
        final StepAmountKind kind;
        final Integer sets;
        final Integer repetitions;
        final Integer durationSeconds;

        StoredAmount(StepAmountKind kind, Integer sets, Integer repetitions,
                     Integer durationSeconds) {
            this.kind = kind;
            this.sets = sets;
            this.repetitions = repetitions;
            this.durationSeconds = durationSeconds;
        }
    }

    private static LocalDate date(String value) {
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    private static String text(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String nullableText(LocalDate value) {
        return value == null ? null : value.toString();
    }
}

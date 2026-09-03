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
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;
import de.thonktank.autosecretary.domain.model.StepPrescription;
import de.thonktank.autosecretary.domain.model.SetResult;
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy;
import de.thonktank.autosecretary.domain.model.TrainingAssistantProfile;

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
                MissedOccurrenceMode.valueOf(entity.missedOccurrenceMode));
    }

    public TaskEntity toEntity(Task task) {
        return new TaskEntity(task.id.value, task.title, task.recurrence.storageCode(),
                task.intervalDays, task.weekdayMask, task.ongoing,
                task.conditionText, task.conditionDone, task.archived, text(task.nextDueOn),
                nullableText(task.cadenceAnchorOn), nullableText(task.lastScheduledOn),
                nullableText(task.lastCompletedOn),
                task.catalogOrder, task.hasCompletedOccurrence, task.estimatedMinutes,
                task.boundKind.storageCode(), nullableText(task.boundUntilOn), task.boundWeeks,
                task.remainingCount, nullableText(task.deadlineOn), task.note,
                task.missedOccurrenceMode.name());
    }

    public Occurrence toDomain(OccurrenceEntity entity) {
        return new Occurrence(entity.id, TaskId.of(entity.taskId), LocalDate.parse(entity.scheduledOn),
                TaskSlot.fromStorage(entity.slot), OccurrenceState.fromStorage(entity.state),
                entity.sortOrder, date(entity.completedOn), OccurrenceKind.fromStorage(entity.kind),
                entity.sourceKey, entity.flowRunId, entity.flowSheetSequence);
    }

    public OccurrenceEntity toEntity(Occurrence occurrence) {
        return new OccurrenceEntity(occurrence.id, occurrence.taskId.value,
                occurrence.scheduledOn.toString(), occurrence.state.storageCode(),
                occurrence.sortOrder, nullableText(occurrence.completedOn),
                occurrence.slot.storageCode, occurrence.kind.name(), occurrence.sourceKey,
                occurrence.flowRunId, occurrence.flowSheetSequence);
    }

    public TaskStepTemplate toDomain(TaskStepEntity entity) {
        StepAmount amount = StepAmount.fromStorage(
                StepAmountKind.fromStorage(entity.amountKind), entity.plannedSets,
                entity.plannedReps, entity.plannedDurationSeconds);
        ResistanceLoad load = ResistanceLoad.restore(entity.plannedLoadMode,
                entity.plannedLoadUnit, entity.plannedLoadMilli);
        return new TaskStepTemplate(entity.id, TaskId.of(entity.taskId), entity.position, entity.text,
                entity.weekdayMask, entity.intervalDays,
                StepPrescription.restore(amount,
                        RestTimerPolicy.fromStorage(entity.restTimerMode, entity.restTimerSeconds),
                        load, entity.assistantTargetRir), profile(entity), entity.note,
                StepActivationKind.fromStorage(entity.activationKind));
    }

    public TaskStepEntity toEntity(TaskStepTemplate step) {
        StoredAmount amount = stored(step.prescription.amount);
        TaskStepEntity entity = new TaskStepEntity(step.id, step.taskId.value, step.position, step.text,
                step.weekdayMask, step.intervalDays, amount.kind.storageCode(), amount.sets,
                amount.repetitions, amount.durationSeconds, step.prescription.rest.mode.name(),
                step.prescription.rest.customSeconds, step.note,
                step.activationKind.storageCode());
        TrainingAssistantPolicy policy = step.assistantProfile == null
                ? null : step.assistantProfile.policy;
        entity.assistantEnabled = policy != null;
        entity.assistantMinSets = policy == null ? 2 : policy.minSets;
        entity.assistantMaxSets = policy == null ? 3 : policy.maxSets;
        entity.assistantMinReps = policy == null ? 8 : policy.minRepetitions;
        entity.assistantMaxReps = policy == null ? 12 : policy.maxRepetitions;
        entity.assistantTargetRir = step.prescription.targetRir();
        entity.assistantWeeklySetCeiling = policy == null
                ? 10 : policy.automaticWeeklySetCeiling;
        ResistanceLoad load = step.prescription.plannedLoad();
        entity.plannedLoadMode = load.mode.name();
        entity.plannedLoadUnit = load.unit.name();
        entity.plannedLoadMilli = load.milliUnits;
        entity.primaryMuscle = policy == null || policy.primaryMuscle == null
                ? null : policy.primaryMuscle.name();
        entity.secondaryMuscles = policy == null ? "" : muscles(policy.secondaryMuscles);
        TrainingAssistantState state = step.assistantProfile == null
                ? TrainingAssistantState.disabled() : step.assistantProfile.state;
        entity.assistantStatus = state.status.name();
        entity.assistantObservations = state.eligibleObservations;
        entity.assistantReadyStreak = state.readyStreak;
        entity.assistantHardStreak = state.hardStreak;
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
                entity.done, StepPrescription.restore(
                StepAmount.fromStorage(StepAmountKind.fromStorage(entity.amountKind),
                        entity.plannedSets, entity.plannedReps,
                        entity.plannedDurationSeconds),
                RestTimerPolicy.fromStorage(entity.restTimerMode, entity.restTimerSeconds),
                ResistanceLoad.restore(entity.plannedLoadMode, entity.plannedLoadUnit,
                        entity.plannedLoadMilli), entity.targetRir), entity.note,
                results,
                entity.sourceTemplateId, entity.comboOwnerId,
                entity.originOccurrenceId,
                CarryForwardReason.fromStorage(entity.carryForwardReason));
    }

    public OccurrenceStepEntity toEntity(OccurrenceStep step) {
        StoredAmount amount = stored(step.prescription.amount);
        OccurrenceStepEntity entity = new OccurrenceStepEntity(step.id, step.occurrenceId, step.position, step.text,
                step.done, amount.kind.storageCode(), amount.sets, amount.repetitions,
                amount.durationSeconds, step.prescription.rest.mode.name(),
                step.prescription.rest.customSeconds, step.note, "", step.sourceTemplateId,
                step.comboOwnerId, step.originOccurrenceId,
                step.carryForwardReason.storageCode());
        ResistanceLoad load = step.prescription.plannedLoad();
        entity.plannedLoadMode = load.mode.name();
        entity.plannedLoadUnit = load.unit.name();
        entity.plannedLoadMilli = load.milliUnits;
        entity.targetRir = step.prescription.targetRir();
        return entity;
    }

    private static TrainingAssistantProfile profile(TaskStepEntity entity) {
        if (!entity.assistantEnabled) return null;
        TrainingAssistantPolicy policy = new TrainingAssistantPolicy(entity.assistantMinSets,
                entity.assistantMaxSets, entity.assistantMinReps, entity.assistantMaxReps,
                entity.assistantWeeklySetCeiling, muscle(entity.primaryMuscle),
                parseMuscles(entity.secondaryMuscles));
        TrainingAssistantState state = TrainingAssistantState.restore(entity.assistantStatus,
                entity.assistantObservations, entity.assistantReadyStreak,
                entity.assistantHardStreak);
        if (state.status == TrainingAssistantState.Status.DISABLED)
            state = TrainingAssistantState.calibrating();
        return new TrainingAssistantProfile(policy, state);
    }

    private static TrainingMuscleGroup muscle(String value) {
        try { return value == null ? null : TrainingMuscleGroup.valueOf(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static Set<TrainingMuscleGroup> parseMuscles(String value) {
        EnumSet<TrainingMuscleGroup> result = EnumSet.noneOf(TrainingMuscleGroup.class);
        if (value == null || value.isEmpty()) return result;
        for (String part : value.split(",")) {
            TrainingMuscleGroup muscle = muscle(part);
            if (muscle != null) result.add(muscle);
        }
        return result;
    }

    private static String muscles(Set<TrainingMuscleGroup> values) {
        StringJoiner result = new StringJoiner(",");
        for (TrainingMuscleGroup value : values) result.add(value.name());
        return result.toString();
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

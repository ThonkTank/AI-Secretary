package de.thonktank.autosecretary.data.local;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunResourceSnapshot;
import de.thonktank.autosecretary.domain.model.FlowRunStepSnapshot;
import de.thonktank.autosecretary.domain.model.RestTimerPolicy;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.model.StepFlowRun;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;

final class StepFlowEntityMapper {
    CapacityResource toDomain(CapacityResourceEntity value) {
        return new CapacityResource(value.id, value.name, value.capacity);
    }

    CapacityResourceEntity toEntity(CapacityResource value) {
        return new CapacityResourceEntity(value.id, value.name, value.normalizedName,
                value.capacity);
    }

    StepTransition toDomain(StepTransitionEntity value) {
        return new StepTransition(value.sourceStepId, value.targetStepId,
                new FlowDelayPolicy(FlowDelayPolicy.Mode.valueOf(value.delayMode),
                        value.defaultDelayMillis, value.lastUsedDelayMillis));
    }

    StepTransitionEntity toEntity(StepTransition value) {
        return new StepTransitionEntity(value.sourceStepId, value.targetStepId,
                value.delay.mode.name(), value.delay.defaultDelayMillis,
                value.delay.lastUsedDelayMillis);
    }

    StepResourceLease toDomain(StepResourceLeaseEntity value) {
        return new StepResourceLease(value.id, TaskId.of(value.taskId), value.acquireStepId,
                value.releaseStepId, value.resourceId, value.units);
    }

    StepResourceLeaseEntity toEntity(StepResourceLease value) {
        return new StepResourceLeaseEntity(value.id, value.taskId.value, value.acquireStepId,
                value.releaseStepId, value.resourceId, value.units);
    }

    StepFlowRun toDomain(StepFlowRunEntity value) {
        return new StepFlowRun(value.id, TaskId.of(value.taskId), value.seedStepId,
                value.sourceKey, LocalDate.parse(value.scheduledOn),
                TaskSlot.fromStorage(value.slot), StepFlowRunState.valueOf(value.state),
                value.currentPosition, value.readyAtEpochMillis, value.currentSheetOccurrenceId,
                value.queueOrder, value.nextSheetSequence, value.createdAtEpochMillis,
                value.updatedAtEpochMillis);
    }

    StepFlowRunEntity toEntity(StepFlowRun value) {
        return new StepFlowRunEntity(value.id, value.taskId.value, value.seedStepId,
                value.sourceKey, value.scheduledOn.toString(), value.slot.storageCode,
                value.state.name(), value.currentPosition, value.readyAtEpochMillis,
                value.currentSheetOccurrenceId, value.queueOrder, value.nextSheetSequence,
                value.createdAtEpochMillis, value.updatedAtEpochMillis);
    }

    FlowRunStepSnapshot toDomain(FlowRunStepEntity value) {
        FlowDelayPolicy delay = value.delayMode == null ? null : new FlowDelayPolicy(
                FlowDelayPolicy.Mode.valueOf(value.delayMode), value.defaultDelayMillis,
                value.lastUsedDelayMillis);
        return new FlowRunStepSnapshot(value.id, value.runId, value.position,
                value.sourceTemplateId, value.text, amount(value.amountKind, value.plannedSets,
                value.plannedReps, value.plannedDurationSeconds),
                RestTimerPolicy.fromStorage(value.restTimerMode, value.restTimerSeconds),
                ResistanceLoad.restore(value.plannedLoadMode, value.plannedLoadUnit,
                        value.plannedLoadMilli), value.targetRir, value.note, delay,
                value.chosenDelayMillis);
    }

    FlowRunStepEntity toEntity(FlowRunStepSnapshot value) {
        StoredAmount amount = stored(value.amount);
        return new FlowRunStepEntity(value.id, value.runId, value.position,
                value.sourceTemplateId, value.text, amount.kind.storageCode(), amount.sets,
                amount.repetitions, amount.durationSeconds, value.restTimerPolicy.mode.name(),
                value.restTimerPolicy.customSeconds, value.plannedLoad.mode.name(),
                value.plannedLoad.unit.name(), value.plannedLoad.milliUnits, value.targetRir,
                value.note,
                value.delayAfter == null ? null : value.delayAfter.mode.name(),
                value.delayAfter == null ? null : value.delayAfter.defaultDelayMillis,
                value.delayAfter == null ? null : value.delayAfter.lastUsedDelayMillis,
                value.chosenDelayMillis);
    }

    FlowRunResourceSnapshot toDomain(FlowRunResourceEntity value) {
        return new FlowRunResourceSnapshot(value.id, value.runId, value.sourceLeaseId,
                value.resourceId, value.resourceName, value.capacityAtCreation, value.units,
                value.acquirePosition, value.releasePosition,
                FlowResourceState.valueOf(value.state), value.reservedAtEpochMillis,
                value.activatedAtEpochMillis, value.releasedAtEpochMillis);
    }

    FlowRunResourceEntity toEntity(FlowRunResourceSnapshot value) {
        return new FlowRunResourceEntity(value.id, value.runId, value.sourceLeaseId,
                value.resourceId, value.resourceName, value.capacityAtCreation, value.units,
                value.acquirePosition, value.releasePosition, value.state.name(),
                value.reservedAtEpochMillis, value.activatedAtEpochMillis,
                value.releasedAtEpochMillis);
    }

    private static StepAmount amount(String kind, Integer sets, Integer repetitions,
                                     Integer durationSeconds) {
        return StepAmount.fromStorage(StepAmountKind.fromStorage(kind), sets, repetitions,
                durationSeconds);
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
}

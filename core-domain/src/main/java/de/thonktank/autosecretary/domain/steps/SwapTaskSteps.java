package de.thonktank.autosecretary.domain.steps;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.util.ArrayList;
import java.util.List;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Swaps two stable step definitions while preserving IDs, history and combo ownership. */
public final class SwapTaskSteps {
    private final StepOrganizationRepository repository;
    private final TransactionRunner transactions;

    public SwapTaskSteps(StepOrganizationRepository repository, TransactionRunner transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    public StepTransferResult execute(StepSwapRequest request) {
        return transactions.inTransaction(() -> swap(request));
    }

    private StepTransferResult swap(StepSwapRequest request) {
        TaskStepTemplate first = repository.findTemplate(request.stepId.value);
        TaskStepTemplate second = repository.findTemplate(request.targetStepId.value);
        if (first == null || second == null) return StepTransferResult.NOT_FOUND;
        if (first.id.equals(second.id)) return StepTransferResult.UNCHANGED;
        Task firstTask = StepTransferSupport.active(repository, first.taskId);
        Task secondTask = StepTransferSupport.active(repository, second.taskId);
        if (firstTask == null || secondTask == null)
            return StepTransferResult.REJECTED_ARCHIVED_TASK;
        List<TaskStepTemplate> firstSteps = new ArrayList<>(repository.templates(firstTask.id));
        List<TaskStepTemplate> secondSteps = firstTask.id.equals(secondTask.id)
                ? firstSteps : new ArrayList<>(repository.templates(secondTask.id));
        if (!StepTransferSupport.canonicalTemplates(firstSteps)
                || secondSteps != firstSteps && !StepTransferSupport.canonicalTemplates(secondSteps)
                || !StepTransferSupport.canonicalSnapshots(repository, firstTask.id)
                || !firstTask.id.equals(secondTask.id)
                && !StepTransferSupport.canonicalSnapshots(repository, secondTask.id))
            return StepTransferResult.REJECTED_INVALID_POSITION_SEQUENCE;

        if (firstSteps == secondSteps) {
            int firstIndex = StepTransferSupport.indexOf(firstSteps, first.id);
            int secondIndex = StepTransferSupport.indexOf(firstSteps, second.id);
            TaskStepTemplate value = firstSteps.get(firstIndex);
            firstSteps.set(firstIndex, firstSteps.get(secondIndex));
            firstSteps.set(secondIndex, value);
            repository.insertTemplates(StepTransferSupport.resequence(firstSteps, firstTask));
            StepTransferSupport.resequenceOpen(repository, firstTask.id);
            return StepTransferResult.STEPS_SWAPPED;
        }

        StepTransferSupport.remove(firstSteps, first.id);
        StepTransferSupport.remove(secondSteps, second.id);
        int firstIndex = Math.min(first.position, firstSteps.size());
        int secondIndex = Math.min(second.position, secondSteps.size());
        firstSteps.add(firstIndex, StepTransferSupport.reparent(second, firstTask, firstIndex));
        secondSteps.add(secondIndex, StepTransferSupport.reparent(first, secondTask, secondIndex));
        List<TaskStepTemplate> writes = new ArrayList<>();
        writes.addAll(StepTransferSupport.resequence(firstSteps, firstTask));
        writes.addAll(StepTransferSupport.resequence(secondSteps, secondTask));
        repository.insertTemplates(writes);
        StepTransferSupport.reparentCombo(repository, first.id, secondTask.id);
        StepTransferSupport.reparentCombo(repository, second.id, firstTask.id);

        for (Occurrence firstOccurrence : repository.openOccurrences(firstTask.id)) {
            Occurrence secondOccurrence = repository.openOccurrence(secondTask.id,
                    firstOccurrence.slot);
            if (secondOccurrence == null) continue;
            OccurrenceStep firstSnapshot = StepTransferSupport.snapshot(repository,
                    firstOccurrence.id, first.id);
            OccurrenceStep secondSnapshot = StepTransferSupport.snapshot(repository,
                    secondOccurrence.id, second.id);
            if (firstSnapshot == null || secondSnapshot == null) continue;
            repository.updateOccurrenceStep(firstSnapshot.relocate(secondOccurrence.id,
                    firstSnapshot.position));
            repository.assignRewardBookings(firstSnapshot.id, secondOccurrence.id);
            repository.updateOccurrenceStep(secondSnapshot.relocate(firstOccurrence.id,
                    secondSnapshot.position));
            repository.assignRewardBookings(secondSnapshot.id, firstOccurrence.id);
        }
        StepTransferSupport.resequenceOpen(repository, firstTask.id);
        StepTransferSupport.resequenceOpen(repository, secondTask.id);
        return StepTransferResult.STEPS_SWAPPED;
    }
}

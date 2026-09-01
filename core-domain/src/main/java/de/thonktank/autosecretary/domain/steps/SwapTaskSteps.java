package de.thonktank.autosecretary.domain.steps;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.util.ArrayList;
import java.util.List;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Swaps two stable step definitions while preserving IDs, history and combo ownership. */
public final class SwapTaskSteps {
    private final CatalogRepository catalog;
    private final StepRepository steps;
    private final TodayRepository today;
    private final TransactionRunner transactions;

    public SwapTaskSteps(CatalogRepository catalog, StepRepository steps,
                         TodayRepository today, TransactionRunner transactions) {
        this.catalog = catalog;
        this.steps = steps;
        this.today = today;
        this.transactions = transactions;
    }

    public StepTransferResult execute(StepSwapRequest request) {
        return transactions.inTransaction(() -> swap(request));
    }

    private StepTransferResult swap(StepSwapRequest request) {
        TaskStepTemplate first = steps.findTemplate(request.stepId.value);
        TaskStepTemplate second = steps.findTemplate(request.targetStepId.value);
        if (first == null || second == null) return StepTransferResult.NOT_FOUND;
        if (first.id.equals(second.id)) return StepTransferResult.UNCHANGED;
        Task firstTask = StepTransferSupport.active(catalog, first.taskId);
        Task secondTask = StepTransferSupport.active(catalog, second.taskId);
        if (firstTask == null || secondTask == null)
            return StepTransferResult.REJECTED_ARCHIVED_TASK;
        List<TaskStepTemplate> firstSteps = new ArrayList<>(steps.templates(firstTask.id));
        List<TaskStepTemplate> secondSteps = firstTask.id.equals(secondTask.id)
                ? firstSteps : new ArrayList<>(steps.templates(secondTask.id));
        if (!StepTransferSupport.canonicalTemplates(firstSteps)
                || secondSteps != firstSteps && !StepTransferSupport.canonicalTemplates(secondSteps)
                || !StepTransferSupport.canonicalSnapshots(steps, today, firstTask.id)
                || !firstTask.id.equals(secondTask.id)
                && !StepTransferSupport.canonicalSnapshots(steps, today, secondTask.id))
            return StepTransferResult.REJECTED_INVALID_POSITION_SEQUENCE;

        if (firstSteps == secondSteps) {
            int firstIndex = StepTransferSupport.indexOf(firstSteps, first.id);
            int secondIndex = StepTransferSupport.indexOf(firstSteps, second.id);
            TaskStepTemplate value = firstSteps.get(firstIndex);
            firstSteps.set(firstIndex, firstSteps.get(secondIndex));
            firstSteps.set(secondIndex, value);
            steps.insertTemplates(StepTransferSupport.resequence(firstSteps, firstTask));
            StepTransferSupport.resequenceOpen(steps, today, firstTask.id);
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
        steps.insertTemplates(writes);
        StepTransferSupport.reparentCombo(today, first.id, secondTask.id);
        StepTransferSupport.reparentCombo(today, second.id, firstTask.id);

        for (Occurrence firstOccurrence : today.openOccurrences(firstTask.id)) {
            Occurrence secondOccurrence = today.openOccurrence(secondTask.id,
                    firstOccurrence.slot);
            if (secondOccurrence == null) continue;
            OccurrenceStep firstSnapshot = StepTransferSupport.snapshot(steps,
                    firstOccurrence.id, first.id);
            OccurrenceStep secondSnapshot = StepTransferSupport.snapshot(steps,
                    secondOccurrence.id, second.id);
            if (firstSnapshot == null || secondSnapshot == null) continue;
            steps.updateOccurrenceStep(firstSnapshot.relocate(secondOccurrence.id,
                    firstSnapshot.position));
            today.assignRewardBookings(firstSnapshot.id, secondOccurrence.id);
            steps.updateOccurrenceStep(secondSnapshot.relocate(firstOccurrence.id,
                    secondSnapshot.position));
            today.assignRewardBookings(secondSnapshot.id, firstOccurrence.id);
        }
        StepTransferSupport.resequenceOpen(steps, today, firstTask.id);
        StepTransferSupport.resequenceOpen(steps, today, secondTask.id);
        return StepTransferResult.STEPS_SWAPPED;
    }
}

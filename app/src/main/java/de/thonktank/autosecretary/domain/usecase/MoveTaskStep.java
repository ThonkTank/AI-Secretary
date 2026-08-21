package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

/** Moves one stable step definition and only relocates a matching open snapshot. */
public final class MoveTaskStep {
    private final TaskRepository repository;

    public MoveTaskStep(TaskRepository repository) { this.repository = repository; }

    public StepTransferResult execute(StepMoveRequest request) {
        return repository.inTransaction(() -> move(request));
    }

    private StepTransferResult move(StepMoveRequest request) {
        TaskStepTemplate moving = repository.findTemplate(request.stepId.value);
        if (moving == null) return StepTransferResult.NOT_FOUND;
        Task sourceTask = StepTransferSupport.active(repository, moving.taskId);
        Task targetTask = StepTransferSupport.active(repository, request.targetTaskId);
        if (sourceTask == null || targetTask == null)
            return StepTransferResult.REJECTED_ARCHIVED_TASK;
        String beforeId = request.beforeStepId.map(value -> value.value).orElse(null);
        if (moving.id.equals(beforeId)) return StepTransferResult.UNCHANGED;
        TaskStepTemplate before = beforeId == null ? null : repository.findTemplate(beforeId);
        if (beforeId != null && before == null) return StepTransferResult.NOT_FOUND;
        if (before != null && !before.taskId.equals(targetTask.id))
            return StepTransferResult.REJECTED_OCCUPIED_TARGET;

        List<TaskStepTemplate> source = new ArrayList<>(repository.templates(sourceTask.id));
        List<TaskStepTemplate> originalSource = new ArrayList<>(source);
        List<TaskStepTemplate> target = sourceTask.id.equals(targetTask.id)
                ? source : new ArrayList<>(repository.templates(targetTask.id));
        if (!StepTransferSupport.canonicalTemplates(source)
                || target != source && !StepTransferSupport.canonicalTemplates(target)
                || !StepTransferSupport.canonicalSnapshots(repository, sourceTask.id)
                || !sourceTask.id.equals(targetTask.id)
                && !StepTransferSupport.canonicalSnapshots(repository, targetTask.id))
            return StepTransferResult.REJECTED_INVALID_POSITION_SEQUENCE;

        if (!sourceTask.id.equals(targetTask.id))
            for (Occurrence sourceOccurrence : repository.openOccurrences(sourceTask.id)) {
                Occurrence targetOccurrence = repository.openOccurrence(targetTask.id,
                        sourceOccurrence.slot);
                if (targetOccurrence != null && StepTransferSupport.snapshot(repository,
                        targetOccurrence.id, moving.id) != null)
                    return StepTransferResult.REJECTED_OCCUPIED_TARGET;
            }

        StepTransferSupport.remove(source, moving.id);
        int targetIndex = StepTransferSupport.indexOf(target, beforeId);
        if (targetIndex < 0) targetIndex = target.size();
        targetIndex = Math.max(0, Math.min(targetIndex, target.size()));
        target.add(targetIndex, StepTransferSupport.reparent(moving, targetTask, targetIndex));
        if (target == source && sameOrder(originalSource, source))
            return StepTransferResult.UNCHANGED;

        List<TaskStepTemplate> writes = new ArrayList<>();
        writes.addAll(StepTransferSupport.resequence(source, sourceTask));
        if (target != source) writes.addAll(StepTransferSupport.resequence(target, targetTask));
        repository.insertTemplates(writes);
        StepTransferSupport.reparentCombo(repository, moving.id, targetTask.id);

        boolean todayMoved = false;
        if (sourceTask.id.equals(targetTask.id)) {
            todayMoved = StepTransferSupport.resequenceOpen(repository, sourceTask.id);
        } else {
            for (Occurrence sourceOccurrence : repository.openOccurrences(sourceTask.id)) {
                Occurrence targetOccurrence = repository.openOccurrence(targetTask.id,
                        sourceOccurrence.slot);
                if (targetOccurrence == null) continue;
                OccurrenceStep sourceStep = StepTransferSupport.snapshot(repository,
                        sourceOccurrence.id, moving.id);
                if (sourceStep == null) continue;
                repository.updateOccurrenceStep(sourceStep.relocate(targetOccurrence.id,
                        sourceStep.position));
                repository.assignRewardBookings(sourceStep.id, targetOccurrence.id);
                todayMoved = true;
            }
            StepTransferSupport.resequenceOpen(repository, sourceTask.id);
            StepTransferSupport.resequenceOpen(repository, targetTask.id);
        }
        return todayMoved ? StepTransferResult.DEFINITION_AND_TODAY_MOVED
                : StepTransferResult.DEFINITION_ONLY_FOR_FUTURE;
    }

    private static boolean sameOrder(List<TaskStepTemplate> first,
                                     List<TaskStepTemplate> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++)
            if (!first.get(index).id.equals(second.get(index).id)) return false;
        return true;
    }
}

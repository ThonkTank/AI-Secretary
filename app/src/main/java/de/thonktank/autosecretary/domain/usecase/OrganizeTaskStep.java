package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reorders, moves or swaps stable step templates without resetting their combo history. */
public final class OrganizeTaskStep {
    private final TaskRepository repository;

    public OrganizeTaskStep(TaskRepository repository) { this.repository = repository; }

    public void move(String stepId, TaskId targetTaskId, String beforeStepId) {
        execute(stepId, targetTaskId, beforeStepId, false);
    }

    public void swap(String stepId, String targetStepId) {
        TaskStepTemplate target = find(targetStepId);
        if (target == null) return;
        execute(stepId, target.taskId, targetStepId, true);
    }

    private void execute(String stepId, TaskId targetTaskId, String targetStepId,
                         boolean swap) {
        repository.inTransaction(() -> {
            TaskStepTemplate moving = find(stepId);
            TaskStepTemplate targetStep = swap ? find(targetStepId) : null;
            if (moving == null || swap && targetStep == null || moving.id.equals(targetStepId))
                return null;
            Task sourceTask = active(moving.taskId);
            Task targetTask = active(targetTaskId);
            if (sourceTask == null || targetTask == null)
                throw new IllegalArgumentException("Schritte können nur zwischen aktiven Aufgaben verschoben werden.");

            List<TaskStepTemplate> source = new ArrayList<>(repository.templates(moving.taskId));
            List<TaskStepTemplate> target = moving.taskId.equals(targetTaskId)
                    ? source : new ArrayList<>(repository.templates(targetTaskId));
            if (swap && source == target) {
                int first = indexOf(source, moving.id);
                int second = indexOf(source, targetStep.id);
                if (first >= 0 && second >= 0) {
                    TaskStepTemplate value = source.get(first);
                    source.set(first, source.get(second));
                    source.set(second, value);
                    repository.insertTemplates(resequence(source, sourceTask));
                    resequenceOpen(sourceTask.id);
                }
                return null;
            }
            remove(source, moving.id);
            if (swap) {
                remove(target, targetStep.id);
                int sourceIndex = Math.min(moving.position, source.size());
                int targetIndex = Math.min(targetStep.position, target.size());
                source.add(sourceIndex, reparent(targetStep, sourceTask, sourceIndex));
                target.add(targetIndex, reparent(moving, targetTask, targetIndex));
            } else {
                int targetIndex = indexOf(target, targetStepId);
                if (targetIndex < 0) targetIndex = target.size();
                target.add(targetIndex, reparent(moving, targetTask, targetIndex));
            }
            List<TaskStepTemplate> writes = new ArrayList<>();
            writes.addAll(resequence(source, sourceTask));
            if (target != source) writes.addAll(resequence(target, targetTask));
            repository.insertTemplates(writes);
            reparentCombo(moving.id, targetTaskId);
            if (swap) reparentCombo(targetStep.id, moving.taskId);
            syncOpenSnapshots(moving, targetStep, targetTaskId, swap);
            return null;
        });
    }

    private void syncOpenSnapshots(TaskStepTemplate moving, TaskStepTemplate swapped,
                                   TaskId targetTaskId, boolean swap) {
        if (moving.taskId.equals(targetTaskId)) {
            resequenceOpen(moving.taskId);
            return;
        }
        Map<String, Occurrence> targets = new HashMap<>();
        for (Occurrence occurrence : repository.openOccurrences())
            if (occurrence.taskId.equals(targetTaskId))
                targets.put(occurrence.slot.name(), occurrence);
        for (Occurrence source : repository.openOccurrences()) {
            if (!source.taskId.equals(moving.taskId)) continue;
            Occurrence target = targets.get(source.slot.name());
            if (target == null) continue;
            OccurrenceStep sourceStep = snapshot(source.id, moving.id);
            if (sourceStep == null) continue;
            if (swap) {
                OccurrenceStep targetStep = snapshot(target.id, swapped.id);
                if (targetStep == null) continue;
                repository.updateOccurrenceStep(sourceStep.relocate(target.id,
                        sourceStep.position));
                repository.moveRewardBookings(sourceStep.id, target.id);
                repository.updateOccurrenceStep(targetStep.relocate(source.id,
                        targetStep.position));
                repository.moveRewardBookings(targetStep.id, source.id);
            } else {
                repository.updateOccurrenceStep(sourceStep.relocate(target.id,
                        sourceStep.position));
                repository.moveRewardBookings(sourceStep.id, target.id);
            }
        }
        resequenceOpen(moving.taskId);
        resequenceOpen(targetTaskId);
    }

    private void resequenceOpen(TaskId taskId) {
        Map<String, Integer> templateOrder = new HashMap<>();
        List<TaskStepTemplate> templates = repository.templates(taskId);
        for (int index = 0; index < templates.size(); index++)
            templateOrder.put(templates.get(index).id, index);
        for (Occurrence occurrence : repository.openOccurrences()) {
            if (!occurrence.taskId.equals(taskId)) continue;
            List<OccurrenceStep> steps = repository.occurrenceSteps(occurrence.id);
            steps.sort((left, right) -> {
                int leftOrder = templateOrder.getOrDefault(left.sourceTemplateId,
                        templates.size() + left.position);
                int rightOrder = templateOrder.getOrDefault(right.sourceTemplateId,
                        templates.size() + right.position);
                return Integer.compare(leftOrder, rightOrder);
            });
            for (int index = 0; index < steps.size(); index++)
                if (steps.get(index).position != index)
                    repository.updateOccurrenceStep(steps.get(index).relocate(
                            occurrence.id, index));
        }
    }

    private OccurrenceStep snapshot(String occurrenceId, String templateId) {
        for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
            if (templateId.equals(step.sourceTemplateId)) return step;
        return null;
    }

    private void reparentCombo(String templateId, TaskId taskId) {
        String owner = ComboProgress.stepOwner(templateId);
        ComboProgress combo = repository.combo(owner);
        if (combo != null && !combo.taskId.equals(taskId))
            repository.putCombo(new ComboProgress(combo.ownerId, taskId, combo.kind,
                    combo.points, combo.settledThroughOn));
    }

    private Task active(TaskId id) {
        Task task = repository.findTask(id);
        return task == null || task.archived || task.conditionDone ? null : task;
    }

    private TaskStepTemplate find(String id) {
        if (id == null) return null;
        List<TaskId> ids = new ArrayList<>();
        for (Task task : repository.allTasks()) ids.add(task.id);
        for (TaskStepTemplate step : repository.templatesFor(ids))
            if (step.id.equals(id)) return step;
        return null;
    }

    private static TaskStepTemplate reparent(TaskStepTemplate value, Task task, int position) {
        return new TaskStepTemplate(value.id, task.id, position, value.text,
                task.recurrence == Recurrence.ONCE ? 0 : value.weekdayMask,
                value.amount, value.note);
    }

    private static List<TaskStepTemplate> resequence(List<TaskStepTemplate> values, Task task) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++)
            result.add(reparent(values.get(index), task, index));
        return result;
    }

    private static void remove(List<TaskStepTemplate> values, String id) {
        values.removeIf(value -> value.id.equals(id));
    }

    private static int indexOf(List<TaskStepTemplate> values, String id) {
        if (id == null) return -1;
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }
}

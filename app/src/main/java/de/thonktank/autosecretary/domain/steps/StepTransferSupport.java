package de.thonktank.autosecretary.domain.steps;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StepTransferSupport {
    private StepTransferSupport() { }

    static Task active(StepOrganizationRepository repository, TaskId id) {
        Task task = repository.findTask(id);
        return task == null || task.archived || task.conditionDone ? null : task;
    }

    static boolean isCanonical(List<? extends Positioned> values) {
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            Positioned value = values.get(index);
            if (value.position() != index || !ids.add(value.id())) return false;
        }
        return true;
    }

    static boolean canonicalTemplates(List<TaskStepTemplate> values) {
        List<Positioned> positioned = new ArrayList<>();
        for (TaskStepTemplate value : values) positioned.add(new Positioned() {
            @Override public String id() { return value.id; }
            @Override public int position() { return value.position; }
        });
        return isCanonical(positioned);
    }

    static boolean canonicalSnapshots(StepOrganizationRepository repository, TaskId taskId) {
        for (Occurrence occurrence : repository.openOccurrences(taskId)) {
            List<Positioned> positioned = new ArrayList<>();
            for (OccurrenceStep value : repository.occurrenceSteps(occurrence.id))
                positioned.add(new Positioned() {
                    @Override public String id() { return value.id; }
                    @Override public int position() { return value.position; }
                });
            if (!isCanonical(positioned)) return false;
        }
        return true;
    }

    static List<TaskStepTemplate> resequence(List<TaskStepTemplate> values, Task task) {
        List<TaskStepTemplate> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++)
            result.add(reparent(values.get(index), task, index));
        if (!canonicalTemplates(result))
            throw new IllegalStateException("Step template resequencing failed");
        return result;
    }

    static TaskStepTemplate reparent(TaskStepTemplate value, Task task, int position) {
        return new TaskStepTemplate(value.id, task.id, position, value.text,
                task.recurrence == Recurrence.ONCE ? 0 : value.weekdayMask,
                value.amount, value.note);
    }

    static boolean resequenceOpen(StepOrganizationRepository repository, TaskId taskId) {
        Map<String, Integer> templateOrder = new HashMap<>();
        List<TaskStepTemplate> templates = repository.templates(taskId);
        for (int index = 0; index < templates.size(); index++)
            templateOrder.put(templates.get(index).id, index);
        boolean changed = false;
        for (Occurrence occurrence : repository.openOccurrences(taskId)) {
            List<OccurrenceStep> steps = repository.occurrenceSteps(occurrence.id);
            steps.sort((left, right) -> {
                int leftOrder = templateOrder.getOrDefault(left.sourceTemplateId,
                        templates.size() + left.position);
                int rightOrder = templateOrder.getOrDefault(right.sourceTemplateId,
                        templates.size() + right.position);
                return Integer.compare(leftOrder, rightOrder);
            });
            for (int index = 0; index < steps.size(); index++) {
                OccurrenceStep step = steps.get(index);
                if (step.position == index) continue;
                repository.updateOccurrenceStep(step.relocate(occurrence.id, index));
                changed = true;
            }
        }
        return changed;
    }

    static OccurrenceStep snapshot(StepOrganizationRepository repository, String occurrenceId,
                                   String templateId) {
        for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
            if (templateId.equals(step.sourceTemplateId)) return step;
        return null;
    }

    static void reparentCombo(StepOrganizationRepository repository, String templateId,
                              TaskId taskId) {
        String owner = ComboProgress.stepOwner(templateId);
        ComboProgress combo = repository.combo(owner);
        if (combo != null && !combo.taskId.equals(taskId))
            repository.putCombo(new ComboProgress(combo.ownerId, taskId, combo.kind,
                    combo.points, combo.settledThroughOn));
    }

    static int indexOf(List<TaskStepTemplate> values, String id) {
        if (id == null) return -1;
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(id)) return index;
        return -1;
    }

    static void remove(List<TaskStepTemplate> values, String id) {
        values.removeIf(value -> value.id.equals(id));
    }

    private interface Positioned {
        String id();
        int position();
    }
}

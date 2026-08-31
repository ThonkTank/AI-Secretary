package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowConfigurationDraft;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Atomically creates or updates a task together with its optional flow and capacity rules. */
public final class SaveTaskConfiguration {
    private final TaskDefinitionRepository tasks;
    private final StepFlowDefinitionRepository flows;
    private final TransactionRunner transactions;
    private final CreateTask create;
    private final UpdateTask update;
    private final IdGenerator ids;

    public SaveTaskConfiguration(TaskDefinitionRepository tasks,
                                 StepFlowDefinitionRepository flows,
                                 TransactionRunner transactions, CreateTask create,
                                 UpdateTask update, IdGenerator ids) {
        this.tasks = tasks;
        this.flows = flows;
        this.transactions = transactions;
        this.create = create;
        this.update = update;
        this.ids = ids;
    }

    public TaskId execute(TaskId taskId, TaskDefinition definition,
                          FlowConfigurationDraft draft) {
        if (definition == null || draft == null)
            throw new IllegalArgumentException("Aufgabe und Ablauf sind erforderlich");
        if (definition.steps.size() != draft.stepKeys.size())
            throw new IllegalArgumentException("Ablauf und Schritte passen nicht zusammen");
        return transactions.inTransaction(() -> saveInside(taskId, definition, draft));
    }

    private TaskId saveInside(TaskId requestedId, TaskDefinition definition,
                              FlowConfigurationDraft draft) {
        TaskDefinition resolvedDefinition = withDerivedActivations(definition, draft);
        TaskId taskId;
        if (requestedId == null) taskId = create.executeInsideTransaction(resolvedDefinition);
        else {
            if (tasks.findTask(requestedId) == null)
                throw new IllegalArgumentException("Aufgabe existiert nicht mehr");
            update.executeInsideTransaction(requestedId, resolvedDefinition, false);
            taskId = requestedId;
        }

        List<TaskStepTemplate> templates = tasks.templates(taskId);
        if (templates.size() != draft.stepKeys.size())
            throw new IllegalStateException("Gespeicherte Schritte passen nicht zum Ablauf");
        Map<String, String> stepIds = new HashMap<>();
        for (int index = 0; index < templates.size(); index++)
            stepIds.put(draft.stepKeys.get(index), templates.get(index).id);

        Map<String, String> resourceIds = persistResources(draft.resources);
        List<StepTransition> transitions = new ArrayList<>();
        for (FlowConfigurationDraft.Link link : draft.links)
            transitions.add(new StepTransition(required(stepIds, link.sourceStepKey),
                    required(stepIds, link.targetStepKey), link.delay));
        List<StepResourceLease> leases = new ArrayList<>();
        for (FlowConfigurationDraft.Lease lease : draft.leases) {
            String identity = lease.persistedId == null ? ids.nextId() : lease.persistedId;
            leases.add(new StepResourceLease(identity, taskId,
                    required(stepIds, lease.acquireStepKey),
                    required(stepIds, lease.releaseStepKey),
                    required(resourceIds, lease.resourceKey), lease.units));
        }

        List<CapacityResource> resources = flows.capacityResources();
        new StepFlowDefinition(taskId, templates, transitions, leases, resources);
        validateOtherDefinitions(taskId, resources);
        flows.replaceStepFlow(taskId, transitions, leases);
        return taskId;
    }

    private static TaskDefinition withDerivedActivations(TaskDefinition definition,
                                                         FlowConfigurationDraft draft) {
        Set<String> targets = new HashSet<>();
        for (FlowConfigurationDraft.Link link : draft.links) targets.add(link.targetStepKey);
        List<TaskStepDefinition> steps = new ArrayList<>();
        for (int index = 0; index < definition.steps.size(); index++) {
            TaskStepDefinition step = definition.steps.get(index);
            StepActivationKind activation = targets.contains(draft.stepKeys.get(index))
                    ? StepActivationKind.FOLLOW_UP : StepActivationKind.SCHEDULED;
            boolean followUp = activation == StepActivationKind.FOLLOW_UP;
            steps.add(new TaskStepDefinition(step.id, index, step.text,
                    followUp ? 0 : step.weekdayMask, followUp ? 0 : step.intervalDays,
                    step.prescription, step.assistantPolicy, step.note, activation));
        }
        return new TaskDefinition(definition.title, definition.estimatedMinutes,
                definition.fallbackSlot, definition.recurrence, definition.intervalDays,
                definition.weekdayMask, definition.timeOfDayMask, definition.boundKind,
                definition.boundUntilOn, definition.boundWeeks, definition.remainingCount,
                definition.deadlineOn, definition.note, definition.missedOccurrenceMode, steps);
    }

    private Map<String, String> persistResources(List<FlowConfigurationDraft.Resource> drafts) {
        Map<String, String> result = new HashMap<>();
        for (FlowConfigurationDraft.Resource draft : drafts) {
            String identity = draft.persistedId == null ? ids.nextId() : draft.persistedId;
            if (draft.persistedId != null && flows.findCapacityResource(identity) == null)
                throw new IllegalArgumentException("Begrenztes Ding existiert nicht mehr: "
                        + draft.name);
            if (draft.persistedId == null || draft.changed)
                flows.putCapacityResource(new CapacityResource(identity, draft.name, draft.capacity));
            result.put(draft.key, identity);
        }
        return result;
    }

    private void validateOtherDefinitions(TaskId changedTask, List<CapacityResource> resources) {
        for (Task task : tasks.allTasks()) {
            if (task.id.equals(changedTask)) continue;
            List<StepTransition> transitions = flows.stepTransitions(task.id);
            List<StepResourceLease> leases = flows.stepResourceLeases(task.id);
            if (transitions.isEmpty() && leases.isEmpty()) continue;
            new StepFlowDefinition(task.id, tasks.templates(task.id), transitions, leases, resources);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Ablauf verweist auf einen fehlenden Eintrag");
        return value;
    }
}

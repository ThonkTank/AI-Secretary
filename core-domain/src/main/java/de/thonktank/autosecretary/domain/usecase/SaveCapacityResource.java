package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;
import de.thonktank.autosecretary.domain.repository.TaskDefinitionRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.List;

/** Creates or edits a named capacity pool while preserving its stable identity. */
public final class SaveCapacityResource {
    private final StepFlowDefinitionRepository repository;
    private final TaskDefinitionRepository tasks;
    private final TransactionRunner transactions;
    private final IdGenerator ids;

    public SaveCapacityResource(StepFlowDefinitionRepository repository,
                                TransactionRunner transactions, IdGenerator ids) {
        this(repository, null, transactions, ids);
    }

    public SaveCapacityResource(StepFlowDefinitionRepository repository,
                                TaskDefinitionRepository tasks, TransactionRunner transactions,
                                IdGenerator ids) {
        this.repository = repository;
        this.tasks = tasks;
        this.transactions = transactions;
        this.ids = ids;
    }

    public CapacityResource execute(String id, String name, int capacity) {
        String identity = id == null || id.trim().isEmpty() ? ids.nextId() : id;
        CapacityResource resource = new CapacityResource(identity, name, capacity);
        transactions.inTransaction(() -> {
            validateDefinitions(resource);
            repository.putCapacityResource(resource);
            return null;
        });
        return resource;
    }

    private void validateDefinitions(CapacityResource changed) {
        if (tasks == null) return;
        List<CapacityResource> resources = new ArrayList<>();
        boolean replaced = false;
        for (CapacityResource current : repository.capacityResources()) {
            if (current.id.equals(changed.id)) {
                resources.add(changed);
                replaced = true;
            } else resources.add(current);
        }
        if (!replaced) resources.add(changed);
        for (Task task : tasks.allTasks()) {
            List<TaskStepTemplate> templates = tasks.templates(task.id);
            List<de.thonktank.autosecretary.domain.model.StepTransition> transitions =
                    repository.stepTransitions(task.id);
            List<de.thonktank.autosecretary.domain.model.StepResourceLease> leases =
                    repository.stepResourceLeases(task.id);
            if (transitions.isEmpty() && leases.isEmpty()) continue;
            new StepFlowDefinition(task.id, templates, transitions, leases, resources);
        }
    }
}

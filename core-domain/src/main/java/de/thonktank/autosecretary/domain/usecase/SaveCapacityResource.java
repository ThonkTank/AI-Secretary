package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.StepFlowDefinition;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.util.ArrayList;
import java.util.List;

/** Creates or edits a named capacity pool while preserving its stable identity. */
public final class SaveCapacityResource {
    private final FlowRepository repository;
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final TransactionRunner transactions;
    private final IdGenerator ids;

    public SaveCapacityResource(FlowRepository repository,
                                TransactionRunner transactions, IdGenerator ids) {
        this(repository, null, null, transactions, ids);
    }

    public SaveCapacityResource(FlowRepository repository,
                                CatalogRepository tasks, StepRepository steps,
                                TransactionRunner transactions,
                                IdGenerator ids) {
        this.repository = repository;
        this.tasks = tasks;
        this.steps = steps;
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
            List<TaskStepTemplate> templates = steps.templates(task.id);
            List<de.thonktank.autosecretary.domain.model.StepTransition> transitions =
                    repository.stepTransitions(task.id);
            List<de.thonktank.autosecretary.domain.model.StepResourceLease> leases =
                    repository.stepResourceLeases(task.id);
            if (transitions.isEmpty() && leases.isEmpty()) continue;
            new StepFlowDefinition(task.id, templates, transitions, leases, resources);
        }
    }
}

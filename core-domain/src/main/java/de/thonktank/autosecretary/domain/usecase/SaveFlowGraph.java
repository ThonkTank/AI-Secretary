package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.*;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import java.util.*;

/** One save boundary for the dedicated editor. Never edits or cancels an existing execution. */
public final class SaveFlowGraph {
    private final CatalogRepository tasks;
    private final StepRepository steps;
    private final FlowRepository resources;
    private final FlowGraphDefinitionRepository graphs;
    private final TransactionRunner transactions;
    private final CreateTask create;
    private final IdGenerator ids;

    public SaveFlowGraph(CatalogRepository tasks, StepRepository steps, FlowRepository resources,
                        FlowGraphDefinitionRepository graphs, TransactionRunner transactions,
                        CreateTask create, IdGenerator ids) {
        this.tasks = tasks; this.steps = steps; this.resources = resources; this.graphs = graphs;
        this.transactions = transactions; this.create = create; this.ids = ids;
    }

    public TaskId execute(FlowGraphEdit edit) {
        return execute(edit, UUID.randomUUID().toString());
    }

    /** Reuse the same key and immutable input when recovering an interrupted save attempt. */
    public TaskId execute(FlowGraphEdit edit, String requestKey) {
        if (requestKey == null || requestKey.trim().isEmpty()) throw new IllegalArgumentException("Save request identity is missing");
        return transactions.inTransaction(() -> {
            TaskId existing = graphs.findSaveResult(requestKey);
            if (existing != null) return existing;
            TaskId saved = save(edit);
            graphs.recordSaveResult(requestKey, saved);
            return saved;
        });
    }

    private TaskId save(FlowGraphEdit edit) {
        Task oldTask = edit.taskId == null ? null : tasks.findTask(edit.taskId);
        if (edit.taskId != null && oldTask == null)
            throw new IllegalArgumentException("Ablauf existiert nicht mehr");
        FlowGraphDefinition previous = oldTask == null ? null : graphs.find(oldTask.id);
        Set<String> retainedLeaseIds = new HashSet<>();
        if (previous != null) for (FlowGraphDefinition.Lease lease : previous.leases) retainedLeaseIds.add(lease.id);
        Map<String, TaskStepTemplate> oldSteps = new HashMap<>();
        if (oldTask != null) for (TaskStepTemplate step : steps.templates(oldTask.id)) oldSteps.put(step.id, step);
        Map<String, String> stepIds = new LinkedHashMap<>();
        List<TaskStepDefinition> definitions = new ArrayList<>();
        Set<String> roots = new HashSet<>(edit.graph.roots());
        for (int i = 0; i < edit.steps.size(); i++) {
            TaskStepDefinition request = edit.steps.get(i);
            String key = edit.graph.stepIds.get(i);
            if (request.id != null && !oldSteps.containsKey(request.id))
                throw new FlowGraphEditProblem("step", key, "Schritt existiert nicht mehr: " + request.text);
            String id = request.id == null ? ids.nextId() : request.id;
            stepIds.put(key, id);
            TaskStepTemplate old = oldSteps.get(id);
            boolean start = roots.contains(key);
            // The small step dialog does not edit prescriptions, notes or assistant state.
            // Retain live values, including assistant adjustments made while this editor was open.
            definitions.add(new TaskStepDefinition(id, i, request.text,
                    start ? request.weekdayMask : 0, start ? request.intervalDays : 0,
                    old == null ? request.prescription : old.prescription,
                    old == null ? request.assistantPolicy : old.assistantProfile == null ? null : old.assistantProfile.policy,
                    old == null ? request.note : old.note,
                    start ? StepActivationKind.SCHEDULED : StepActivationKind.FOLLOW_UP));
        }
        TaskId taskId;
        if (oldTask == null) {
            taskId = create.executeInsideTransaction(new TaskDefinition(edit.name, null, TaskSlot.MORNING,
                    Recurrence.DAILY, 1, 0, TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER,
                    null, null, null, null, "", definitions));
        } else {
            taskId = oldTask.id;
            tasks.updateTask(oldTask.edit(edit.name, oldTask.catalogOrder));
            Set<String> retained = new HashSet<>(stepIds.values());
            for (TaskStepTemplate old : oldSteps.values()) if (!retained.contains(old.id)) steps.deleteTemplate(old.id);
            List<TaskStepTemplate> templates = new ArrayList<>();
            for (TaskStepDefinition definition : definitions) {
                TaskStepTemplate old = oldSteps.get(definition.id);
                TrainingAssistantProfile profile = old == null ? definition.assistantPolicy == null ? null
                        : new TrainingAssistantProfile(definition.assistantPolicy, TrainingAssistantState.calibrating())
                        : old.assistantProfile;
                templates.add(new TaskStepTemplate(definition.id, taskId, definition.position, definition.text,
                        definition.weekdayMask, definition.intervalDays, definition.prescription, profile,
                        definition.note, definition.activationKind));
            }
            steps.insertTemplates(templates);
        }
        Set<String> usedResources = new HashSet<>();
        for (FlowGraphEdit.Binding binding : edit.bindings) usedResources.add(binding.lease.resourceKey);
        Map<String, String> resourceIds = persistResources(edit.resources, usedResources);
        List<FlowGraphDefinition.Node> nodes = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            TaskStepDefinition definition = definitions.get(i);
            nodes.add(new FlowGraphDefinition.Node(definition.id, definition.text, definition.prescription,
                    definition.note, edit.waits.get(edit.graph.stepIds.get(i))));
        }
        List<FlowTileGraph.Link> links = new ArrayList<>();
        for (FlowTileGraph.Link link : edit.graph.links)
            links.add(new FlowTileGraph.Link(stepIds.get(link.source), stepIds.get(link.target)));
        List<FlowGraphDefinition.Lease> leases = new ArrayList<>();
        Map<String, Map<String, Long>> acquired = new HashMap<>();
        for (FlowGraphEdit.Binding binding : edit.bindings) {
            FlowConfigurationDraft.Lease lease = binding.lease;
            if (lease.persistedId != null && !retainedLeaseIds.contains(lease.persistedId))
                throw new FlowGraphEditProblem("lease", lease.key, "Kapazitätszuordnung existiert nicht mehr");
            String resourceId = resourceIds.get(lease.resourceKey);
            String acquireId = stepIds.get(lease.acquireStepKey);
            long total = acquired.computeIfAbsent(acquireId, ignored -> new HashMap<>())
                    .merge(resourceId, (long) lease.units, Math::addExact);
            if (total > resources.findCapacityResource(resourceId).capacity)
                throw new FlowGraphEditProblem("lease", lease.key, "Die Zuordnungen dieses Schritts überschreiten die Kapazität");
            leases.add(new FlowGraphDefinition.Lease(lease.persistedId == null ? ids.nextId() : lease.persistedId,
                    resourceId, acquireId, stepIds.get(lease.releaseStepKey), lease.units, binding.releaseAfterWait));
        }
        graphs.replace(new FlowGraphDefinition(taskId, new FlowTileGraph(new ArrayList<>(stepIds.values()), links), nodes, leases));
        return taskId;
    }

    private Map<String, String> persistResources(List<FlowConfigurationDraft.Resource> drafts, Set<String> used) {
        Map<String, CapacityResource> existing = new LinkedHashMap<>();
        for (CapacityResource resource : resources.capacityResources()) existing.put(resource.id, resource);
        Map<String, CapacityResource> before = new LinkedHashMap<>(existing);
        Map<String, String> resolved = new HashMap<>();
        List<CapacityResource> writes = new ArrayList<>();
        for (FlowConfigurationDraft.Resource draft : drafts) {
            String id = draft.persistedId == null ? ids.nextId() : draft.persistedId;
            if (draft.persistedId != null && !existing.containsKey(id)) {
                if (!draft.changed && !used.contains(draft.key)) continue;
                throw new FlowGraphEditProblem("resource", draft.key, "Kapazität existiert nicht mehr: " + draft.name);
            }
            if (draft.persistedId == null || draft.changed) {
                CapacityResource changed = new CapacityResource(id, draft.name, draft.capacity);
                existing.put(id, changed); writes.add(changed);
            }
            resolved.put(draft.key, id);
        }
        Set<String> names = new HashSet<>();
        for (CapacityResource resource : existing.values()) if (!names.add(resource.normalizedName)) {
            String key = null;
            for (FlowConfigurationDraft.Resource draft : drafts)
                if ((draft.changed || draft.persistedId == null)
                        && CapacityResource.normalizeName(draft.name).equals(resource.normalizedName)) key = draft.key;
            throw new FlowGraphEditProblem("resource", key, "Kapazitätsname existiert bereits: " + resource.name);
        }
        // SQLite checks the shared unique-name index per row. Release changed names inside
        // this same transaction before writing final names, so exchanging two names works too.
        Set<String> occupied = new HashSet<>(names);
        for (CapacityResource resource : before.values()) occupied.add(resource.normalizedName);
        for (CapacityResource changed : writes) {
            CapacityResource old = before.get(changed.id);
            if (old != null && !old.normalizedName.equals(changed.normalizedName)) {
                String temporary;
                do { temporary = "flow-edit-" + UUID.randomUUID(); }
                while (!occupied.add(CapacityResource.normalizeName(temporary)));
                resources.putCapacityResource(new CapacityResource(old.id, temporary, old.capacity));
            }
        }
        for (CapacityResource changed : writes) resources.putCapacityResource(changed);
        return resolved;
    }
}

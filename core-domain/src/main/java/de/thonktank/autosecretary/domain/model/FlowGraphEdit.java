package de.thonktank.autosecretary.domain.model;

import java.util.*;

/** Uncommitted two-sheet editor input; graph keys may be temporary, persisted IDs never are. */
public final class FlowGraphEdit {
    public final TaskId taskId;
    public final String name;
    public final FlowTileGraph graph;
    public final List<TaskStepDefinition> steps;
    public final Map<String, FlowDelayPolicy> waits;
    public final List<FlowConfigurationDraft.Resource> resources;
    public final List<Binding> bindings;

    public FlowGraphEdit(TaskId taskId, String name, FlowTileGraph graph, List<TaskStepDefinition> steps,
                         Map<String, FlowDelayPolicy> waits, List<FlowConfigurationDraft.Resource> resources,
                         List<Binding> bindings) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 120)
            throw new IllegalArgumentException("Ablaufname muss 1 bis 120 Zeichen enthalten");
        this.taskId = taskId;
        this.name = name.trim();
        this.graph = Objects.requireNonNull(graph);
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.waits = Collections.unmodifiableMap(new LinkedHashMap<>(waits));
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
        if (steps.isEmpty() || steps.size() != graph.stepIds.size()
                || !waits.keySet().equals(new HashSet<>(graph.stepIds)) || waits.containsValue(null))
            throw new IllegalArgumentException("Schritte, Kacheln und Wartezeiten passen nicht zusammen");
        Set<String> identities = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            TaskStepDefinition step = steps.get(i);
            if (step.position != i || (step.id != null && !identities.add(step.id)))
                throw new IllegalArgumentException("Schrittidentitäten oder Reihenfolge sind ungültig");
        }
        Set<String> resourceKeys = new HashSet<>();
        Set<String> persistedResources = new HashSet<>();
        for (FlowConfigurationDraft.Resource resource : resources)
            if (!resourceKeys.add(resource.key)
                    || (resource.persistedId != null && !persistedResources.add(resource.persistedId)))
                throw new IllegalArgumentException("Kapazität ist doppelt");
        Set<String> bindingsKeys = new HashSet<>();
        Set<String> persistedBindings = new HashSet<>();
        for (Binding binding : bindings) {
            FlowConfigurationDraft.Lease lease = binding.lease;
            if (!bindingsKeys.add(lease.key) || !resourceKeys.contains(lease.resourceKey)
                    || (lease.persistedId != null && !persistedBindings.add(lease.persistedId))
                    || !graph.reaches(lease.acquireStepKey, lease.releaseStepKey))
                throw new IllegalArgumentException("Kapazitätszuordnung ist ungültig");
        }
    }

    public static final class Binding {
        public final FlowConfigurationDraft.Lease lease;
        public final boolean releaseAfterWait;

        public Binding(FlowConfigurationDraft.Lease lease, boolean releaseAfterWait) {
            this.lease = Objects.requireNonNull(lease);
            this.releaseAfterWait = releaseAfterWait;
        }
    }
}

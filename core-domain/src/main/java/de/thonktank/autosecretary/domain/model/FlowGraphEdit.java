package de.thonktank.autosecretary.domain.model;

import java.util.*;

/** Uncommitted two-sheet editor input; graph keys may be temporary, persisted IDs never are. */
public final class FlowGraphEdit {
    public final TaskId taskId;
    public final String name;
    public final FlowTileGraph graph;
    public final List<TaskStepDefinition> steps;
    public final Map<String, FlowDelayPolicy> waits;
    public final List<Resource> resources;
    public final List<Binding> bindings;

    public FlowGraphEdit(TaskId taskId, String name, FlowTileGraph graph, List<TaskStepDefinition> steps,
                         Map<String, FlowDelayPolicy> waits, List<Resource> resources,
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
        for (Resource resource : resources)
            if (!resourceKeys.add(resource.key)
                    || (resource.persistedId != null && !persistedResources.add(resource.persistedId)))
                throw new IllegalArgumentException("Kapazität ist doppelt");
        Set<String> bindingsKeys = new HashSet<>();
        Set<String> persistedBindings = new HashSet<>();
        for (Binding binding : bindings) {
            Lease lease = binding.lease;
            if (!bindingsKeys.add(lease.key) || !resourceKeys.contains(lease.resourceKey)
                    || (lease.persistedId != null && !persistedBindings.add(lease.persistedId))
                    || !graph.reaches(lease.acquireStepId, lease.releaseStepId))
                throw new IllegalArgumentException("Kapazitätszuordnung ist ungültig");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Resource {
        public final String key;
        public final String persistedId;
        public final String name;
        public final int capacity;
        public final boolean changed;

        public Resource(String key, String persistedId, String name, int capacity,
                        boolean changed) {
            if (blank(key)) throw new IllegalArgumentException("Ressourcenschlüssel fehlt");
            // CapacityResource owns the user-facing name and range validation.
            CapacityResource validated = new CapacityResource(persistedId == null ? key : persistedId, name, capacity);
            this.key = key;
            this.persistedId = blank(persistedId) ? null : persistedId;
            this.name = validated.name;
            this.capacity = validated.capacity;
            this.changed = changed;
        }
    }

    public static final class Lease {
        public final String key;
        public final String persistedId;
        public final String resourceKey;
        public final String acquireStepId;
        public final String releaseStepId;
        public final int units;

        public Lease(String key, String persistedId, String resourceKey,
                     String acquireStepId, String releaseStepId, int units) {
            if (blank(key) || blank(resourceKey) || blank(acquireStepId)
                    || blank(releaseStepId) || units < 1)
                throw new IllegalArgumentException("Platzregel ist unvollständig");
            this.key = key;
            this.persistedId = blank(persistedId) ? null : persistedId;
            this.resourceKey = resourceKey;
            this.acquireStepId = acquireStepId;
            this.releaseStepId = releaseStepId;
            this.units = units;
        }
    }

    public static final class Binding {
        public final Lease lease;
        public final boolean releaseAfterWait;

        public Binding(Lease lease, boolean releaseAfterWait) {
            this.lease = Objects.requireNonNull(lease);
            this.releaseAfterWait = releaseAfterWait;
        }
    }
}

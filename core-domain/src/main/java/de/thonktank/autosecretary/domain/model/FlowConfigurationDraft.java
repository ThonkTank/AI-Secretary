package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistency-independent flow input. Step and resource keys may be temporary editor identities;
 * {@code SaveTaskConfiguration} resolves them atomically to stored identities.
 */
public final class FlowConfigurationDraft {
    public final List<String> stepKeys;
    public final List<Link> links;
    public final List<Resource> resources;
    public final List<Lease> leases;

    public FlowConfigurationDraft(List<String> stepKeys, List<Link> links,
                                  List<Resource> resources, List<Lease> leases) {
        if (stepKeys == null || links == null || resources == null || leases == null)
            throw new IllegalArgumentException("Ablaufentwurf ist unvollständig");
        this.stepKeys = immutable(stepKeys);
        this.links = immutable(links);
        this.resources = immutable(resources);
        this.leases = immutable(leases);
        validateKeys();
    }

    public static FlowConfigurationDraft empty(List<String> stepKeys) {
        return new FlowConfigurationDraft(stepKeys, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    private void validateKeys() {
        Set<String> steps = unique(stepKeys, "Schritt-ID");
        Set<String> resourceKeys = new HashSet<>();
        for (Resource resource : resources) {
            if (!resourceKeys.add(resource.key))
                throw new IllegalArgumentException("Begrenztes Ding ist doppelt: " + resource.key);
        }
        for (Link link : links)
            if (!steps.contains(link.sourceStepKey) || !steps.contains(link.targetStepKey))
                throw new IllegalArgumentException("Verbindung verweist auf einen fehlenden Schritt");
        Set<String> leaseKeys = new HashSet<>();
        for (Lease lease : leases) {
            if (!leaseKeys.add(lease.key))
                throw new IllegalArgumentException("Platzregel ist doppelt: " + lease.key);
            if (!resourceKeys.contains(lease.resourceKey)
                    || !steps.contains(lease.acquireStepKey)
                    || !steps.contains(lease.releaseStepKey))
                throw new IllegalArgumentException("Platzregel verweist auf einen fehlenden Eintrag");
        }
    }

    private static Set<String> unique(List<String> values, String label) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (blank(value) || !result.add(value))
                throw new IllegalArgumentException(label + " ist leer oder doppelt");
        }
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class Link {
        public final String sourceStepKey;
        public final String targetStepKey;
        public final FlowDelayPolicy delay;

        public Link(String sourceStepKey, String targetStepKey, FlowDelayPolicy delay) {
            if (blank(sourceStepKey) || blank(targetStepKey) || delay == null)
                throw new IllegalArgumentException("Verbindung ist unvollständig");
            this.sourceStepKey = sourceStepKey;
            this.targetStepKey = targetStepKey;
            this.delay = delay;
        }
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
            new CapacityResource(persistedId == null ? key : persistedId, name, capacity);
            this.key = key;
            this.persistedId = blank(persistedId) ? null : persistedId;
            this.name = name.trim();
            this.capacity = capacity;
            this.changed = changed;
        }
    }

    public static final class Lease {
        public final String key;
        public final String persistedId;
        public final String resourceKey;
        public final String acquireStepKey;
        public final String releaseStepKey;
        public final int units;

        public Lease(String key, String persistedId, String resourceKey,
                     String acquireStepKey, String releaseStepKey, int units) {
            if (blank(key) || blank(resourceKey) || blank(acquireStepKey)
                    || blank(releaseStepKey) || units < 1)
                throw new IllegalArgumentException("Platzregel ist unvollständig");
            this.key = key;
            this.persistedId = blank(persistedId) ? null : persistedId;
            this.resourceKey = resourceKey;
            this.acquireStepKey = acquireStepKey;
            this.releaseStepKey = releaseStepKey;
            this.units = units;
        }
    }
}

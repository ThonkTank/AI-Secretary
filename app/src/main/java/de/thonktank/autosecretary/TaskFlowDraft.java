package de.thonktank.autosecretary;

import android.os.Bundle;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowConfigurationDraft;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.StepResourceLease;
import de.thonktank.autosecretary.domain.model.StepTransition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Rotation-safe optional flow draft embedded in the normal task editor. */
public final class TaskFlowDraft {
    private static final String RESOURCE_PREFIX = "draft-resource:";
    private static final String LEASE_PREFIX = "draft-lease:";

    public final List<StepTransition> transitions;
    public final List<Resource> resources;
    public final List<Lease> leases;
    public final int nextResourceIdentity;
    public final int nextLeaseIdentity;

    public TaskFlowDraft(List<StepTransition> transitions, List<Resource> resources,
                         List<Lease> leases, int nextResourceIdentity,
                         int nextLeaseIdentity) {
        if (transitions == null || resources == null || leases == null)
            throw new IllegalArgumentException("Flow editor draft is incomplete");
        this.transitions = immutable(transitions);
        this.resources = immutable(resources);
        this.leases = immutable(leases);
        this.nextResourceIdentity = Math.max(1, nextResourceIdentity);
        this.nextLeaseIdentity = Math.max(1, nextLeaseIdentity);
    }

    public static TaskFlowDraft empty() {
        return new TaskFlowDraft(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), 1, 1);
    }

    public static TaskFlowDraft withCatalog(List<CapacityResource> catalog) {
        List<Resource> resources = new ArrayList<>();
        for (CapacityResource value : catalog) resources.add(Resource.persisted(value));
        return new TaskFlowDraft(Collections.emptyList(), resources,
                Collections.emptyList(), 1, 1);
    }

    public TaskFlowDraft mergeCatalog(List<CapacityResource> catalog) {
        if (catalog == null || catalog.isEmpty()) return this;
        Map<String, Resource> currentByPersistedId = new HashMap<>();
        for (Resource value : resources)
            if (value.persistedId != null) currentByPersistedId.put(value.persistedId, value);
        List<Resource> merged = new ArrayList<>();
        Set<String> catalogIds = new HashSet<>();
        for (CapacityResource value : catalog) {
            catalogIds.add(value.id);
            Resource current = currentByPersistedId.get(value.id);
            merged.add(current == null ? Resource.persisted(value) : current);
        }
        for (Resource value : resources)
            if (value.persistedId == null || !catalogIds.contains(value.persistedId))
                merged.add(value);
        if (merged.equals(resources)) return this;
        return new TaskFlowDraft(transitions, merged, leases,
                nextResourceIdentity, nextLeaseIdentity);
    }

    public static TaskFlowDraft from(StepFlowSetup setup) {
        if (setup == null) return empty();
        List<Resource> resources = new ArrayList<>();
        for (CapacityResource value : setup.resources) resources.add(Resource.persisted(value));
        List<Lease> leases = new ArrayList<>();
        for (StepResourceLease value : setup.resourceLeases) leases.add(Lease.persisted(value));
        return new TaskFlowDraft(setup.transitions, resources, leases, 1, 1);
    }

    public boolean configured() { return !transitions.isEmpty() || !leases.isEmpty(); }

    public boolean isFollowUp(String stepId) {
        for (StepTransition transition : transitions)
            if (transition.targetStepId.equals(stepId)) return true;
        return false;
    }

    public StepTransition transitionAfter(String stepId) {
        for (StepTransition transition : transitions)
            if (transition.sourceStepId.equals(stepId)) return transition;
        return null;
    }

    public Resource resource(String key) {
        for (Resource value : resources) if (value.key.equals(key)) return value;
        return null;
    }

    public TaskFlowDraft withTransition(String source, String target, FlowDelayPolicy delay) {
        List<StepTransition> next = new ArrayList<>();
        for (StepTransition value : transitions)
            if (!value.sourceStepId.equals(source)) next.add(value);
        if (target != null) {
            if (wouldCreateCycle(source, target, next))
                throw new IllegalArgumentException("Ein Ablauf darf keinen Kreis bilden");
            next.add(new StepTransition(source, target,
                    delay == null ? FlowDelayPolicy.fixed(0L) : delay));
        }
        return rebuilt(next, resources, leases, nextResourceIdentity, nextLeaseIdentity);
    }

    public boolean canTarget(String source, String target) {
        if (source == null || target == null || source.equals(target)) return false;
        List<StepTransition> withoutSource = new ArrayList<>();
        for (StepTransition value : transitions)
            if (!value.sourceStepId.equals(source)) withoutSource.add(value);
        return !wouldCreateCycle(source, target, withoutSource);
    }

    public TaskFlowDraft addResource(String name, int capacity) {
        List<Resource> next = new ArrayList<>(resources);
        next.add(new Resource(RESOURCE_PREFIX + nextResourceIdentity, null,
                name, capacity, true));
        return new TaskFlowDraft(transitions, next, leases,
                nextResourceIdentity + 1, nextLeaseIdentity);
    }

    public TaskFlowDraft updateResource(String key, String name, int capacity) {
        List<Resource> next = new ArrayList<>();
        boolean found = false;
        for (Resource value : resources) {
            if (value.key.equals(key)) {
                next.add(new Resource(value.key, value.persistedId, name, capacity, true));
                found = true;
            } else next.add(value);
        }
        return found ? new TaskFlowDraft(transitions, next, leases,
                nextResourceIdentity, nextLeaseIdentity) : this;
    }

    public TaskFlowDraft removeResource(String key) {
        List<Resource> nextResources = new ArrayList<>();
        for (Resource value : resources) if (!value.key.equals(key)) nextResources.add(value);
        List<Lease> nextLeases = new ArrayList<>();
        for (Lease value : leases) if (!value.resourceKey.equals(key)) nextLeases.add(value);
        return new TaskFlowDraft(transitions, nextResources, nextLeases,
                nextResourceIdentity, nextLeaseIdentity);
    }

    public TaskFlowDraft addLease(String resourceKey, String acquireStepId,
                                  String releaseStepId, int units) {
        List<Lease> next = new ArrayList<>(leases);
        next.add(new Lease(LEASE_PREFIX + nextLeaseIdentity, null, resourceKey,
                acquireStepId, releaseStepId, units));
        return rebuilt(transitions, resources, next,
                nextResourceIdentity, nextLeaseIdentity + 1);
    }

    public TaskFlowDraft updateLease(String key, String resourceKey, String acquireStepId,
                                     String releaseStepId, int units) {
        List<Lease> next = new ArrayList<>();
        boolean found = false;
        for (Lease value : leases) {
            if (value.key.equals(key)) {
                next.add(new Lease(value.key, value.persistedId, resourceKey,
                        acquireStepId, releaseStepId, units));
                found = true;
            } else next.add(value);
        }
        return found ? rebuilt(transitions, resources, next,
                nextResourceIdentity, nextLeaseIdentity) : this;
    }

    public TaskFlowDraft removeLease(String key) {
        List<Lease> next = new ArrayList<>();
        for (Lease value : leases) if (!value.key.equals(key)) next.add(value);
        return new TaskFlowDraft(transitions, resources, next,
                nextResourceIdentity, nextLeaseIdentity);
    }

    public TaskFlowDraft clearRules() {
        return new TaskFlowDraft(Collections.emptyList(), resources,
                Collections.emptyList(), nextResourceIdentity, nextLeaseIdentity);
    }

    public TaskFlowDraft reconcileSteps(List<EditorStepState> steps) {
        Set<String> keys = new HashSet<>();
        for (EditorStepState step : steps) keys.add(step.id);
        List<StepTransition> next = new ArrayList<>();
        for (StepTransition value : transitions)
            if (keys.contains(value.sourceStepId) && keys.contains(value.targetStepId))
                next.add(value);
        List<Lease> nextLeases = new ArrayList<>();
        for (Lease value : leases)
            if (keys.contains(value.acquireStepId) && keys.contains(value.releaseStepId))
                nextLeases.add(value);
        return rebuilt(next, resources, nextLeases,
                nextResourceIdentity, nextLeaseIdentity);
    }

    public boolean reachableAfter(String acquireStepId, String releaseStepId) {
        return reachableAfter(acquireStepId, releaseStepId, transitions);
    }

    public List<String> startStepIds(List<EditorStepState> steps) {
        Set<String> targets = new HashSet<>();
        for (StepTransition value : transitions) targets.add(value.targetStepId);
        List<String> result = new ArrayList<>();
        for (EditorStepState step : steps) if (!targets.contains(step.id)) result.add(step.id);
        return Collections.unmodifiableList(result);
    }

    public FlowConfigurationDraft configuration(List<EditorStepState> steps) {
        List<String> stepKeys = new ArrayList<>();
        for (EditorStepState step : steps) stepKeys.add(step.id);
        List<FlowConfigurationDraft.Link> links = new ArrayList<>();
        for (StepTransition transition : transitions)
            links.add(new FlowConfigurationDraft.Link(transition.sourceStepId,
                    transition.targetStepId, transition.delay));
        List<FlowConfigurationDraft.Resource> resourceDrafts = new ArrayList<>();
        for (Resource resource : resources)
            resourceDrafts.add(new FlowConfigurationDraft.Resource(resource.key,
                    resource.persistedId, resource.name, resource.capacity,
                    resource.changed));
        List<FlowConfigurationDraft.Lease> leaseDrafts = new ArrayList<>();
        for (Lease lease : leases)
            leaseDrafts.add(new FlowConfigurationDraft.Lease(lease.key, lease.persistedId,
                    lease.resourceKey, lease.acquireStepId, lease.releaseStepId, lease.units));
        return new FlowConfigurationDraft(stepKeys, links, resourceDrafts, leaseDrafts);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        ArrayList<Bundle> transitionValues = new ArrayList<>();
        for (StepTransition value : transitions) {
            Bundle item = new Bundle();
            item.putString("source", value.sourceStepId);
            item.putString("target", value.targetStepId);
            item.putString("mode", value.delay.mode.name());
            item.putLong("default", value.delay.defaultDelayMillis);
            if (value.delay.lastUsedDelayMillis != null) {
                item.putBoolean("last_set", true);
                item.putLong("last", value.delay.lastUsedDelayMillis);
            }
            transitionValues.add(item);
        }
        bundle.putParcelableArrayList("transitions", transitionValues);
        ArrayList<Bundle> resourceValues = new ArrayList<>();
        for (Resource value : resources) resourceValues.add(value.toBundle());
        bundle.putParcelableArrayList("resources", resourceValues);
        ArrayList<Bundle> leaseValues = new ArrayList<>();
        for (Lease value : leases) leaseValues.add(value.toBundle());
        bundle.putParcelableArrayList("leases", leaseValues);
        bundle.putInt("next_resource", nextResourceIdentity);
        bundle.putInt("next_lease", nextLeaseIdentity);
        return bundle;
    }

    static TaskFlowDraft fromBundle(Bundle bundle) {
        if (bundle == null) return empty();
        try {
            List<StepTransition> transitions = new ArrayList<>();
            ArrayList<Bundle> transitionValues = bundle.getParcelableArrayList("transitions");
            if (transitionValues != null) for (Bundle value : transitionValues) {
                FlowDelayPolicy.Mode mode = BundleValues.enumValue(FlowDelayPolicy.Mode.class,
                        value.getString("mode"), FlowDelayPolicy.Mode.FIXED);
                Long last = value.getBoolean("last_set") ? value.getLong("last") : null;
                transitions.add(new StepTransition(value.getString("source"),
                        value.getString("target"), new FlowDelayPolicy(mode,
                        value.getLong("default"), last)));
            }
            List<Resource> resources = new ArrayList<>();
            ArrayList<Bundle> resourceValues = bundle.getParcelableArrayList("resources");
            if (resourceValues != null) for (Bundle value : resourceValues)
                resources.add(Resource.fromBundle(value));
            List<Lease> leases = new ArrayList<>();
            ArrayList<Bundle> leaseValues = bundle.getParcelableArrayList("leases");
            if (leaseValues != null) for (Bundle value : leaseValues)
                leases.add(Lease.fromBundle(value));
            return new TaskFlowDraft(transitions, resources, leases,
                    bundle.getInt("next_resource", 1), bundle.getInt("next_lease", 1));
        } catch (RuntimeException invalid) {
            return empty();
        }
    }

    private static TaskFlowDraft rebuilt(List<StepTransition> transitions,
                                         List<Resource> resources, List<Lease> leases,
                                         int nextResourceIdentity, int nextLeaseIdentity) {
        Set<String> resourceKeys = new HashSet<>();
        for (Resource resource : resources) resourceKeys.add(resource.key);
        List<Lease> retained = new ArrayList<>();
        for (Lease lease : leases)
            if (resourceKeys.contains(lease.resourceKey)
                    && reachableAfter(lease.acquireStepId, lease.releaseStepId, transitions))
                retained.add(lease);
        return new TaskFlowDraft(transitions, resources, retained,
                nextResourceIdentity, nextLeaseIdentity);
    }

    private static boolean reachableAfter(String acquire, String release,
                                          List<StepTransition> transitions) {
        Map<String, String> next = new HashMap<>();
        for (StepTransition value : transitions)
            next.put(value.sourceStepId, value.targetStepId);
        Set<String> visited = new LinkedHashSet<>();
        String cursor = acquire;
        while (next.containsKey(cursor) && visited.add(cursor)) {
            cursor = next.get(cursor);
            if (release.equals(cursor)) return true;
        }
        return false;
    }

    private static boolean wouldCreateCycle(String source, String target,
                                            List<StepTransition> transitions) {
        Map<String, String> next = new HashMap<>();
        for (StepTransition value : transitions)
            next.put(value.sourceStepId, value.targetStepId);
        next.put(source, target);
        Set<String> visited = new HashSet<>();
        String cursor = source;
        while (cursor != null) {
            if (!visited.add(cursor)) return true;
            cursor = next.get(cursor);
        }
        return false;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static final class Resource {
        public final String key;
        public final String persistedId;
        public final String name;
        public final int capacity;
        public final boolean changed;

        public Resource(String key, String persistedId, String name, int capacity,
                        boolean changed) {
            CapacityResource validated = new CapacityResource(
                    persistedId == null ? key : persistedId, name, capacity);
            this.key = key;
            this.persistedId = persistedId;
            this.name = validated.name;
            this.capacity = validated.capacity;
            this.changed = changed;
        }

        static Resource persisted(CapacityResource value) {
            return new Resource(value.id, value.id, value.name, value.capacity, false);
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putString("key", key); bundle.putString("persisted", persistedId);
            bundle.putString("name", name); bundle.putInt("capacity", capacity);
            bundle.putBoolean("changed", changed);
            return bundle;
        }

        static Resource fromBundle(Bundle bundle) {
            return new Resource(bundle.getString("key"), bundle.getString("persisted"),
                    bundle.getString("name"), bundle.getInt("capacity"),
                    bundle.getBoolean("changed"));
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Resource)) return false;
            Resource value = (Resource) other;
            return key.equals(value.key) && Objects.equals(persistedId, value.persistedId)
                    && name.equals(value.name) && capacity == value.capacity
                    && changed == value.changed;
        }

        @Override public int hashCode() {
            return Objects.hash(key, persistedId, name, capacity, changed);
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
            if (key == null || key.isEmpty() || resourceKey == null || resourceKey.isEmpty()
                    || acquireStepId == null || acquireStepId.isEmpty()
                    || releaseStepId == null || releaseStepId.isEmpty() || units < 1)
                throw new IllegalArgumentException("Capacity rule is incomplete");
            this.key = key;
            this.persistedId = persistedId;
            this.resourceKey = resourceKey;
            this.acquireStepId = acquireStepId;
            this.releaseStepId = releaseStepId;
            this.units = units;
        }

        static Lease persisted(StepResourceLease value) {
            return new Lease(value.id, value.id, value.resourceId,
                    value.acquireStepId, value.releaseStepId, value.units);
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putString("key", key); bundle.putString("persisted", persistedId);
            bundle.putString("resource", resourceKey);
            bundle.putString("acquire", acquireStepId); bundle.putString("release", releaseStepId);
            bundle.putInt("units", units);
            return bundle;
        }

        static Lease fromBundle(Bundle bundle) {
            return new Lease(bundle.getString("key"), bundle.getString("persisted"),
                    bundle.getString("resource"), bundle.getString("acquire"),
                    bundle.getString("release"), bundle.getInt("units"));
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Lease)) return false;
            Lease value = (Lease) other;
            return key.equals(value.key) && Objects.equals(persistedId, value.persistedId)
                    && resourceKey.equals(value.resourceKey)
                    && acquireStepId.equals(value.acquireStepId)
                    && releaseStepId.equals(value.releaseStepId) && units == value.units;
        }

        @Override public int hashCode() {
            return Objects.hash(key, persistedId, resourceKey, acquireStepId,
                    releaseStepId, units);
        }
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TaskFlowDraft)) return false;
        TaskFlowDraft value = (TaskFlowDraft) other;
        return transitions.equals(value.transitions) && resources.equals(value.resources)
                && leases.equals(value.leases)
                && nextResourceIdentity == value.nextResourceIdentity
                && nextLeaseIdentity == value.nextLeaseIdentity;
    }

    @Override public int hashCode() {
        return Objects.hash(transitions, resources, leases,
                nextResourceIdentity, nextLeaseIdentity);
    }
}

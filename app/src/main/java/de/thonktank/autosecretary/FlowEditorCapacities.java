package de.thonktank.autosecretary;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Capacity-only draft. Topology validation belongs to FlowEditorDraft, never a linear helper. */
public final class FlowEditorCapacities {
    public final List<TaskFlowDraft.Resource> resources;
    public final List<TaskFlowDraft.Lease> leases;
    private final int nextResourceIdentity;
    private final int nextLeaseIdentity;

    public FlowEditorCapacities(List<TaskFlowDraft.Resource> resources, List<TaskFlowDraft.Lease> leases,
                                 int nextResourceIdentity, int nextLeaseIdentity) {
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        this.leases = Collections.unmodifiableList(new ArrayList<>(leases));
        this.nextResourceIdentity = nextResourceIdentity;
        this.nextLeaseIdentity = nextLeaseIdentity;
    }

    public static FlowEditorCapacities empty() {
        return new FlowEditorCapacities(Collections.emptyList(), Collections.emptyList(), 1, 1);
    }

    public TaskFlowDraft.Resource resource(String key) {
        for (TaskFlowDraft.Resource value : resources) if (value.key.equals(key)) return value;
        throw new IllegalArgumentException("Kapazität existiert nicht");
    }

    public FlowEditorCapacities addResource(String name, int total) {
        List<TaskFlowDraft.Resource> next = new ArrayList<>(resources);
        next.add(new TaskFlowDraft.Resource("flow-resource:" + nextResourceIdentity, null, name, total, true));
        return new FlowEditorCapacities(next, leases, nextResourceIdentity + 1, nextLeaseIdentity);
    }

    public FlowEditorCapacities updateResource(String id, String name, int total) {
        TaskFlowDraft.Resource current = resource(id);
        List<TaskFlowDraft.Resource> next = new ArrayList<>();
        for (TaskFlowDraft.Resource value : resources) next.add(!value.key.equals(id) ? value
                : new TaskFlowDraft.Resource(id, current.persistedId, name, total, true));
        return new FlowEditorCapacities(next, leases, nextResourceIdentity, nextLeaseIdentity);
    }

    public FlowEditorCapacities addLease(String resource, String from, String to, int units) {
        resource(resource);
        List<TaskFlowDraft.Lease> next = new ArrayList<>(leases);
        next.add(new TaskFlowDraft.Lease("flow-lease:" + nextLeaseIdentity, null, resource, from, to, units));
        return new FlowEditorCapacities(resources, next, nextResourceIdentity, nextLeaseIdentity + 1);
    }

    public FlowEditorCapacities updateLease(String id, String resource, String from, String to, int units) {
        resource(resource);
        List<TaskFlowDraft.Lease> next = new ArrayList<>(); boolean found = false;
        for (TaskFlowDraft.Lease value : leases) {
            if (value.key.equals(id)) {
                found = true;
                next.add(new TaskFlowDraft.Lease(id, value.persistedId, resource, from, to, units));
            } else next.add(value);
        }
        if (!found) throw new IllegalArgumentException("Zuordnung existiert nicht");
        return new FlowEditorCapacities(resources, next, nextResourceIdentity, nextLeaseIdentity);
    }

    Bundle toBundle() {
        // Reuse value codecs, not TaskFlowDraft's topology-dependent mutations.
        return new TaskFlowDraft(Collections.emptyList(), resources, leases,
                nextResourceIdentity, nextLeaseIdentity).toBundle();
    }

    static FlowEditorCapacities fromBundle(Bundle bundle) {
        TaskFlowDraft values = TaskFlowDraft.fromBundle(bundle);
        return new FlowEditorCapacities(values.resources, values.leases,
                values.nextResourceIdentity, values.nextLeaseIdentity);
    }
}

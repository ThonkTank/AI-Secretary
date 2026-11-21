package de.thonktank.autosecretary;

import android.os.Bundle;
import de.thonktank.autosecretary.domain.model.FlowGraphEdit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Capacity-only draft. Topology validation belongs to FlowEditorDraft, never a linear helper. */
public final class FlowEditorCapacities {
    public final List<FlowGraphEdit.Resource> resources;
    public final List<FlowGraphEdit.Lease> leases;
    private final int nextResourceIdentity;
    private final int nextLeaseIdentity;

    public FlowEditorCapacities(List<FlowGraphEdit.Resource> resources, List<FlowGraphEdit.Lease> leases,
                                 int nextResourceIdentity, int nextLeaseIdentity) {
        this.resources = Collections.unmodifiableList(new ArrayList<>(resources));
        this.leases = Collections.unmodifiableList(new ArrayList<>(leases));
        this.nextResourceIdentity = Math.max(1, nextResourceIdentity);
        this.nextLeaseIdentity = Math.max(1, nextLeaseIdentity);
    }

    public static FlowEditorCapacities empty() {
        return new FlowEditorCapacities(Collections.emptyList(), Collections.emptyList(), 1, 1);
    }

    public FlowGraphEdit.Resource resource(String key) {
        for (FlowGraphEdit.Resource value : resources) if (value.key.equals(key)) return value;
        throw new IllegalArgumentException("Kapazität existiert nicht");
    }

    public FlowEditorCapacities addResource(String name, int total) {
        List<FlowGraphEdit.Resource> next = new ArrayList<>(resources);
        next.add(new FlowGraphEdit.Resource("flow-resource:" + nextResourceIdentity, null, name, total, true));
        return new FlowEditorCapacities(next, leases, nextResourceIdentity + 1, nextLeaseIdentity);
    }

    public FlowEditorCapacities updateResource(String id, String name, int total) {
        FlowGraphEdit.Resource current = resource(id);
        List<FlowGraphEdit.Resource> next = new ArrayList<>();
        for (FlowGraphEdit.Resource value : resources) next.add(!value.key.equals(id) ? value
                : new FlowGraphEdit.Resource(id, current.persistedId, name, total, true));
        return new FlowEditorCapacities(next, leases, nextResourceIdentity, nextLeaseIdentity);
    }

    public FlowEditorCapacities addLease(String resource, String from, String to, int units) {
        resource(resource);
        List<FlowGraphEdit.Lease> next = new ArrayList<>(leases);
        next.add(new FlowGraphEdit.Lease("flow-lease:" + nextLeaseIdentity, null, resource, from, to, units));
        return new FlowEditorCapacities(resources, next, nextResourceIdentity, nextLeaseIdentity + 1);
    }

    public FlowEditorCapacities updateLease(String id, String resource, String from, String to, int units) {
        resource(resource);
        List<FlowGraphEdit.Lease> next = new ArrayList<>(); boolean found = false;
        for (FlowGraphEdit.Lease value : leases) {
            if (value.key.equals(id)) {
                found = true;
                next.add(new FlowGraphEdit.Lease(id, value.persistedId, resource, from, to, units));
            } else next.add(value);
        }
        if (!found) throw new IllegalArgumentException("Zuordnung existiert nicht");
        return new FlowEditorCapacities(resources, next, nextResourceIdentity, nextLeaseIdentity);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        ArrayList<Bundle> resourceValues = new ArrayList<>();
        for (FlowGraphEdit.Resource resource : resources) {
            Bundle item = new Bundle();
            item.putString("key", resource.key);
            item.putString("persisted", resource.persistedId);
            item.putString("name", resource.name);
            item.putInt("capacity", resource.capacity);
            item.putBoolean("changed", resource.changed);
            resourceValues.add(item);
        }
        ArrayList<Bundle> leaseValues = new ArrayList<>();
        for (FlowGraphEdit.Lease lease : leases) {
            Bundle item = new Bundle();
            item.putString("key", lease.key);
            item.putString("persisted", lease.persistedId);
            item.putString("resource", lease.resourceKey);
            item.putString("acquire", lease.acquireStepId);
            item.putString("release", lease.releaseStepId);
            item.putInt("units", lease.units);
            leaseValues.add(item);
        }
        // Keep the capacity bundle compatible with existing saved drafts.
        bundle.putParcelableArrayList("transitions", new ArrayList<Bundle>());
        bundle.putParcelableArrayList("resources", resourceValues);
        bundle.putParcelableArrayList("leases", leaseValues);
        bundle.putInt("next_resource", nextResourceIdentity);
        bundle.putInt("next_lease", nextLeaseIdentity);
        return bundle;
    }

    @SuppressWarnings("deprecation")
    static FlowEditorCapacities fromBundle(Bundle bundle) {
        if (bundle == null) return empty();
        try {
            List<FlowGraphEdit.Resource> resources = new ArrayList<>();
            ArrayList<Bundle> resourceValues = bundle.getParcelableArrayList("resources");
            if (resourceValues != null) for (Bundle item : resourceValues)
                resources.add(new FlowGraphEdit.Resource(item.getString("key"), item.getString("persisted"),
                        item.getString("name"), item.getInt("capacity"), item.getBoolean("changed")));
            List<FlowGraphEdit.Lease> leases = new ArrayList<>();
            ArrayList<Bundle> leaseValues = bundle.getParcelableArrayList("leases");
            if (leaseValues != null) for (Bundle item : leaseValues)
                leases.add(new FlowGraphEdit.Lease(item.getString("key"), item.getString("persisted"),
                        item.getString("resource"), item.getString("acquire"), item.getString("release"),
                        item.getInt("units")));
            return new FlowEditorCapacities(resources, leases,
                    bundle.getInt("next_resource", 1), bundle.getInt("next_lease", 1));
        } catch (RuntimeException invalid) {
            return empty();
        }
    }
}

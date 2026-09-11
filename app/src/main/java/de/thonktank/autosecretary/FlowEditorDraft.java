package de.thonktank.autosecretary;

import android.os.Bundle;
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.FlowGraphDefinition;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.FlowTileGraph;
import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.StepTransition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Entire dedicated editor draft. No edit writes a resource or a task to the repository. */
public final class FlowEditorDraft {
    public final String taskId;
    public final String name;
    public final List<EditorStepState> steps;
    public final FlowTileGraph graph;
    public final Map<String, FlowDelayPolicy> waits;
    public final FlowEditorCapacities capacities;
    public final Map<String, Boolean> releaseAfterWait;
    public final int nextIdentity;

    public FlowEditorDraft(String taskId, String name, List<EditorStepState> steps,
                           FlowTileGraph graph, Map<String, FlowDelayPolicy> waits,
                           FlowEditorCapacities capacities, Map<String, Boolean> releaseAfterWait,
                           int nextIdentity) {
        this.taskId = taskId;
        this.name = Objects.requireNonNull(name, "name");
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.graph = Objects.requireNonNull(graph, "graph");
        this.waits = Collections.unmodifiableMap(new LinkedHashMap<>(waits));
        this.capacities = Objects.requireNonNull(capacities, "capacities");
        this.releaseAfterWait = Collections.unmodifiableMap(new LinkedHashMap<>(releaseAfterWait));
        this.nextIdentity = nextIdentity;
        List<String> ids = new ArrayList<>();
        for (EditorStepState step : steps) ids.add(step.id);
        if (!ids.equals(graph.stepIds) || !waits.keySet().equals(new java.util.LinkedHashSet<>(ids))
                || waits.containsValue(null))
            throw new IllegalArgumentException("Schritte, Wartezeiten und Kacheln passen nicht zusammen");
    }

    public static FlowEditorDraft empty() {
        return new FlowEditorDraft(null, "", Collections.emptyList(),
                new FlowTileGraph(Collections.emptyList(), Collections.emptyList()),
                Collections.emptyMap(), FlowEditorCapacities.empty(), Collections.emptyMap(), 1);
    }

    public static FlowEditorDraft from(StepFlowSetup setup) {
        List<EditorStepState> steps = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<FlowTileGraph.Link> links = new ArrayList<>();
        Map<String, FlowDelayPolicy> waits = new LinkedHashMap<>();
        setup.steps.forEach(step -> {
            steps.add(EditorStepState.from(step)); ids.add(step.id);
            waits.put(step.id, FlowDelayPolicy.fixed(0L));
        });
        for (StepTransition edge : setup.transitions) {
            links.add(new FlowTileGraph.Link(edge.sourceStepId, edge.targetStepId));
            waits.put(edge.sourceStepId, edge.delay);
        }
        TaskFlowDraft existing = TaskFlowDraft.from(setup);
        FlowEditorCapacities capacities = new FlowEditorCapacities(existing.resources,
                existing.leases, existing.nextResourceIdentity, existing.nextLeaseIdentity);
        return new FlowEditorDraft(setup.task.id.value, setup.task.title, steps,
                new FlowTileGraph(ids, links), waits, capacities, Collections.emptyMap(), 1);
    }

    public EditorStepState step(String id) {
        for (EditorStepState step : steps) if (step.id.equals(id)) return step;
        throw new IllegalArgumentException("Schritt existiert nicht");
    }

    public FlowEditorDraft rename(String name) {
        return copy(name, steps, graph, waits, capacities, releaseAfterWait, nextIdentity);
    }

    public FlowEditorDraft addStep(String name, FlowDelayPolicy wait) {
        validateName(name);
        EditorStepState step = EditorStepState.blank(nextIdentity).withText(name.trim());
        List<EditorStepState> next = new ArrayList<>(steps);
        next.add(step);
        Map<String, FlowDelayPolicy> delays = new LinkedHashMap<>(waits);
        delays.put(step.id, Objects.requireNonNull(wait, "wait"));
        return copy(this.name, next, graph.add(step.id), delays, capacities,
                releaseAfterWait, nextIdentity + 1);
    }

    public FlowEditorDraft editStep(String id, String name, FlowDelayPolicy wait) {
        validateName(name);
        step(id);
        List<EditorStepState> next = new ArrayList<>();
        for (EditorStepState step : steps)
            next.add(step.id.equals(id) ? step.withText(name.trim()) : step);
        Map<String, FlowDelayPolicy> delays = new LinkedHashMap<>(waits);
        delays.put(id, Objects.requireNonNull(wait, "wait"));
        return copy(this.name, next, graph, delays, capacities, releaseAfterWait, nextIdentity);
    }

    public FlowEditorDraft cadence(String id, int days) {
        if (!graph.roots().contains(id) || days < 1)
            throw new IllegalArgumentException("Nur Starts besitzen einen Rhythmus");
        List<EditorStepState> next = new ArrayList<>();
        for (EditorStepState step : steps)
            next.add(!step.id.equals(id) ? step : days == 1
                    ? step.withCadenceMode(StepCadenceMode.ALWAYS) : step.withIntervalDays(days));
        return copy(name, next, graph, waits, capacities, releaseAfterWait, nextIdentity);
    }

    public FlowEditorDraft withGraph(FlowTileGraph value) {
        if (!new java.util.HashSet<>(value.stepIds).equals(new java.util.HashSet<>(graph.stepIds)))
            throw new IllegalArgumentException("Verschieben darf keine Schritte ersetzen");
        List<EditorStepState> next = new ArrayList<>();
        for (String id : value.stepIds) next.add(step(id));
        return copy(name, next, value, waits, capacities, releaseAfterWait, nextIdentity);
    }

    public FlowEditorDraft withCapacities(FlowEditorCapacities value) {
        Map<String, Boolean> release = new LinkedHashMap<>();
        for (TaskFlowDraft.Lease lease : value.leases)
            release.put(lease.key, releaseAfterWait.getOrDefault(lease.key, false));
        return copy(name, steps, graph, waits, value, release, nextIdentity);
    }

    public FlowEditorDraft releaseAfter(String leaseId, boolean afterWait) {
        if (capacities.leases.stream().noneMatch(lease -> lease.key.equals(leaseId)))
            throw new IllegalArgumentException("Zuordnung existiert nicht");
        Map<String, Boolean> release = new LinkedHashMap<>(releaseAfterWait);
        release.put(leaseId, afterWait);
        return copy(name, steps, graph, waits, capacities, release, nextIdentity);
    }

    /** Rechecked at the save boundary: moving a tile must not silently remove a capacity rule. */
    public void validateForSave() {
        if (name.trim().isEmpty() || name.trim().length() > 120)
            throw new Problem("name", null, "Ablaufname muss 1 bis 120 Zeichen enthalten");
        if (steps.isEmpty()) throw new Problem("step", null, "Mindestens einen Schritt ergänzen");
        for (EditorStepState step : steps) {
            try { validateName(step.text); }
            catch (IllegalArgumentException invalid) { throw new Problem("step", step.id, invalid.getMessage()); }
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (TaskFlowDraft.Resource resource : capacities.resources) {
            if (!keys.add(resource.key) || !names.add(CapacityResource.normalizeName(resource.name)))
                throw new Problem("resource", resource.key, "Kapazität ist doppelt: " + resource.name);
        }
        Map<String, Map<String, Long>> acquired = new LinkedHashMap<>();
        for (TaskFlowDraft.Lease lease : capacities.leases) {
            if (!graph.stepIds.contains(lease.acquireStepId) || !graph.stepIds.contains(lease.releaseStepId)
                    || !graph.reaches(lease.acquireStepId, lease.releaseStepId))
                throw new Problem("lease", lease.key, "Freigabe muss am selben oder einem folgenden Schritt liegen");
            if (!keys.contains(lease.resourceKey))
                throw new Problem("lease", lease.key, "Die zugeordnete Kapazität fehlt");
            Map<String, Long> byResource = acquired.computeIfAbsent(lease.acquireStepId,
                    ignored -> new LinkedHashMap<>());
            long total = byResource.getOrDefault(lease.resourceKey, 0L) + lease.units;
            byResource.put(lease.resourceKey, total);
            if (total > capacities.resource(lease.resourceKey).capacity)
                throw new Problem("lease", lease.key, "Die Zuordnungen dieses Schritts überschreiten die Kapazität");
        }
    }

    /** Uses the same stable editor keys until the atomic save maps them to persisted IDs. */
    public FlowGraphDefinition graphDefinition(TaskId resolvedTaskId) {
        validateForSave();
        List<FlowGraphDefinition.Node> nodes = new ArrayList<>();
        for (EditorStepState step : steps)
            nodes.add(new FlowGraphDefinition.Node(step.id, step.text, step.prescription,
                    step.note, waits.get(step.id)));
        List<FlowGraphDefinition.Lease> leases = new ArrayList<>();
        for (TaskFlowDraft.Lease lease : capacities.leases)
            leases.add(new FlowGraphDefinition.Lease(lease.key, lease.resourceKey,
                    lease.acquireStepId, lease.releaseStepId, lease.units,
                    releaseAfterWait.getOrDefault(lease.key, false)));
        return new FlowGraphDefinition(resolvedTaskId, graph, nodes, leases);
    }

    public static final class Problem extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public final String kind;
        public final String elementId;

        Problem(String kind, String elementId, String message) {
            super(message);
            this.kind = kind;
            this.elementId = elementId;
        }
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("task", taskId); bundle.putString("name", name);
        bundle.putInt("next", nextIdentity);
        ArrayList<Bundle> nodes = new ArrayList<>();
        for (EditorStepState step : steps) {
            Bundle value = step.toBundle();
            FlowDelayPolicy wait = waits.get(step.id);
            value.putString("flow_wait_mode", wait.mode.name());
            value.putLong("flow_wait_default", wait.defaultDelayMillis);
            if (wait.lastUsedDelayMillis != null) value.putLong("flow_wait_last", wait.lastUsedDelayMillis);
            nodes.add(value);
        }
        bundle.putParcelableArrayList("steps", nodes);
        ArrayList<String> links = new ArrayList<>();
        for (FlowTileGraph.Link edge : graph.links) { links.add(edge.source); links.add(edge.target); }
        bundle.putStringArrayList("links", links);
        bundle.putBundle("capacities", capacities.toBundle());
        Bundle releases = new Bundle();
        releaseAfterWait.forEach(releases::putBoolean);
        bundle.putBundle("release", releases);
        return bundle;
    }

    @SuppressWarnings("deprecation")
    public static FlowEditorDraft fromBundle(Bundle bundle) {
        if (bundle == null) return empty();
        List<EditorStepState> steps = new ArrayList<>(); List<String> ids = new ArrayList<>();
        Map<String, FlowDelayPolicy> waits = new LinkedHashMap<>();
        ArrayList<Bundle> nodes = bundle.getParcelableArrayList("steps");
        if (nodes != null) for (Bundle value : nodes) {
            EditorStepState step = EditorStepState.fromBundle(value);
            steps.add(step); ids.add(step.id);
            waits.put(step.id, new FlowDelayPolicy(FlowDelayPolicy.Mode.valueOf(
                    value.getString("flow_wait_mode", "FIXED")), value.getLong("flow_wait_default"),
                    value.containsKey("flow_wait_last") ? value.getLong("flow_wait_last") : null));
        }
        ArrayList<String> values = bundle.getStringArrayList("links");
        List<FlowTileGraph.Link> links = new ArrayList<>();
        if (values != null) {
            if (values.size() % 2 != 0) throw new IllegalArgumentException("Verbindung ist unvollständig");
            for (int i = 0; i < values.size(); i += 2) links.add(new FlowTileGraph.Link(values.get(i), values.get(i + 1)));
        }
        Map<String, Boolean> release = new LinkedHashMap<>();
        Bundle releases = bundle.getBundle("release");
        if (releases != null) for (String id : releases.keySet()) release.put(id, releases.getBoolean(id));
        return new FlowEditorDraft(bundle.getString("task"), bundle.getString("name", ""), steps,
                new FlowTileGraph(ids, links), waits, FlowEditorCapacities.fromBundle(bundle.getBundle("capacities")),
                release, bundle.getInt("next", 1));
    }

    private FlowEditorDraft copy(String name, List<EditorStepState> steps, FlowTileGraph graph,
                                 Map<String, FlowDelayPolicy> waits, FlowEditorCapacities capacities,
                                 Map<String, Boolean> release, int next) {
        return new FlowEditorDraft(taskId, name, steps, graph, waits, capacities, release, next);
    }

    private static void validateName(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 80)
            throw new IllegalArgumentException("Schrittname muss 1 bis 80 Zeichen enthalten");
    }
}

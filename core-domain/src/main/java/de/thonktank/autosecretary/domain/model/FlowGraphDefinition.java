package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete, immutable input to a graph run. Waits belong to nodes, including terminal nodes. */
public final class FlowGraphDefinition {
    public final TaskId taskId;
    public final FlowTileGraph graph;
    public final Map<String, Node> nodes;
    public final List<Lease> leases;

    public FlowGraphDefinition(TaskId taskId, FlowTileGraph graph, List<Node> nodes,
                               List<Lease> leases) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.graph = Objects.requireNonNull(graph, "graph");
        Map<String, Node> indexed = new LinkedHashMap<>();
        for (Node node : nodes)
            if (indexed.put(node.id, node) != null)
                throw new FlowDefinitionException("Schritt ist doppelt");
        if (indexed.isEmpty() || !indexed.keySet().equals(new java.util.HashSet<>(graph.stepIds)))
            throw new FlowDefinitionException("Ablauf und Schritte passen nicht zusammen");
        this.nodes = Collections.unmodifiableMap(indexed);
        this.leases = Collections.unmodifiableList(new ArrayList<>(leases));
        Map<String, Lease> bindings = new HashMap<>();
        for (Lease lease : leases) {
            if (bindings.put(lease.id, lease) != null)
                throw new FlowDefinitionException("Kapazitätszuordnung ist doppelt");
            if (!graph.reaches(lease.acquireStepId, lease.releaseStepId))
                throw new FlowDefinitionException("Freigabe muss am selben oder einem folgenden Schritt liegen");
        }
    }

    public static final class Node {
        public final String id;
        public final String title;
        public final StepPrescription prescription;
        public final String note;
        public final FlowDelayPolicy waitAfter;

        public Node(String id, String title, StepPrescription prescription, String note,
                    FlowDelayPolicy waitAfter) {
            if (id == null || id.trim().isEmpty() || title == null || title.trim().isEmpty())
                throw new FlowDefinitionException("Schritt-ID und Name fehlen");
            this.id = id;
            this.title = title;
            this.prescription = Objects.requireNonNull(prescription, "prescription");
            this.note = Objects.requireNonNull(note, "note");
            this.waitAfter = Objects.requireNonNull(waitAfter, "waitAfter");
        }
    }

    public static final class Lease {
        public final String id;
        public final String resourceId;
        public final String acquireStepId;
        public final String releaseStepId;
        public final int units;
        public final boolean releaseAfterWait;

        public Lease(String id, String resourceId, String acquireStepId, String releaseStepId,
                     int units, boolean releaseAfterWait) {
            if (id == null || id.trim().isEmpty() || resourceId == null || resourceId.trim().isEmpty()
                    || acquireStepId == null || releaseStepId == null || units < 1)
                throw new FlowDefinitionException("Kapazitätszuordnung ist unvollständig");
            this.id = id;
            this.resourceId = resourceId;
            this.acquireStepId = acquireStepId;
            this.releaseStepId = releaseStepId;
            this.units = units;
            this.releaseAfterWait = releaseAfterWait;
        }
    }
}

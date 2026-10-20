package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable topology shared by tile gestures, accessible commands and run snapshots.
 * Tile order is only a stable layout tie-breaker, never an execution cursor.
 */
public final class FlowTileGraph {
    public enum Placement { BEFORE, AFTER, LEFT, RIGHT, BESIDE }
    public final List<String> stepIds;
    public final List<Link> links;
    private final Map<String, List<String>> incoming = new LinkedHashMap<>();
    private final Map<String, List<String>> outgoing = new LinkedHashMap<>();

    public FlowTileGraph(List<String> stepIds, List<Link> links) {
        if (stepIds == null || links == null)
            throw new FlowDefinitionException("Ablauf ist unvollständig");
        this.stepIds = Collections.unmodifiableList(new ArrayList<>(stepIds));
        this.links = Collections.unmodifiableList(new ArrayList<>(links));
        for (String id : stepIds) {
            if (id == null || id.trim().isEmpty() || incoming.containsKey(id))
                throw new FlowDefinitionException("Schritt-ID fehlt oder ist doppelt");
            incoming.put(id, new ArrayList<>());
            outgoing.put(id, new ArrayList<>());
        }
        Set<Link> unique = new LinkedHashSet<>();
        for (Link link : links) {
            if (link == null || !incoming.containsKey(link.source)
                    || !incoming.containsKey(link.target) || !unique.add(link))
                throw new FlowDefinitionException("Verbindung fehlt, ist doppelt oder verweist auf fremde Schritte");
            incoming.get(link.target).add(link.source);
            outgoing.get(link.source).add(link.target);
        }
        topologicalOrder();
    }

    public List<String> predecessors(String id) {
        requireStep(id);
        return Collections.unmodifiableList(incoming.get(id));
    }

    public List<String> successors(String id) {
        requireStep(id);
        return Collections.unmodifiableList(outgoing.get(id));
    }

    public List<String> roots() {
        List<String> result = new ArrayList<>();
        for (String id : stepIds) if (incoming.get(id).isEmpty()) result.add(id);
        return Collections.unmodifiableList(result);
    }

    /** Stable Kahn ordering, used for layout/migration only, never to serialize execution. */
    public List<String> topologicalOrder() {
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (String id : stepIds) remaining.put(id, incoming.get(id).size());
        List<String> result = new ArrayList<>();
        while (result.size() < stepIds.size()) {
            String next = null;
            for (String id : stepIds)
                if (remaining.get(id) == 0) { next = id; break; }
            if (next == null) throw new FlowDefinitionException("Ablauf enthält einen Kreis");
            result.add(next);
            remaining.put(next, -1);
            for (String target : outgoing.get(next))
                remaining.put(target, remaining.get(target) - 1);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean reaches(String source, String target) {
        requireStep(source);
        requireStep(target);
        return reachableIds(source).contains(target);
    }

    private Set<String> reachableIds(String source) {
        Set<String> result = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(source);
        for (int i = 0; i < pending.size(); i++) {
            String current = pending.get(i);
            if (result.add(current)) pending.addAll(outgoing.get(current));
        }
        return result;
    }

    /** Other alternative roots are absent, including their edges into shared successors. */
    public FlowTileGraph reachableFrom(String root) {
        requireStep(root);
        Set<String> reachable = reachableIds(root);
        List<String> nodes = new ArrayList<>();
        for (String id : stepIds) if (reachable.contains(id)) nodes.add(id);
        List<Link> edges = new ArrayList<>();
        for (Link edge : links)
            if (reachable.contains(edge.source) && reachable.contains(edge.target)) edges.add(edge);
        return new FlowTileGraph(nodes, edges);
    }

    /** A branch ends before a shared successor, even for unequal-length nested forks. */
    public Set<String> branchFrom(String source) {
        requireStep(source);
        Set<String> branch = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(source);
        for (int i = 0; i < pending.size(); i++) {
            String current = pending.get(i);
            if (!branch.add(current)) continue;
            for (String next : outgoing.get(current))
                if (incoming.get(next).size() == 1) pending.add(next);
        }
        return Collections.unmodifiableSet(branch);
    }

    public FlowTileGraph add(String id) {
        List<String> nodes = new ArrayList<>(stepIds);
        nodes.add(id);
        return new FlowTileGraph(nodes, links);
    }

    /** New editor steps continue every open lane; add() remains the unconnected import primitive. */
    public FlowTileGraph append(String id) {
        FlowTileGraph next = add(id);
        List<String> ends = new ArrayList<>();
        for (String step : stepIds) if (successors(step).isEmpty()) ends.add(step);
        return ends.isEmpty() ? next : next.join(ends, id);
    }

    /**
     * Changes a tile's occupied lanes. Released lanes continue at its successors, so resizing
     * a tile inside a parallel section does not accidentally remove the section's later join.
     * Expanding over a lane removes bypasses that have become redundant.
     */
    public FlowTileGraph after(String id, List<String> parents) {
        requireStep(id);
        if (parents == null || parents.isEmpty())
            throw new FlowDefinitionException("Mindestens einen Vorgänger auswählen");
        Set<String> wanted = new LinkedHashSet<>(parents);
        for (String parent : wanted) {
            requireStep(parent);
            if (reaches(id, parent)) throw new FlowDefinitionException("Schritt liegt im eigenen Folgestapel");
        }
        Set<Link> edges = new LinkedHashSet<>(links);
        for (String old : predecessors(id)) if (!wanted.contains(old)) {
            edges.remove(new Link(old, id));
            for (String next : successors(id)) edges.add(new Link(old, next));
        }
        for (String parent : wanted) {
            edges.add(new Link(parent, id));
            for (String next : successors(parent)) {
                if (next.equals(id)) continue;
                if (reaches(id, next)) edges.remove(new Link(parent, next));
                else if (!predecessors(id).contains(parent) && !reaches(next, id)) {
                    // Occupying a new lane also inserts before that lane's continuation.
                    edges.remove(new Link(parent, next));
                    edges.add(new Link(id, next));
                }
            }
        }
        return new FlowTileGraph(stepIds, new ArrayList<>(edges));
    }

    public FlowTileGraph remove(String id) {
        requireStep(id);
        List<String> nodes = new ArrayList<>(stepIds);
        nodes.remove(id);
        List<Link> edges = new ArrayList<>();
        for (Link edge : links)
            if (!edge.source.equals(id) && !edge.target.equals(id)) edges.add(edge);
        return new FlowTileGraph(nodes, edges);
    }

    /** Explicitly adds prerequisites to an existing successor; never removes unrelated edges. */
    public FlowTileGraph join(List<String> branchEnds, String successor) {
        requireStep(successor);
        if (branchEnds == null || branchEnds.isEmpty())
            throw new FlowDefinitionException("Voraussetzung fehlt");
        Set<Link> edges = new LinkedHashSet<>(links);
        for (String end : branchEnds) {
            requireStep(end);
            edges.add(new Link(end, successor));
        }
        return new FlowTileGraph(stepIds, new ArrayList<>(edges));
    }

    /**
     * Pure drop proposal. The caller commits this value only on release; cancel discards it.
     * Lateral placement shares the target's prerequisites and continuation. BESIDE is the
     * compatibility spelling of RIGHT. Alternative roots remain alternative run starts.
     */
    public FlowTileGraph place(String source, String target, Placement placement, boolean branch) {
        requireStep(source);
        requireStep(target);
        Objects.requireNonNull(placement, "placement");
        Set<String> moved = branch ? branchFrom(source) : Collections.singleton(source);
        if (moved.contains(target)) throw new FlowDefinitionException("Schritt liegt im verschobenen Zweig");
        Set<Link> edges = new LinkedHashSet<>();
        List<Link> entering = new ArrayList<>(), leaving = new ArrayList<>();
        for (Link edge : links) {
            boolean a = moved.contains(edge.source), b = moved.contains(edge.target);
            if (a == b) edges.add(edge);
            else if (b) entering.add(edge);
            else leaving.add(edge);
        }
        for (Link before : entering) for (Link after : leaving)
            if (!new FlowTileGraph(stepIds, new ArrayList<>(edges))
                    .reaches(before.source, after.target))
                edges.add(new Link(before.source, after.target));
        List<String> tails = new ArrayList<>();
        for (String id : moved)
            if (outgoing.get(id).stream().noneMatch(moved::contains)) tails.add(id);
        List<Link> atTarget = new ArrayList<>(edges);
        if (placement == Placement.BEFORE) {
            for (Link edge : atTarget) if (edge.target.equals(target)) {
                edges.remove(edge);
                edges.add(new Link(edge.source, source));
            }
            for (String tail : tails) edges.add(new Link(tail, target));
        } else if (placement == Placement.AFTER) {
            for (Link edge : atTarget) if (edge.source.equals(target)) {
                edges.remove(edge);
                for (String tail : tails) edges.add(new Link(tail, edge.target));
            }
            edges.add(new Link(target, source));
        } else {
            for (Link edge : atTarget) if (edge.target.equals(target))
                edges.add(new Link(edge.source, source));
            for (Link edge : atTarget) if (edge.source.equals(target))
                for (String tail : tails) edges.add(new Link(tail, edge.target));
        }
        List<String> ordered = new ArrayList<>(stepIds);
        ordered.removeAll(moved);
        int at = ordered.indexOf(target) +
                (placement == Placement.BEFORE || placement == Placement.LEFT ? 0 : 1);
        List<String> movedOrder = new ArrayList<>();
        for (String id : stepIds) if (moved.contains(id)) movedOrder.add(id);
        ordered.addAll(at, movedOrder);
        return new FlowTileGraph(ordered, new ArrayList<>(edges));
    }

    private void requireStep(String id) {
        if (!incoming.containsKey(id)) throw new FlowDefinitionException("Schritt existiert nicht");
    }

    public static final class Link {
        public final String source;
        public final String target;

        public Link(String source, String target) {
            if (source == null || target == null || source.equals(target))
                throw new FlowDefinitionException("Ein Schritt kann nicht sich selbst voraussetzen");
            this.source = source;
            this.target = target;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Link && source.equals(((Link) other).source)
                    && target.equals(((Link) other).target);
        }

        @Override public int hashCode() { return Objects.hash(source, target); }
    }
}

package com.autosecretary.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generic utility for assembling flat lists into tree structures based on parent-child relationships.
 *
 * <p>TreeBuilder takes a flat list of items and uses their parent-child relationships to construct
 * a hierarchical tree. Items whose parent IDs don't exist in the set become roots; all other items
 * become children of their parents. Items can have zero, one, or multiple parents, supporting
 * DAG (directed acyclic graph) structures beyond simple trees.
 *
 * <p>The tree structure is defined by five callback functions provided to the constructor:
 * <ul>
 *   <li><code>getId</code>: Extracts the unique ID from an item
 *   <li><code>getParentIds</code>: Returns the parent IDs for an item (may be empty or have multiple entries)
 *   <li><code>addChild</code>: Adds a child to a parent's child collection
 *   <li><code>getChildren</code>: Retrieves the current children of an item
 *   <li><code>resetChildren</code>: Clears all children from an item (called before building to reset state)
 * </ul>
 *
 * <p><strong>Example:</strong> Building a task tree from a flat list of Task objects:
 * <pre>
 * TreeBuilder&lt;Task&gt; taskBuilder = new TreeBuilder&lt;&gt;(
 *     task -&gt; task.id,                           // getId
 *     task -&gt; task.parentIds,                    // getParentIds
 *     (parent, child) -&gt; parent.children.add(child),  // addChild
 *     task -&gt; task.children,                     // getChildren
 *     task -&gt; task.children.clear()              // resetChildren
 * );
 * List&lt;Task&gt; roots = taskBuilder.buildTree(allTasks);
 * </pre>
 *
 * <p>This utility is used throughout the application to support hierarchical views (e.g., task
 * trees in Manage mode, calendar event grouping in Checklist mode).
 *
 * @param <T> the type of items being organized into a tree
 */
public class TreeBuilder<T> {

    private final Function<T, String> getId;
    private final Function<T, List<String>> getParentIds;
    private final BiConsumer<T, T> addChild;
    private final Function<T, List<T>> getChildren;
    private final Consumer<T> resetChildren;

    /**
     * Constructs a TreeBuilder with callbacks that define the tree structure.
     *
     * @param getId extracts the unique identifier from an item; must return a non-null, stable value
     * @param getParentIds returns the list of parent IDs for an item; may be empty (for roots) or
     *        contain multiple IDs (for items with multiple parents)
     * @param addChild called when building the tree: {@code addChild.accept(parent, child)} adds
     *        {@code child} to {@code parent}'s child collection
     * @param getChildren retrieves the current children of an item; must reflect changes made via
     *        {@code addChild} and {@code resetChildren}
     * @param resetChildren clears all children from an item; called at the start of each
     *        {@link #buildTree(List)} invocation to ensure a clean slate for tree reconstruction.
     *        Important for TreeBuilder reuse.
     */
    public TreeBuilder(
            Function<T, String> getId,
            Function<T, List<String>> getParentIds,
            BiConsumer<T, T> addChild,
            Function<T, List<T>> getChildren,
            Consumer<T> resetChildren) {
        this.getId = getId;
        this.getParentIds = getParentIds;
        this.addChild = addChild;
        this.getChildren = getChildren;
        this.resetChildren = resetChildren;
    }

    /**
     * Assembles a flat list of items into a tree structure based on parent-child relationships.
     *
     * <p><strong>Algorithm:</strong> Items whose parent IDs all reference missing parents (parents not
     * in the item set) are treated as roots. All other items become children of their parents.
     * If an item has multiple parent IDs and all exist, it is added as a child to each parent.
     *
     * <p><strong>Edge cases:</strong>
     * <ul>
     *   <li>Missing parents: If an item references a parent ID that doesn't exist in the set, that
     *       parent link is ignored. If <em>all</em> parent IDs are missing, the item becomes a root.
     *   <li>Cycles: Cycles are tolerated; no error is raised. Use {@link #flatten(List)} with cycle
     *       detection if needed.
     *   <li>Multiple parents: An item can have multiple parents. It will be added as a child to all
     *       of them, supporting DAG structures.
     * </ul>
     *
     * <p><strong>Side effects:</strong> Calls {@code resetChildren} on all items before building to
     * clear any previous tree state. This allows safe reuse of the same TreeBuilder instance.
     *
     * @param items the flat list of items to organize
     * @return list of root items (items with no parents in the set), with children populated recursively
     */
    public List<T> buildTree(List<T> items) {
        // Reset children from any previous tree builds to ensure a clean state for this build.
        // Necessary for reuse of TreeBuilder instances.
        items.forEach(resetChildren);

        Map<String, T> map = new HashMap<>();
        List<T> roots = new ArrayList<>();

        // Index all items by ID for fast parent lookup
        items.forEach(item -> map.put(getId.apply(item), item));

        // Build parent-child relationships: items without parents in the set become roots
        for (T item : items) {
            List<String> parentIds = getParentIds.apply(item);
            boolean hasParentInMap = false;
            // If an item has multiple parent IDs, add it as a child to all parents that exist
            for (String parentId : parentIds) {
                T parent = map.get(parentId);
                if (parent != null) {
                    addChild.accept(parent, item);
                    hasParentInMap = true;
                }
            }
            // If no parent exists, this item is a root
            if (!hasParentInMap) {
                roots.add(item);
            }
        }

        return roots;
    }

    /**
     * Converts a tree back into a flat list via depth-first traversal.
     *
     * <p>Each item is included exactly once in the result, even if it appears in multiple subtrees
     * (DAG structures). Items are visited in depth-first order: parent first, then children
     * recursively.
     *
     * <p><strong>Use case:</strong> Preparing tree-structured data for bulk writes to storage or
     * iteration that requires a flat view.
     *
     * <p><strong>Cycle safety:</strong> A visited set ensures each item is processed only once,
     * even if the tree contains cycles.
     *
     * @param roots the root items (as returned by {@link #buildTree(List)})
     * @return all items in depth-first order, each item exactly once
     */
    public List<T> flatten(List<T> roots) {
        Set<T> visited = new HashSet<>();
        List<T> result = new ArrayList<>();
        for (T root : roots) {
            flattenNode(root, result, visited);
        }
        return result;
    }

    /**
     * Recursively flattens a single node and its descendants into a flat list.
     * Each node is added to the result exactly once (visited set prevents duplicates).
     * Depth-first: the node itself is added first, then its children recursively.
     */
    private void flattenNode(T node, List<T> result, Set<T> visited) {
        if (!visited.add(node)) return;  // Skip if already visited (cycle safety)
        result.add(node);
        for (T child : getChildren.apply(node)) {
            flattenNode(child, result, visited);
        }
    }

    /**
     * Recursively sorts all items in the tree at every level using the provided comparator.
     *
     * <p><strong>Behavior:</strong> Sorts the root list, then recursively sorts the children of
     * each root, and so on. Modifies the tree structure in place; does not return a new tree.
     *
     * <p><strong>Performance:</strong> O(n log n) per tree level; total complexity depends on tree
     * depth and the comparator cost.
     *
     * @param roots the root items to sort (and recursively sort their children)
     * @param comparator defines the sort order at each tree level
     */
    public void sortTree(List<T> roots, Comparator<T> comparator) {
        if (roots.isEmpty()) return;
        roots.sort(comparator);
        for (T node : roots) {
            sortTree(getChildren.apply(node), comparator);
        }
    }
}

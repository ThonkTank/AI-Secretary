package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.util.TreeBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility for building and flattening task trees using {@link TreeBuilder}.
 * Parent-child relationships are derived from each task's {@link TaskRelation} entries.
 * Orphaned tasks (missing parent references) are treated as roots.
 */
public final class TaskTreeOperations {

    private static final TreeBuilder<Task> TASK_TREE_BUILDER = new TreeBuilder<>(
            task -> task.core.id,
            task -> {
                List<String> ids = new ArrayList<>();
                for (TaskRelation rel : task.parents) {
                    ids.add(rel.parent);
                }
                return ids;
            },
            (parent, child) -> parent.children.add(child),
            task -> task.children,
            task -> task.children.clear()
    );

    private TaskTreeOperations() {}

    /**
     * Assembles a flat list of tasks into a tree based on {@link TaskRelation} parent-child links.
     *
     * @return root-level tasks with children populated recursively
     */
    public static List<Task> buildTree(List<Task> tasks) {
        return TASK_TREE_BUILDER.buildTree(tasks);
    }

    /**
     * Flattens a tree of tasks into a single list via depth-first traversal, clearing children
     * as it goes so the result is a flat collection suitable for bulk DB writes.
     *
     * @param roots root-level tasks (as returned by {@link #buildTree(List)})
     * @return all tasks in DFS order
     */
    public static List<Task> flatten(List<Task> roots) {
        return TASK_TREE_BUILDER.flatten(roots);
    }
}

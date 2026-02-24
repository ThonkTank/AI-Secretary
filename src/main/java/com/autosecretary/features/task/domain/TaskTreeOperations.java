package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.util.TreeBuilder;

import java.util.ArrayList;
import java.util.List;

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

    public static List<Task> buildTree(List<Task> tasks) {
        return TASK_TREE_BUILDER.buildTree(tasks);
    }

    public static List<Task> flatten(List<Task> roots) {
        return TASK_TREE_BUILDER.flatten(roots);
    }
}

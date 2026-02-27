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

public class TreeBuilder<T> {

    private final Function<T, String> getId;
    private final Function<T, List<String>> getParentIds;
    private final BiConsumer<T, T> addChild;
    private final Function<T, List<T>> getChildren;
    private final Consumer<T> resetChildren;

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

    public List<T> buildTree(List<T> items) {
        for (T item : items) {
            resetChildren.accept(item);
        }

        Map<String, T> map = new HashMap<>();
        List<T> roots = new ArrayList<>();

        for (T item : items) {
            map.put(getId.apply(item), item);
        }

        for (T item : items) {
            List<String> parentIds = getParentIds.apply(item);
            boolean added = false;
            for (String parentId : parentIds) {
                T parent = map.get(parentId);
                if (parent != null) {
                    addChild.accept(parent, item);
                    added = true;
                }
            }
            if (!added) {
                roots.add(item);
            }
        }

        return roots;
    }

    public List<T> flatten(List<T> roots) {
        Set<T> visited = new HashSet<>();
        List<T> result = new ArrayList<>();
        for (T root : roots) {
            flattenNode(root, result, visited);
        }
        return result;
    }

    private void flattenNode(T node, List<T> result, Set<T> visited) {
        if (!visited.add(node)) return;
        result.add(node);
        for (T child : getChildren.apply(node)) {
            flattenNode(child, result, visited);
        }
    }

    public void sortTree(List<T> roots, Comparator<T> comparator) {
        if (roots.isEmpty()) return;
        roots.sort(comparator);
        for (T node : roots) {
            sortTree(getChildren.apply(node), comparator);
        }
    }
}

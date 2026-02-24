package com.autosecretary.features.task.ui.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;

import com.autosecretary.features.task.application.model.TaskListItem;
import com.autosecretary.util.TreeBuilder;

public class ViewSlotList {
    private List<ViewSlot> viewSlots;
    public List<ViewSlot> displaySlots;

    private static final TreeBuilder<ViewSlot> TREE_BY_TASK = new TreeBuilder<>(
            vs -> vs.item.taskId,
            vs -> vs.item.parentTaskIds,
            (parent, child) -> parent.children.add(child),
            vs -> vs.children,
            vs -> vs.children = new ArrayList<>()
    );

    private static final TreeBuilder<ViewSlot> TREE_BY_SLOT = new TreeBuilder<>(
            vs -> vs.item.slotId,
            vs -> vs.item.slotParentId != null
                    ? Collections.singletonList(vs.item.slotParentId)
                    : Collections.emptyList(),
            (parent, child) -> parent.children.add(child),
            vs -> vs.children,
            vs -> vs.children = new ArrayList<>()
    );

    public static class ViewSlot {
        public final TaskListItem item;
        public int depth;

        private List<ViewSlot> children = new ArrayList<>();

        public ViewSlot(TaskListItem item) {
            this.item = item;
        }
    }

    public void fromList(List<TaskListItem> items) {
        viewSlots = new ArrayList<>();
        for (TaskListItem item : items) {
            viewSlots.add(new ViewSlot(item));
        }
    }

    public void filter(Predicate<ViewSlot> predicate) {
        displaySlots = new ArrayList<>();
        for (ViewSlot vs : viewSlots) {
            if (predicate.test(vs)) {
                displaySlots.add(vs);
            }
        }
    }

    public void sort(boolean byTaskRelation, Comparator<ViewSlot> comparator) {
        TreeBuilder<ViewSlot> builder = byTaskRelation ? TREE_BY_TASK : TREE_BY_SLOT;
        displaySlots = builder.buildTree(displaySlots);
        builder.sortTree(displaySlots, comparator);
        displaySlots = builder.flattenWithDepth(displaySlots, (vs, depth) -> vs.depth = depth);
    }
}

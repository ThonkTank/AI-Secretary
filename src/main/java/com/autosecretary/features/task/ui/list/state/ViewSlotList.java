package com.autosecretary.features.task.ui.list.state;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.util.TreeBuilder;

public class ViewSlotList {
    private List<ViewSlot> viewSlots = new ArrayList<>();
    private List<ViewSlot> displaySlots = new ArrayList<>();

    public List<ViewSlot> getDisplaySlots() {
        return displaySlots;
    }

    public void appendToDisplay(List<ViewSlot> extra) {
        displaySlots.addAll(extra);
    }

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

        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    public void fromList(List<TaskListItem> items) {
        List<ViewSlot> slots = new ArrayList<>();
        for (TaskListItem item : items) {
            slots.add(new ViewSlot(item));
        }
        viewSlots = slots;
        displaySlots = new ArrayList<>(slots);
    }

    public void filter(Predicate<ViewSlot> predicate) {
        displaySlots = new ArrayList<>();
        for (ViewSlot vs : viewSlots) {
            if (predicate.test(vs)) {
                displaySlots.add(vs);
            }
        }
    }

    public void sortByTask(Comparator<ViewSlot> comparator, Predicate<ViewSlot> isExpanded) {
        applySort(TREE_BY_TASK, comparator, isExpanded);
    }

    public void sortBySlot(Comparator<ViewSlot> comparator) {
        applySort(TREE_BY_SLOT, comparator, slot -> true);
    }

    private void applySort(TreeBuilder<ViewSlot> builder,
                           Comparator<ViewSlot> comparator,
                           Predicate<ViewSlot> isExpanded) {
        displaySlots = builder.buildTree(displaySlots);
        builder.sortTree(displaySlots, comparator);

        List<ViewSlot> flattened = new ArrayList<>();
        flattenWithDepth(displaySlots, 0, isExpanded, flattened);
        displaySlots = flattened;
    }

    private void flattenWithDepth(List<ViewSlot> source,
                                  int depth,
                                  Predicate<ViewSlot> isExpanded,
                                  List<ViewSlot> target) {
        for (ViewSlot slot : source) {
            slot.depth = depth;
            target.add(slot);
            if (slot.hasChildren() && isExpanded.test(slot)) {
                flattenWithDepth(slot.children, depth + 1, isExpanded, target);
            }
        }
    }
}

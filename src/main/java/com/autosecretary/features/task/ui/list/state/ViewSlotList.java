package com.autosecretary.features.task.ui.list.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.util.TreeBuilder;

public class ViewSlotList {
    // Source of truth: all task slots (never modified after fromList, used as reference for re-filtering)
    private List<ViewSlot> allSlots = new ArrayList<>();
    // Working set: filtered/sorted task slots + appended calendar events, used for display
    private List<ViewSlot> displaySlots = new ArrayList<>();

    public List<ViewSlot> getDisplaySlots() {
        return displaySlots;
    }

    public void appendToDisplay(List<ViewSlot> extra) {
        displaySlots.addAll(extra);
    }

    private static final Predicate<ViewSlot> ALWAYS_EXPANDED = slot -> true;

    private static final TreeBuilder<ViewSlot> TREE_BY_TASK = createTreeBuilder(
            vs -> vs.item.taskId,
            vs -> vs.item.parentTaskIds
    );

    private static final TreeBuilder<ViewSlot> TREE_BY_SLOT = createTreeBuilder(
            vs -> vs.item.slotId,
            ViewSlotList::getSlotParents
    );

    private static TreeBuilder<ViewSlot> createTreeBuilder(
            java.util.function.Function<ViewSlot, String> getId,
            java.util.function.Function<ViewSlot, List<String>> getParentIds) {
        return new TreeBuilder<>(
                getId,
                getParentIds,
                (parent, child) -> parent.getChildren().add(child),
                ViewSlot::getChildren,
                vs -> vs.setChildren(new ArrayList<>())
        );
    }

    private static List<String> getSlotParents(ViewSlot vs) {
        return vs.item.slotParentId != null
                ? Collections.singletonList(vs.item.slotParentId)
                : Collections.emptyList();
    }

    public void fromList(List<TaskListItem> items) {
        allSlots = items.stream().map(ViewSlot::new).collect(Collectors.toList());
        displaySlots = new ArrayList<>(allSlots);
    }

    public void filter(Predicate<ViewSlot> predicate) {
        displaySlots = allSlots.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Sorts displaySlots by task parent-child relationships and applies comparator within each group.
     * Call sequence: fromList() → filter() → [appendToDisplay()] → sortByTask()
     *
     * @param comparator how to order slots at each tree level
     * @param isExpanded determines which parent-child relationships are expanded in the flattened view
     */
    public void sortByTask(Comparator<ViewSlot> comparator, Predicate<ViewSlot> isExpanded) {
        applySort(TREE_BY_TASK, comparator, isExpanded);
    }

    /**
     * Sorts displaySlots by slot parent-child relationships (calendar hierarchy).
     * All slot parents are always expanded.
     * Call sequence: fromList() → filter() → [appendToDisplay()] → sortBySlot()
     *
     * @param comparator how to order slots within the slot hierarchy
     */
    public void sortBySlot(Comparator<ViewSlot> comparator) {
        applySort(TREE_BY_SLOT, comparator, ALWAYS_EXPANDED);
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
            slot.setDepth(depth);
            target.add(slot);
            if (slot.hasChildren() && isExpanded.test(slot)) {
                flattenWithDepth(slot.getChildren(), depth + 1, isExpanded, target);
            }
        }
    }
}

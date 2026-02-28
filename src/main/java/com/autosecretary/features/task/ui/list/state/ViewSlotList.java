package com.autosecretary.features.task.ui.list.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.util.TreeBuilder;

/**
 * Manages hierarchical task and calendar slot lists for display.
 * Uses util.TreeBuilder to construct and sort task or calendar hierarchies from flat lists.
 * See TreeBuilder.java for tree-building and sorting semantics.
 */
public class ViewSlotList {
    // Source of truth: all task slots (never modified after fromList, used as reference for re-filtering)
    private List<ViewSlot> allSlots = new ArrayList<>();
    // Working set: filtered/sorted task slots + appended calendar events, used for display
    private List<ViewSlot> displaySlots = new ArrayList<>();
    // Tracks whether appendToDisplay() has been called, preventing unsafe filter() calls
    private boolean hasAppendedItems = false;

    public List<ViewSlot> getDisplaySlots() {
        return displaySlots;
    }

    /**
     * Appends extra items (typically calendar events) to the current displaySlots.
     * Appended items are NOT re-sorted unless sortByTask() or sortBySlot() is called afterward.
     * Used to add calendar data after task filtering.
     *
     * WARNING: Must call sortByTask() or sortBySlot() immediately after to maintain correct state.
     * Calling filter() while appended items are pending will lose them.
     */
    public void appendToDisplay(List<ViewSlot> extra) {
        displaySlots.addAll(extra);
        hasAppendedItems = true;
    }

    // Predicate for tree flattening: all slots are "expanded" (children always included in flat list).
    // Used for sortBySlot() because calendar hierarchy is immutable — users cannot collapse calendar event groups.
    private static final Predicate<ViewSlot> ALWAYS_EXPANDED = slot -> true;

    // Two tree-building modes for different UI contexts:
    // - TREE_BY_TASK: Groups slots by task parent-child relationships (Manage mode).
    //   Respects the isExpanded predicate to show/hide task families.
    // - TREE_BY_SLOT: Groups slots by calendar event hierarchy (Checklist mode).
    //   All slot parents are always expanded (calendar hierarchy is immutable in display).
    // A single applySort() call uses one tree builder to rebuild the hierarchy for the current mode.
    private static final TreeBuilder<ViewSlot> TREE_BY_TASK = createTreeBuilder(
            vs -> vs.getItem().taskId,
            vs -> vs.getItem().parentTaskIds
    );

    private static final TreeBuilder<ViewSlot> TREE_BY_SLOT = createTreeBuilder(
            vs -> vs.getItem().slotId,
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
        return vs.getItem().slotParentId != null
                ? Collections.singletonList(vs.getItem().slotParentId)
                : Collections.emptyList();
    }

    public void fromList(List<TaskListItem> items) {
        allSlots = items.stream().map(ViewSlot::new).collect(Collectors.toList());
        displaySlots = new ArrayList<>(allSlots);
        hasAppendedItems = false;
    }

    /**
     * Rebuilds displaySlots by filtering allSlots (source of truth).
     * allSlots is never modified; re-filtering always starts from the original full list.
     *
     * @throws IllegalStateException if appendToDisplay() was called without a subsequent sort
     */
    public void filter(Predicate<ViewSlot> predicate) {
        if (hasAppendedItems) {
            throw new IllegalStateException("Cannot filter after appendToDisplay() without calling sort first");
        }
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
     * All slot parents are always expanded (calendar hierarchy is immutable in UI).
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

        hasAppendedItems = false;
    }

    /**
     * Recursively flattens the tree into displaySlots, assigning depth at each level.
     * Respects isExpanded predicate: if a slot is expanded, its children are added to the flat list
     * at depth+1; otherwise, children are skipped entirely.
     * Side effect: calls slot.setDepth(depth) on each visited slot for use in RecyclerView indentation.
     */
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

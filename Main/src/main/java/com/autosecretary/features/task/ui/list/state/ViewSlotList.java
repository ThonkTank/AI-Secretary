package com.autosecretary.features.task.ui.list.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
            vs -> vs.getItem().slotParentId != null
                    ? Collections.singletonList(vs.getItem().slotParentId)
                    : Collections.emptyList()
    );

    // Calendar hierarchy is immutable in UI; all slot parents are always expanded
    private static final Predicate<ViewSlot> ALL_EXPANDED = slot -> true;

    private static TreeBuilder<ViewSlot> createTreeBuilder(
            Function<ViewSlot, String> getId,
            Function<ViewSlot, List<String>> getParentIds) {
        return new TreeBuilder<>(
                getId,
                getParentIds,
                (parent, child) -> parent.getChildren().add(child),
                ViewSlot::getChildren,
                vs -> vs.setChildren(new ArrayList<>())
        );
    }

    public void fromList(List<TaskListItem> items) {
        if (hasAppendedItems) {
            throw new IllegalStateException("Cannot call fromList() after appendToDisplay() without calling sort first");
        }
        allSlots = items.stream().map(ViewSlot::new).collect(Collectors.toCollection(ArrayList::new));
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
        displaySlots = allSlots.stream().filter(predicate).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Sorts displaySlots by task parent-child relationships and applies comparator within each group.
     * Call sequence: fromList() → filter() → [appendToDisplay()] → sortByTask()
     *
     * @param comparator how to order slots at each tree level
     * @param isExpanded determines which parent-child relationships are expanded in the flattened view
     */
    public void sortByTask(Comparator<ViewSlot> comparator, Predicate<ViewSlot> isExpanded) {
        collapseToSingleRowPerTask();
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
        applySort(TREE_BY_SLOT, comparator, ALL_EXPANDED);
    }

    private void applySort(TreeBuilder<ViewSlot> builder,
                           Comparator<ViewSlot> comparator,
                           Predicate<ViewSlot> isExpanded) {
        displaySlots = builder.buildTree(displaySlots);
        builder.sortTree(displaySlots, comparator);

        List<ViewSlot> flattened = new ArrayList<>();
        flattenAndAssignDepths(displaySlots, 0, isExpanded, flattened);
        displaySlots = flattened;

        hasAppendedItems = false;
    }

    /**
     * Manage mode is task-centric; duplicate rows for the same task ID create unstable trees
     * because task hierarchy links are also task-ID based. Keep one representative row per task.
     */
    private void collapseToSingleRowPerTask() {
        Map<String, ViewSlot> bestByTaskId = new LinkedHashMap<>();
        for (ViewSlot slot : displaySlots) {
            String taskId = slot.getItem().taskId;
            if (taskId == null) {
                continue;
            }
            ViewSlot current = bestByTaskId.get(taskId);
            if (current == null || isBetterManageRepresentative(slot, current)) {
                bestByTaskId.put(taskId, slot);
            }
        }
        displaySlots = new ArrayList<>(bestByTaskId.values());
    }

    private static boolean isBetterManageRepresentative(ViewSlot candidate, ViewSlot current) {
        int candidateRank = manageRowRank(candidate);
        int currentRank = manageRowRank(current);
        if (candidateRank != currentRank) {
            return candidateRank > currentRank;
        }
        return compareNullableTimes(candidate.getItem().start, current.getItem().start) < 0;
    }

    private static int manageRowRank(ViewSlot slot) {
        if (slot.getItem().inProgress) return 3;
        if (slot.getItem().completed) return 2;
        if (slot.getItem().slotId != null) return 1;
        return 0;
    }

    private static int compareNullableTimes(java.time.LocalTime a, java.time.LocalTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    /**
     * Recursively flattens the tree structure into a flat list and assigns depth values.
     * Respects isExpanded predicate: if a slot is expanded, its children are added to the flat list;
     * otherwise, children are skipped entirely.
     * As each slot is added to the flat list, its depth is assigned for RecyclerView indentation.
     */
    private void flattenAndAssignDepths(List<ViewSlot> source,
                                        int depth,
                                        Predicate<ViewSlot> isExpanded,
                                        List<ViewSlot> target) {
        for (ViewSlot slot : source) {
            slot.setDepth(depth);
            target.add(slot);
            if (!slot.getChildren().isEmpty() && isExpanded.test(slot)) {
                flattenAndAssignDepths(slot.getChildren(), depth + 1, isExpanded, target);
            }
        }
    }
}

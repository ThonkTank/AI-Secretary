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
    // Working set: filtered/sorted task slots plus optional calendar events, used for display
    private List<ViewSlot> displaySlots = new ArrayList<>();

    public List<ViewSlot> getDisplaySlots() {
        return displaySlots;
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
        allSlots = items.stream().map(ViewSlot::new).collect(Collectors.toCollection(ArrayList::new));
        displaySlots = new ArrayList<>(allSlots);
    }

    /**
     * Rebuilds the display list from source slots in one explicit pass.
     */
    public void rebuildDisplay(Predicate<ViewSlot> predicate,
                               List<ViewSlot> extraItems,
                               Comparator<ViewSlot> comparator,
                               Predicate<ViewSlot> isExpanded,
                               boolean groupByTaskParent) {
        List<ViewSlot> workingSlots = allSlots.stream()
                .filter(predicate)
                .collect(Collectors.toCollection(ArrayList::new));
        if (extraItems != null && !extraItems.isEmpty()) {
            workingSlots.addAll(extraItems);
        }
        if (groupByTaskParent) {
            workingSlots = collapseToSingleRowPerTask(workingSlots);
        }
        TreeBuilder<ViewSlot> builder = groupByTaskParent ? TREE_BY_TASK : TREE_BY_SLOT;
        Predicate<ViewSlot> expansionPredicate = groupByTaskParent ? isExpanded : ALL_EXPANDED;
        List<ViewSlot> tree = builder.buildTree(workingSlots);
        builder.sortTree(tree, comparator);
        List<ViewSlot> flattened = new ArrayList<>();
        flattenAndAssignDepths(tree, 0, expansionPredicate, flattened);
        displaySlots = flattened;
    }

    /**
     * Manage mode is task-centric; duplicate rows for the same task ID create unstable trees
     * because task hierarchy links are also task-ID based. Keep one representative row per task.
     */
    private List<ViewSlot> collapseToSingleRowPerTask(List<ViewSlot> slots) {
        Map<String, ViewSlot> bestByTaskId = new LinkedHashMap<>();
        for (ViewSlot slot : slots) {
            String taskId = slot.getItem().taskId;
            if (taskId == null) {
                continue;
            }
            ViewSlot current = bestByTaskId.get(taskId);
            if (current == null || isBetterManageRepresentative(slot, current)) {
                bestByTaskId.put(taskId, slot);
            }
        }
        return new ArrayList<>(bestByTaskId.values());
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

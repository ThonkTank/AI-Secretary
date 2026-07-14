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
    // - TREE_BY_CATEGORY: Groups task rows under a synthetic category-header row (Manage mode).
    //   Respects the isExpanded predicate to collapse/expand a whole category.
    // - TREE_BY_SLOT: Groups slots by calendar event hierarchy (Checklist mode).
    //   All slot parents are always expanded (calendar hierarchy is immutable in display).
    private static final TreeBuilder<ViewSlot> TREE_BY_CATEGORY = createTreeBuilder(
            vs -> vs.getItem().isCategoryHeader() ? vs.getItem().categoryId : vs.getItem().taskId,
            vs -> vs.getItem().isCategoryHeader()
                    ? Collections.emptyList()
                    : Collections.singletonList(groupId(vs.getItem()))
    );

    private static final TreeBuilder<ViewSlot> TREE_BY_SLOT = createTreeBuilder(
            vs -> vs.getItem().slotId,
            vs -> vs.getItem().slotParentId != null
                    ? Collections.singletonList(vs.getItem().slotParentId)
                    : Collections.emptyList()
    );

    /**
     * Effective category-group id for a task/calendar row: its own category when the category
     * is known, otherwise the synthetic "uncategorised" bucket. Keeps header injection and
     * tree parent lookup consistent so no row is ever orphaned.
     */
    private static String groupId(TaskListItem item) {
        return item.categoryId != null && item.categoryName != null
                ? item.categoryId
                : TaskListItem.UNCATEGORIZED_ID;
    }

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
     *
     * <p>In Manage mode ({@code groupByCategory=true}) rows are collapsed to one per task and
     * grouped under synthetic category-header rows (which can be collapsed via {@code isExpanded}).
     * In Checklist mode rows are grouped by the (calendar) slot hierarchy instead.
     */
    public void rebuildDisplay(Predicate<ViewSlot> predicate,
                               List<ViewSlot> extraItems,
                               Comparator<ViewSlot> comparator,
                               Predicate<ViewSlot> isExpanded,
                               boolean groupByCategory) {
        List<ViewSlot> workingSlots = allSlots.stream()
                .filter(predicate)
                .collect(Collectors.toCollection(ArrayList::new));
        if (extraItems != null && !extraItems.isEmpty()) {
            workingSlots.addAll(extraItems);
        }
        TreeBuilder<ViewSlot> builder;
        Predicate<ViewSlot> expansionPredicate;
        if (groupByCategory) {
            workingSlots = collapseToSingleRowPerTask(workingSlots);
            workingSlots.addAll(buildCategoryHeaders(workingSlots));
            builder = TREE_BY_CATEGORY;
            expansionPredicate = isExpanded;
        } else {
            builder = TREE_BY_SLOT;
            expansionPredicate = ALL_EXPANDED;
        }
        List<ViewSlot> tree = builder.buildTree(workingSlots);
        builder.sortTree(tree, comparator);
        List<ViewSlot> flattened = new ArrayList<>();
        flattenAndAssignDepths(tree, 0, expansionPredicate, flattened);
        displaySlots = flattened;
    }

    /**
     * Creates one synthetic {@link TaskListItem.ItemType#CATEGORY_HEADER} row per distinct
     * category present among the given task rows, reusing category metadata carried on the rows.
     */
    private List<ViewSlot> buildCategoryHeaders(List<ViewSlot> taskSlots) {
        Map<String, ViewSlot> headerByGroupId = new LinkedHashMap<>();
        for (ViewSlot slot : taskSlots) {
            TaskListItem item = slot.getItem();
            String group = groupId(item);
            if (headerByGroupId.containsKey(group)) {
                continue;
            }
            TaskListItem header = TaskListItem.UNCATEGORIZED_ID.equals(group)
                    ? TaskListItem.categoryHeader(TaskListItem.UNCATEGORIZED_ID, "Ohne Kategorie", null, null)
                    : TaskListItem.categoryHeader(group, item.categoryName, item.categoryIcon, item.categoryColorHex);
            headerByGroupId.put(group, new ViewSlot(header));
        }
        return new ArrayList<>(headerByGroupId.values());
    }

    /**
     * Manage mode shows one row per task; a task may have several slots for the selected day.
     * Keep one representative row per task (the most advanced/earliest) for a stable tree.
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

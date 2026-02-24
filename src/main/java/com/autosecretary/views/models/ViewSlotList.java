package com.autosecretary.views.models;

import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Predicate;

import com.autosecretary.database.task.*;
import com.autosecretary.util.TreeBuilder;

public class ViewSlotList {
    private List<ViewSlot> viewSlots;
    public List<ViewSlot> displaySlots;

    private static final TreeBuilder<ViewSlot> TREE_BY_TASK = new TreeBuilder<>(
            vs -> vs.task.core.id,
            vs -> {
                List<String> ids = new ArrayList<>();
                for (TaskRelation rel : vs.task.parents) ids.add(rel.parent);
                return ids;
            },
            (parent, child) -> parent.children.add(child),
            vs -> vs.children,
            vs -> vs.children = new ArrayList<>()
    );

    private static final TreeBuilder<ViewSlot> TREE_BY_SLOT = new TreeBuilder<>(
            vs -> vs.slot.id,
            vs -> vs.slot.parent != null
                    ? Collections.singletonList(vs.slot.parent)
                    : Collections.emptyList(),
            (parent, child) -> parent.children.add(child),
            vs -> vs.children,
            vs -> vs.children = new ArrayList<>()
    );

    public static class ViewSlot {
        public enum DeadlineUrgency {
            NONE,
            OVERDUE,
            TODAY,
            SOON,
            FUTURE
        }

        public Task task;
        public TaskSlot slot;
        public int depth;

        private List<ViewSlot> children = new ArrayList<>();

        public ViewSlot (Task task, TaskSlot slot) {
            this.task = task;
            this.slot = slot;
        }

        public long daysUntilDeadline() {
            if (task.core.deadline == null) {
                return Long.MAX_VALUE;
            }
            return ChronoUnit.DAYS.between(LocalDate.now(), task.core.deadline);
        }

        public DeadlineUrgency deadlineUrgency() {
            if (task.core.deadline == null) {
                return DeadlineUrgency.NONE;
            }

            long daysUntil = daysUntilDeadline();
            if (daysUntil < 0) return DeadlineUrgency.OVERDUE;
            if (daysUntil == 0) return DeadlineUrgency.TODAY;
            if (daysUntil <= 3) return DeadlineUrgency.SOON;
            return DeadlineUrgency.FUTURE;
        }
    }

    public void fromList(List<Task> tasks) {
        viewSlots = new ArrayList<>();
        for (Task task : tasks) {
            int nrSlots = 0;
            for (TaskSlot slot : task.slots) {
                viewSlots.add(new ViewSlot(task, slot));
                nrSlots++;
            }
            if (nrSlots == 0) {
                ViewSlot vs = new ViewSlot(task, new TaskSlot());
                vs.slot.day = LocalDate.now();
                viewSlots.add(vs);
            }
        }
    }

    //ViewModel ruft filter auf
    public void filter(Predicate<ViewSlot> predicate) {
        displaySlots = new ArrayList<>();
        for (ViewSlot vs : viewSlots) {
            if (predicate.test(vs)) {
                displaySlots.add(vs);
            }
        }
    }

    //ViewModel ruft sort auf
    public void sort(boolean byTaskRelation, Comparator<ViewSlot> comparator) {
        TreeBuilder<ViewSlot> builder = byTaskRelation ? TREE_BY_TASK : TREE_BY_SLOT;
        displaySlots = builder.buildTree(displaySlots);
        builder.sortTree(displaySlots, comparator);
        displaySlots = builder.flattenWithDepth(displaySlots, (vs, depth) -> vs.depth = depth);
    }
}

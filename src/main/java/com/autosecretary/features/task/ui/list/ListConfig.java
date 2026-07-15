package com.autosecretary.features.task.ui.list;

import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.application.listmodel.TaskListItemComparators;
import com.autosecretary.features.task.ui.list.state.ViewSlot;
import com.autosecretary.features.task.ui.list.state.ViewSlotList;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Defines the list display modes for the task list screen.
 * <p>
 * {@link #CHECKLIST} shows only slots scheduled for the selected day, sorted by time.
 * {@link #MANAGE} shows all tasks for the selected day grouped by category, sorted by title.
 * {@link #URGENCY} shows all open tasks (day-independent) as a flat list, sorted by priority,
 * then nearest deadline, then title.
 * {@link #DEADLINE} shows all open one-off tasks with a deadline (day-independent) as a flat
 * list sorted by nearest deadline, each with a visual remaining-time bar.
 */
enum ListConfig {
    CHECKLIST(ViewSlotList.Grouping.BY_SLOT) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            return slot.getItem().isScheduledOn(day);
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator.comparing(
                    (ViewSlot slot) -> slot.getItem().start,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
    },
    MANAGE(ViewSlotList.Grouping.BY_CATEGORY) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            // day == null is the "show all" path (no day filter applied, e.g. during initial load
            // before selectedDay is set). In practice selectedDay is always non-null at runtime,
            // so this guards against an edge case rather than a real operating mode.
            return day == null || slot.getItem().day.equals(day);
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator
                    .<ViewSlot, Boolean>comparing(slot -> slot.getItem().isCalendarEvent())
                    .thenComparing(slot -> slot.getItem().title, Comparator.nullsLast(Comparator.naturalOrder()));
        }
    },
    URGENCY(ViewSlotList.Grouping.FLAT_TASKS) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            TaskListItem item = slot.getItem();
            return item.itemType == TaskListItem.ItemType.TASK && !item.completed;
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator.comparing(ViewSlot::getItem, TaskListItemComparators.BY_PRIORITY);
        }
    },
    DEADLINE(ViewSlotList.Grouping.FLAT_TASKS) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            TaskListItem item = slot.getItem();
            return item.itemType == TaskListItem.ItemType.TASK
                    && !item.completed
                    && !item.repeating
                    && item.deadline != null;
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator.comparing(ViewSlot::getItem, TaskListItemComparators.BY_DEADLINE);
        }
    };

    /** How {@link ViewSlotList#rebuildDisplay} structures the rows in this mode. */
    private final ViewSlotList.Grouping grouping;

    ListConfig(ViewSlotList.Grouping grouping) {
        this.grouping = grouping;
    }

    abstract boolean matches(ViewSlot slot, LocalDate day);

    abstract Comparator<ViewSlot> comparator();

    ViewSlotList.Grouping grouping() {
        return grouping;
    }

    /** Calendar events accompany the day-scoped modes only; the flat modes list tasks alone. */
    boolean showsCalendarEvents() {
        return this == CHECKLIST || this == MANAGE;
    }

    /**
     * Whether this mode renders one selected day (day navigation, today-gating) — the flat
     * modes ignore the day cursor entirely. Kept separate from {@link #showsCalendarEvents()}:
     * the sets coincide today (calendar rows only exist where a day cursor exists), but the
     * semantics differ.
     */
    boolean isDayScoped() {
        return this == CHECKLIST || this == MANAGE;
    }
}

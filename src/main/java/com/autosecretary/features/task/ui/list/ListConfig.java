package com.autosecretary.features.task.ui.list;

import com.autosecretary.features.task.ui.list.state.ViewSlot;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Defines the two list display modes for the task list screen.
 * <p>
 * {@link #CHECKLIST} shows only slots scheduled for the selected day, sorted by time.
 * {@link #MANAGE} shows all slots for the selected day grouped by task parent, sorted by title.
 */
enum ListConfig {
    CHECKLIST(false) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            return slot.item.isScheduledOn(day);
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator.comparing(
                    (ViewSlot slot) -> slot.item.start,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        }
    },
    MANAGE(true) {
        @Override
        boolean matches(ViewSlot slot, LocalDate day) {
            // day == null is the "show all" path (no day filter applied, e.g. during initial load
            // before selectedDay is set). In practice selectedDay is always non-null at runtime,
            // so this guards against an edge case rather than a real operating mode.
            return day == null || slot.item.day.equals(day);
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator
                    .<ViewSlot, Boolean>comparing(slot -> slot.item.isCalendarEvent())
                    .thenComparing(slot -> slot.item.title, Comparator.nullsLast(Comparator.naturalOrder()));
        }
    };

    /**
     * When true, {@link TaskViewModel#filterList()} calls {@link state.ViewSlotList#sortByTask}
     * to group display slots into a parent-child task tree. When false, it calls
     * {@link state.ViewSlotList#sortBySlot} to group by slot hierarchy (e.g. calendar sub-events).
     */
    private final boolean groupByTaskParent;

    ListConfig(boolean groupByTaskParent) {
        this.groupByTaskParent = groupByTaskParent;
    }

    abstract boolean matches(ViewSlot slot, LocalDate day);

    abstract Comparator<ViewSlot> comparator();

    boolean groupByTaskParent() {
        return groupByTaskParent;
    }
}

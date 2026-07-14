package com.autosecretary.features.task.ui.list;

import com.autosecretary.features.task.ui.list.state.ViewSlot;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Defines the two list display modes for the task list screen.
 * <p>
 * {@link #CHECKLIST} shows only slots scheduled for the selected day, sorted by time.
 * {@link #MANAGE} shows all tasks for the selected day grouped by category, sorted by title.
 */
enum ListConfig {
    CHECKLIST(false) {
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
    MANAGE(true) {
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
    };

    /**
     * When true, {@link state.ViewSlotList#rebuildDisplay} groups task rows under synthetic
     * category-header rows (Manage mode). When false, it groups by the slot hierarchy
     * (e.g. calendar sub-events) for Checklist mode.
     */
    private final boolean groupByCategory;

    ListConfig(boolean groupByCategory) {
        this.groupByCategory = groupByCategory;
    }

    abstract boolean matches(ViewSlot slot, LocalDate day);

    abstract Comparator<ViewSlot> comparator();

    boolean groupByCategory() {
        return groupByCategory;
    }
}

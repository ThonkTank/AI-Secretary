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
            return day == null || slot.item.day.equals(day);
        }

        @Override
        Comparator<ViewSlot> comparator() {
            return Comparator
                    .<ViewSlot, Boolean>comparing(slot -> slot.item.isCalendarEvent())
                    .thenComparing(slot -> slot.item.title, Comparator.nullsLast(Comparator.naturalOrder()));
        }
    };

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

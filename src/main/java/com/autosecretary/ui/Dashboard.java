package com.autosecretary.ui;

import java.util.List;

/** Fully prepared immutable rendering model; adapters perform no domain decisions. */
public record Dashboard(
        List<FocusRow> focus,
        List<WorkItemRow> workItems,
        List<CalendarRow> calendar) {
    public Dashboard {
        focus = List.copyOf(focus);
        workItems = List.copyOf(workItems);
        calendar = List.copyOf(calendar);
    }
}

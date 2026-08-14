package com.autosecretary.ui;

import java.util.List;

/** Fully prepared immutable rendering model; adapters perform no domain decisions. */
public record Dashboard(
        List<TodayRow> today,
        List<WorkItemRow> workItems) {
    public Dashboard {
        today = List.copyOf(today);
        workItems = List.copyOf(workItems);
    }
}

package com.autosecretary.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Shared rendering input for the Activity and RemoteViews widget. */
public record TodayViewData(
        LocalDate day,
        Instant generatedAt,
        Instant nextRefreshAt,
        List<TodayRow> rows,
        boolean undoAvailable,
        boolean calendarPermissionMissing) {
    public TodayViewData {
        rows = List.copyOf(rows);
    }
}

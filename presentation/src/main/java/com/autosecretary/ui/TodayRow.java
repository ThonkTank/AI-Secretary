package com.autosecretary.ui;

import java.time.LocalDateTime;

/** One heterogeneous row in the shared Today timeline. */
public sealed interface TodayRow permits TodayRow.Focus, TodayRow.Calendar {
    LocalDateTime start();
    String stableId();

    record Focus(FocusRow value) implements TodayRow {
        @Override public LocalDateTime start() { return value.suggestedStart(); }
        @Override public String stableId() { return "focus:" + value.id(); }
    }

    record Calendar(CalendarRow value) implements TodayRow {
        @Override public LocalDateTime start() { return value.start(); }
        @Override public String stableId() { return "calendar:" + value.stableId(); }
    }
}

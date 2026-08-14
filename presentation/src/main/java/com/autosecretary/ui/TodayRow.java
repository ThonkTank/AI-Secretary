package com.autosecretary.ui;

import java.time.LocalDateTime;

/** One heterogeneous row in the shared Today timeline. */
public sealed interface TodayRow permits TodayRow.Focus, TodayRow.Calendar {
    LocalDateTime start();

    record Focus(FocusRow value) implements TodayRow {
        @Override public LocalDateTime start() { return value.suggestedStart(); }
    }

    record Calendar(CalendarRow value) implements TodayRow {
        @Override public LocalDateTime start() { return value.start(); }
    }
}

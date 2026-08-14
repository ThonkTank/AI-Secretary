package com.autosecretary.ui;

import java.time.LocalDateTime;

public record CalendarRow(
        String stableId,
        LocalDateTime start,
        LocalDateTime end,
        String title,
        boolean titleHidden) {
    public CalendarRow(LocalDateTime start, LocalDateTime end, String title) {
        this("calendar:" + start + ":" + end + ":" + title,
                start, end, title, false);
    }
}

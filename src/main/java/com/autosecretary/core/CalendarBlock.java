package com.autosecretary.core;

import java.time.LocalDateTime;

/** Read-only busy interval imported from the device calendar, including its title. */
public record CalendarBlock(LocalDateTime start, LocalDateTime end, String title) {
    public CalendarBlock(LocalDateTime start, LocalDateTime end) {
        this(start, end, "Kalendertermin");
    }

    public CalendarBlock {
        title = title == null || title.trim().isEmpty() ? "Kalendertermin" : title.trim();
    }
}

package com.autosecretary.core;

import java.time.LocalDateTime;

/** Read-only busy interval imported from the device calendar. */
public record CalendarBlock(LocalDateTime start, LocalDateTime end) {
}

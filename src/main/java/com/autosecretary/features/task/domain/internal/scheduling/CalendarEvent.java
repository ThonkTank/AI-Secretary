package com.autosecretary.features.task.domain.internal.scheduling;

import java.time.LocalTime;

/** Calendar event with title and bounded time range. */
public record CalendarEvent(String title, LocalTime start, LocalTime end) {
}

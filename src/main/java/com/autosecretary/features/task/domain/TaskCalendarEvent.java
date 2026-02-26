package com.autosecretary.features.task.domain;

import java.time.LocalTime;

/** Domain-level calendar event DTO used by task scheduling and UI flows. */
public record TaskCalendarEvent(String title, LocalTime start, LocalTime end) {
}

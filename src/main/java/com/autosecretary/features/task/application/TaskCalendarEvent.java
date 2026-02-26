package com.autosecretary.features.task.application;

import java.time.LocalTime;

/** App-level calendar event DTO used by task UI and scheduling flows. */
public record TaskCalendarEvent(String title, LocalTime start, LocalTime end) {
}

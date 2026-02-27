package com.autosecretary.features.task.application.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

/** Represents a day with a time window for schedule-based queries. */
public record ScheduleWindow(LocalDate day, LocalTime startTime, LocalTime endTime) {
}

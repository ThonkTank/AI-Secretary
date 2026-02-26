package com.autosecretary.features.task.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Reads bounded calendar events for a specific day. */
public interface TaskCalendarService {
    List<TaskCalendarEvent> getEventsForDay(LocalDate day,
                                            LocalTime scheduleStart,
                                            LocalTime scheduleEnd);
}

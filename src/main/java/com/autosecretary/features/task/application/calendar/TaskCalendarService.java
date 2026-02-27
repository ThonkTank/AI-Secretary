package com.autosecretary.features.task.application.calendar;

import com.autosecretary.features.task.domain.TaskCalendarEvent;

import java.util.List;

/** Reads bounded calendar events for a specific day within a time window. */
public interface TaskCalendarService {
    List<TaskCalendarEvent> getEventsForDay(ScheduleWindow window);
}

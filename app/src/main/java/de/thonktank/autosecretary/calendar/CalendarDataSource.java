package de.thonktank.autosecretary.calendar;

import de.thonktank.autosecretary.CalendarEventSnapshot;

import java.util.List;

public interface CalendarDataSource {
    List<CalendarEventSnapshot> today();
}

package com.autosecretary.application;

public interface CalendarPort {
    CalendarReadResult read(TimeRange range);
}

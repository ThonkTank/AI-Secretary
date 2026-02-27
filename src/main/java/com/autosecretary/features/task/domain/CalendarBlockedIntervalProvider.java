package com.autosecretary.features.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public interface CalendarBlockedIntervalProvider {

    List<BlockedInterval> readBlockedIntervals(LocalDate day, LocalDateTime windowStart, LocalDateTime windowEnd);

    record BlockedInterval(LocalDateTime start, LocalDateTime end) {
    }

    CalendarBlockedIntervalProvider NONE = (day, windowStart, windowEnd) -> Collections.emptyList();
}

package com.autosecretary.features.task.domain.internal.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public interface CalendarBlockedIntervalProvider {

    List<BlockedInterval> readBlockedIntervals(LocalDate day, LocalDateTime windowStart, LocalDateTime windowEnd);

    class BlockedInterval {
        public final LocalDateTime start;
        public final LocalDateTime end;

        public BlockedInterval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    CalendarBlockedIntervalProvider NONE = (day, windowStart, windowEnd) -> Collections.emptyList();
}

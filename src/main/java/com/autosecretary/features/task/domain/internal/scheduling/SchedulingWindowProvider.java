package com.autosecretary.features.task.domain.internal.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface SchedulingWindowProvider {
    SchedulingWindow forDay(LocalDate day);

    class SchedulingWindow {
        public final LocalDateTime start;
        public final LocalDateTime end;

        public SchedulingWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}

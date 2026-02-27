package com.autosecretary.features.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface SchedulingWindowProvider {
    SchedulingWindow forDay(LocalDate day);

    record SchedulingWindow(LocalDateTime start, LocalDateTime end) {
    }
}

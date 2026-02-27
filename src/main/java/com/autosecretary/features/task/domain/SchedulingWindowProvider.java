package com.autosecretary.features.task.domain;

import com.autosecretary.features.task.data.TaskPrefSlotFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface SchedulingWindowProvider {
    SchedulingWindow forDay(LocalDate day);

    record SchedulingWindow(LocalDateTime start, LocalDateTime end) {
    }

    SchedulingWindowProvider DEFAULT = day -> {
        LocalDateTime start = LocalDateTime.of(day, TaskPrefSlotFactory.DEFAULT_START_TIME);
        LocalDateTime end = LocalDateTime.of(day, TaskPrefSlotFactory.DEFAULT_END_TIME);
        return new SchedulingWindow(start, end);
    };
}

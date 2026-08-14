package com.autosecretary.domain;

import java.time.LocalTime;

/** Inclusive start/exclusive end local planning window. */
public record TimeWindow(LocalTime start, LocalTime end) {
    public TimeWindow {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Ungültiges Zeitfenster");
        }
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}

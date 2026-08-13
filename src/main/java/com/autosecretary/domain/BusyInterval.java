package com.autosecretary.domain;

import java.time.LocalDateTime;

public record BusyInterval(LocalDateTime start, LocalDateTime end, String title) {
    public BusyInterval {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Ungültiger Kalenderblock");
        }
        title = title == null ? "Termin" : title;
    }
}

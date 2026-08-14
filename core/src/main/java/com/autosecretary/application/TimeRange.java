package com.autosecretary.application;

import java.time.Instant;

public record TimeRange(Instant startInclusive, Instant endExclusive) {
    public TimeRange {
        if (startInclusive == null || endExclusive == null
                || !endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("Ungültiger Zeitraum");
        }
    }
}

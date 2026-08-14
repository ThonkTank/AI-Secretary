package com.autosecretary.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** The sole application clock and timezone source. */
@FunctionalInterface
public interface TimeProvider {
    LocalDateTime localNow();
    default ZoneId zone() { return ZoneOffset.UTC; }
    default Instant instant() { return localNow().atZone(zone()).toInstant(); }
}

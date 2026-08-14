package com.autosecretary.application;

import java.time.Instant;
import java.time.ZoneId;

/** The sole application clock and timezone source. */
public interface TimeProvider {
    Instant now();
    ZoneId zone();
}

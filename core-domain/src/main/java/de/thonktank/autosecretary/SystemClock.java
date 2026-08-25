package de.thonktank.autosecretary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class SystemClock implements Clock {
    private final ZoneIdProvider zones;

    public SystemClock() {
        this(new SystemZoneIdProvider());
    }

    public SystemClock(ZoneIdProvider zones) {
        this.zones = zones;
    }

    @Override public LocalDate today() {
        return LocalDate.now(zones.zoneId());
    }

    @Override public LocalTime time() {
        return LocalTime.now(zones.zoneId());
    }

    @Override public LocalDateTime now() {
        return LocalDateTime.now(zones.zoneId());
    }
}

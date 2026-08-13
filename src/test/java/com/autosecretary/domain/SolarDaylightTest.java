package com.autosecretary.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class SolarDaylightTest {
    @Test
    public void currentLocationProducesPlausibleGermanSummerDaylight() {
        SolarDaylight.Window window = SolarDaylight.forDate(
                LocalDate.parse("2026-08-11"), 51.2, 6.69, ZoneId.of("Europe/Berlin"));

        assertTrue(window.sunrise().isBefore(LocalTime.of(7, 0)));
        assertTrue(window.sunset().isAfter(LocalTime.of(20, 0)));
        assertTrue(window.isLight(LocalTime.NOON));
        assertFalse(window.isLight(LocalTime.of(23, 30)));
    }
}

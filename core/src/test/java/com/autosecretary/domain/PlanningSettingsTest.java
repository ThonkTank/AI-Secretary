package com.autosecretary.domain;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.time.LocalTime;

public final class PlanningSettingsTest {
    @Test
    public void preferenceWindowsMustStayInsidePlanningDay() {
        assertThrows(IllegalArgumentException.class, () -> new PlanningSettings(
                new TimeWindow(LocalTime.of(7, 0), LocalTime.of(22, 0)),
                new TimeWindow(LocalTime.of(6, 0), LocalTime.of(11, 0)),
                new TimeWindow(LocalTime.of(11, 0), LocalTime.of(15, 0)),
                new TimeWindow(LocalTime.of(17, 0), LocalTime.of(22, 0)),
                15, 15, 15, 7));
    }
}

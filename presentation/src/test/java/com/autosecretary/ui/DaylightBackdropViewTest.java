package com.autosecretary.ui;

import static org.junit.Assert.assertEquals;

import com.autosecretary.domain.SolarDaylight;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class DaylightBackdropViewTest {
    private static final LocalDate DATE = LocalDate.parse("2026-08-13");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final double LATITUDE = 51.20;
    private static final double LONGITUDE = 6.69;

    @Test
    public void sunriseUsesTheExactHandoffMarkAtTheLocationSunrise() {
        SolarDaylight.Window window = SolarDaylight.forDate(
                DATE, LATITUDE, LONGITUDE, ZONE);

        DaylightBackdropView.Scene scene = DaylightBackdropView.sceneAt(
                DATE, window.sunrise(), LATITUDE, LONGITUDE, ZONE);

        assertEquals(96f, scene.x(), 0.01f);
        assertEquals(88f, scene.width(), 0.01f);
        assertEquals(42f, scene.height(), 0.01f);
        assertEquals(0x80F4B258, scene.firstColor());
        assertEquals(0x3DDC783A, scene.secondColor());
    }

    @Test
    public void solarMiddayUsesTheExactNoonHandoffMark() {
        SolarDaylight.Window window = SolarDaylight.forDate(
                DATE, LATITUDE, LONGITUDE, ZONE);
        int sunrise = window.sunrise().getHour() * 60 + window.sunrise().getMinute();
        int sunset = window.sunset().getHour() * 60 + window.sunset().getMinute();
        LocalTime solarMiddayMark = LocalTime.ofSecondOfDay(
                (sunrise + Math.round((sunset - sunrise) * 0.503f)) * 60L);

        DaylightBackdropView.Scene scene = DaylightBackdropView.sceneAt(
                DATE, solarMiddayMark, LATITUDE, LONGITUDE, ZONE);

        assertEquals(50f, scene.x(), 0.01f);
        assertEquals(150f, scene.width(), 0.01f);
        assertEquals(58f, scene.height(), 0.01f);
        assertEquals(0xCCFFFCEE, scene.firstColor());
        assertEquals(0x4DF6F2DE, scene.secondColor());
    }

    @Test
    public void valuesInterpolateBetweenSunriseAndMorning() {
        SolarDaylight.Window window = SolarDaylight.forDate(
                DATE, LATITUDE, LONGITUDE, ZONE);
        int sunrise = window.sunrise().getHour() * 60 + window.sunrise().getMinute();
        int sunset = window.sunset().getHour() * 60 + window.sunset().getMinute();
        int morning = sunrise + Math.round((sunset - sunrise) * 0.242f);
        LocalTime halfway = LocalTime.ofSecondOfDay(((sunrise + morning) / 2L) * 60L);

        DaylightBackdropView.Scene scene = DaylightBackdropView.sceneAt(
                DATE, halfway, LATITUDE, LONGITUDE, ZONE);

        assertEquals(81f, scene.x(), 0.4f);
        assertEquals(109f, scene.width(), 0.5f);
        assertEquals(47f, scene.height(), 0.3f);
    }
}

package com.autosecretary.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Small NOAA-style sunrise/sunset calculation; approximate location is more than sufficient. */
public final class SolarDaylight {
    public record Window(LocalTime sunrise, LocalTime sunset) {
        public boolean isLight(LocalTime time) {
            return !time.isBefore(sunrise.plusMinutes(20)) && time.isBefore(sunset.minusMinutes(35));
        }
    }

    private SolarDaylight() {
    }

    public static Window forDate(LocalDate date, double latitude, double longitude, ZoneId zone) {
        return new Window(
                event(date, latitude, longitude, zone, true),
                event(date, latitude, longitude, zone, false));
    }

    private static LocalTime event(
            LocalDate date,
            double latitude,
            double longitude,
            ZoneId zone,
            boolean sunrise) {
        double longitudeHour = longitude / 15.0;
        double approximate = date.getDayOfYear()
                + ((sunrise ? 6.0 : 18.0) - longitudeHour) / 24.0;
        double meanAnomaly = 0.9856 * approximate - 3.289;
        double trueLongitude = normalize(meanAnomaly
                + 1.916 * sin(meanAnomaly)
                + 0.020 * sin(2 * meanAnomaly)
                + 282.634);
        double rightAscension = normalize(Math.toDegrees(Math.atan(0.91764
                * Math.tan(Math.toRadians(trueLongitude)))));
        rightAscension += Math.floor(trueLongitude / 90.0) * 90.0
                - Math.floor(rightAscension / 90.0) * 90.0;
        rightAscension /= 15.0;

        double sinDeclination = 0.39782 * sin(trueLongitude);
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        double cosHour = (Math.cos(Math.toRadians(90.833))
                - sinDeclination * Math.sin(Math.toRadians(latitude)))
                / (cosDeclination * Math.cos(Math.toRadians(latitude)));
        if (cosHour > 1 || cosHour < -1) {
            return sunrise ? LocalTime.of(7, 30) : LocalTime.of(18, 30);
        }
        double hour = sunrise
                ? 360.0 - Math.toDegrees(Math.acos(cosHour))
                : Math.toDegrees(Math.acos(cosHour));
        hour /= 15.0;
        double localMean = hour + rightAscension - 0.06571 * approximate - 6.622;
        double utcHour = normalizeHour(localMean - longitudeHour);
        long utcSeconds = Math.round(utcHour * 3600.0);
        Instant instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant().plusSeconds(utcSeconds);
        return ZonedDateTime.ofInstant(instant, zone).toLocalTime().withSecond(0).withNano(0);
    }

    private static double sin(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double normalize(double degrees) {
        double value = degrees % 360.0;
        return value < 0 ? value + 360.0 : value;
    }

    private static double normalizeHour(double hour) {
        double value = hour % 24.0;
        return value < 0 ? value + 24.0 : value;
    }
}

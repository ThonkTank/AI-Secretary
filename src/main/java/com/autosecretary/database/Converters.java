package com.autosecretary.database;

import androidx.room.TypeConverter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.DayOfWeek;
import com.autosecretary.constants.Priority;
import com.autosecretary.constants.Period;

public class Converters {
    @TypeConverter
    public static String fromLocalDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    @TypeConverter
    public static LocalDate toLocalDate(String value) {
        return value != null ? LocalDate.parse(value) : null;
    }

    @TypeConverter
    public static String fromLocalTime(LocalTime time) {
        return time != null ? time.toString() : null;
    }

    @TypeConverter
    public static LocalTime toLocalTime(String value) {
        return value != null ? LocalTime.parse(value) : null;
    }

    @TypeConverter
    public static String fromDayOfWeek(DayOfWeek day) {
        return day != null ? day.name() : null;
    }

    @TypeConverter
    public static DayOfWeek toDayOfWeek(String value) {
        return value != null ? DayOfWeek.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromPriority(Priority prio) {
        return prio != null ? prio.name() : null;
    }

    @TypeConverter
    public static Priority toPriority(String value) {
        return value != null ? Priority.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromPeriod(Period period) {
        return period != null ? period.name() : null;
    }

    @TypeConverter
    public static Period toPeriod(String value) {
        return value != null ? Period.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromDaySet(Set<DayOfWeek> days) {
        return days != null ? days.stream().map(DayOfWeek::name).collect(Collectors.joining(",")) : null;
    }

    @TypeConverter
    public static Set<DayOfWeek> toDaySet(String value) {
        return value != null ? Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet()) : null;
    }
}

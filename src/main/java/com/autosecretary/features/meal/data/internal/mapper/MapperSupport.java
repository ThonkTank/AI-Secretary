package com.autosecretary.features.meal.data.internal.mapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MapperSupport {
    private MapperSupport() {
    }

    // Nullable conversions
    static Long asNullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String raw = value.toString().trim();
        if (raw.isEmpty()) return null;
        return Long.parseLong(raw);
    }

    static LocalDate asLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate date) return date;
        String raw = value.toString().trim();
        return raw.isEmpty() ? null : LocalDate.parse(raw);
    }

    static LocalDateTime asLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        String raw = value.toString().trim();
        return raw.isEmpty() ? null : LocalDateTime.parse(raw);
    }

    // Primitive conversions with fallback
    static long asLong(Object value, long fallback) {
        Long parsed = asNullableLong(value);
        return parsed == null ? fallback : parsed;
    }

    static long asLong(Object value) {
        return asLong(value, 0L);
    }

    static int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        String raw = value.toString().trim();
        return raw.isEmpty() ? fallback : Integer.parseInt(raw);
    }

    static int asInt(Object value) {
        return asInt(value, 0);
    }

    static double asDouble(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        String raw = value.toString().trim();
        return raw.isEmpty() ? fallback : Double.parseDouble(raw);
    }

    static double asDouble(Object value) {
        return asDouble(value, 0.0);
    }

    static boolean asBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        String raw = value.toString().trim();
        if (raw.isEmpty()) return fallback;
        return "1".equals(raw) || Boolean.parseBoolean(raw);
    }

    static boolean asBoolean(Object value) {
        return asBoolean(value, false);
    }

    static <E extends Enum<E>> E asEnum(Class<E> enumType, Object value, E fallback) {
        if (value == null) return fallback;
        if (enumType.isInstance(value)) return enumType.cast(value);
        String raw = value.toString().trim();
        if (raw.isEmpty()) return fallback;
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    static String enumNameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }

    static String toDateString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static String toDateTimeString(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }

    // "Both-paths" collection deserialization: if the stored value is already the target type,
    // use it directly; otherwise parse it from a String via the supplied parser function.
    @SuppressWarnings("unchecked")
    static <T> List<T> asListOrParse(Object value, Function<String, List<T>> parser) {
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        return parser.apply(value instanceof String s ? s : null);
    }

    @SuppressWarnings("unchecked")
    static <T> Set<T> asSetOrParse(Object value, Function<String, Set<T>> parser) {
        if (value instanceof Set<?> set) {
            return (Set<T>) set;
        }
        return parser.apply(value instanceof String s ? s : null);
    }

    // DayOfWeek set serialization — comma-separated names, e.g. "MONDAY,WEDNESDAY,FRIDAY"
    static Set<DayOfWeek> asDayOfWeekSet(Object value) {
        if (value instanceof Set<?> set) {
            Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
            for (Object item : set) {
                DayOfWeek day = asEnum(DayOfWeek.class, item, null);
                if (day != null) result.add(day);
            }
            return result;
        }
        if (value == null) return EnumSet.noneOf(DayOfWeek.class);
        String raw = value.toString().trim();
        if (raw.isEmpty()) return EnumSet.noneOf(DayOfWeek.class);
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String part : raw.split(",")) {
            DayOfWeek day = asEnum(DayOfWeek.class, part.trim(), null);
            if (day != null) result.add(day);
        }
        return result;
    }

    static String serializeDayOfWeekSet(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return "";
        return days.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}

package com.autosecretary.features.meal.data.internal.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    static Object get(Map<String, Object> row, String key, String fallbackKey) {
        Object value = row.get(key);
        return value != null ? value : (fallbackKey != null ? row.get(fallbackKey) : null);
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

    // Safe collection conversions
    @SuppressWarnings("unchecked")
    static <E> Set<E> asSet(Object value) {
        if (value instanceof Set<?> set) {
            return (Set<E>) set;
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<E>) list;
        }
        return Collections.emptyList();
    }
}

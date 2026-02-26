package com.autosecretary.features.meal.data.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

final class LegacyMapperSupport {
    private LegacyMapperSupport() {
    }

    static Object get(Map<String, Object> row, String key, String fallbackKey) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return fallbackKey == null ? null : row.get(fallbackKey);
    }

    static Long asNullableLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        String raw = value.toString().trim();
        if (raw.isEmpty()) return null;
        return Long.parseLong(raw);
    }

    static long asLong(Object value, long fallback) {
        Long parsed = asNullableLong(value);
        return parsed == null ? fallback : parsed;
    }

    static int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        String raw = value.toString().trim();
        return raw.isEmpty() ? fallback : Integer.parseInt(raw);
    }

    static double asDouble(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        String raw = value.toString().trim();
        return raw.isEmpty() ? fallback : Double.parseDouble(raw);
    }

    static boolean asBoolean(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        String raw = value.toString().trim();
        if (raw.isEmpty()) return fallback;
        return "1".equals(raw) || Boolean.parseBoolean(raw);
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

    static <E extends Enum<E>> E asEnum(Class<E> enumType, Object value, E fallback) {
        if (value == null) return fallback;
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) return fallback;
        try {
            return Enum.valueOf(enumType, raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

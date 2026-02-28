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

/**
 * Safe type conversion utilities for RowMapper implementations.
 *
 * <p>RowMappers deserialize storage-level {@code Map<String, Object>} rows into strongly-typed
 * domain entities. Storage may contain values in various forms (actual types, strings, numbers),
 * so MapperSupport provides robust, null-safe conversion methods.
 *
 * <h3>Conversion Patterns</h3>
 *
 * <p><b>Nullable conversions</b> (return the target type or null):
 * <ul>
 *   <li>{@link #asNullableLong(Object)}, {@link #asLocalDate(Object)}, etc.
 * </ul>
 *
 * <p><b>Primitive conversions with fallback</b> (return primitive or fallback value):
 * <ul>
 *   <li>{@link #asInt(Object, int)} — parse to int, fallback if null or invalid
 *   <li>{@link #asInt(Object)} — fallback to 0
 *   <li>Similar overloads for {@code long}, {@code double}, {@code boolean}
 * </ul>
 *
 * <p><b>Enum conversions</b> (case-insensitive by enum name):
 * <ul>
 *   <li>{@link #asEnum(Class, Object, Object)} — parse by enum name, fallback if invalid
 * </ul>
 *
 * <p><b>"Both-paths" collection deserialization</b> (handle both native and string representations):
 * <ul>
 *   <li>{@link #asListOrParse(Object, Function)} — if value is already a {@code List<T>}, use it;
 *       otherwise parse from string via supplied parser function
 *   <li>{@link #asDayOfWeekSet(Object)} — specialized for {@code Set<DayOfWeek>}
 * </ul>
 *
 * <h3>Example Usage</h3>
 *
 * <p>A typical RowMapper's {@code fromRow()} method:
 * <pre>
 * &#64;Override
 * public Recipe fromRow(Map&lt;String, Object&gt; row) {
 *     Recipe recipe = new Recipe();
 *     recipe.id = MapperSupport.asNullableLong(row.get("id"));
 *     recipe.title = (String) row.get("title"); // String fields use raw cast
 *     recipe.servings = MapperSupport.asInt(row.get("servings"), 2); // with fallback
 *     recipe.prepEffort = MapperSupport.asEnum(Recipe.PrepEffort.class,
 *         row.get("prepEffort"), Recipe.PrepEffort.MEDIUM);
 *     recipe.tags = (String) row.get("tags"); // nullable string
 *     recipe.ingredients = MapperSupport.asListOrParse(row.get("ingredients"),
 *         MyMapper::parseIngredientsList); // both-paths pattern
 *     return recipe;
 * }
 * </pre>
 *
 * <h3>Custom Serialization and Delimiters</h3>
 *
 * <p>For complex nested objects (e.g., {@code List<StorePackage>}), mappers often serialize to
 * a delimited string (e.g., "field1|field2;record2|field2"). Use these delimiters consistently:
 * <ul>
 *   <li>{@code |} (pipe) — field separator within a record
 *   <li>{@code ;} (semicolon) — record separator
 *   <li>{@code ,} (comma) — alternate record separator (for simpler single-field records)
 * </ul>
 * <b>Critical:</b> Nested values (field names, enum names, user-entered text) must not contain
 * these characters, or parsing will fail. Document this constraint in your mapper implementation.
 *
 * @see RowMapper
 */
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

    static Integer asNullableInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        String raw = value.toString().trim();
        if (raw.isEmpty()) return null;
        return Integer.parseInt(raw);
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
        Integer parsed = asNullableInt(value);
        return parsed == null ? fallback : parsed;
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

    // Generic enum-set serialization — comma-separated names, e.g. "MONDAY,WEDNESDAY"
    static <E extends Enum<E>> Set<E> asEnumSet(Class<E> enumType, Object value) {
        if (value instanceof Set<?> set) {
            Set<E> result = EnumSet.noneOf(enumType);
            for (Object item : set) {
                E e = asEnum(enumType, item, null);
                if (e != null) result.add(e);
            }
            return result;
        }
        if (value == null) return EnumSet.noneOf(enumType);
        String raw = value.toString().trim();
        if (raw.isEmpty()) return EnumSet.noneOf(enumType);
        Set<E> result = EnumSet.noneOf(enumType);
        for (String part : raw.split(",")) {
            E e = asEnum(enumType, part.trim(), null);
            if (e != null) result.add(e);
        }
        return result;
    }

    static String serializeEnumSet(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    // DayOfWeek convenience wrappers
    static Set<DayOfWeek> asDayOfWeekSet(Object value) {
        return asEnumSet(DayOfWeek.class, value);
    }

    static String serializeDayOfWeekSet(Set<DayOfWeek> days) {
        return serializeEnumSet(days);
    }
}

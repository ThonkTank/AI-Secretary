package repository.parser;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Safe parsing utilities for repository parsers.
 * All methods return null (or default) on invalid input instead of throwing exceptions.
 */
public final class ParseUtils {

    private ParseUtils() {} // Utility class

    // ============== ENUM PARSING ==============

    /**
     * Safely parses an enum value, returning null if parsing fails.
     * @param enumClass The enum class
     * @param value The string value to parse (may be null)
     * @param <T> The enum type
     * @return The enum constant or null if value is null/invalid
     */
    public static <T extends Enum<T>> T safeEnum(Class<T> enumClass, String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            android.util.Log.w("ParseUtils", "Unknown enum value '" + value + "' for " + enumClass.getSimpleName());
            return null;
        }
    }

    /**
     * Safely parses an enum value with a default fallback.
     * @param enumClass The enum class
     * @param value The string value to parse (may be null)
     * @param defaultValue The default value to return on error
     * @param <T> The enum type
     * @return The enum constant or defaultValue if value is null/invalid
     */
    public static <T extends Enum<T>> T safeEnum(Class<T> enumClass, String value, T defaultValue) {
        T result = safeEnum(enumClass, value);
        return result != null ? result : defaultValue;
    }

    /**
     * Safely parses DayOfWeek (java.time.DayOfWeek).
     */
    public static DayOfWeek safeDayOfWeek(String value) {
        return safeEnum(DayOfWeek.class, value);
    }

    // ============== DATE/TIME PARSING ==============

    /**
     * Safely parses a LocalDate, returning null on error.
     */
    public static LocalDate safeLocalDate(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            android.util.Log.w("ParseUtils", "Invalid LocalDate: " + value);
            return null;
        }
    }

    /**
     * Safely parses a LocalTime, returning null on error.
     */
    public static LocalTime safeLocalTime(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            android.util.Log.w("ParseUtils", "Invalid LocalTime: " + value);
            return null;
        }
    }

    /**
     * Safely parses a LocalDateTime, returning null on error.
     */
    public static LocalDateTime safeLocalDateTime(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            android.util.Log.w("ParseUtils", "Invalid LocalDateTime: " + value);
            return null;
        }
    }

    // ============== NUMBER PARSING ==============

    /**
     * Safely parses a Long, returning null on error.
     */
    public static Long safeLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            android.util.Log.w("ParseUtils", "Invalid Long: " + value);
            return null;
        }
    }

    /**
     * Safely parses an int, returning defaultValue on error.
     */
    public static int safeInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            android.util.Log.w("ParseUtils", "Invalid int: " + value);
            return defaultValue;
        }
    }

    /**
     * Safely parses a double, returning defaultValue on error.
     */
    public static double safeDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            android.util.Log.w("ParseUtils", "Invalid double: " + value);
            return defaultValue;
        }
    }

    /**
     * Safely parses a boolean from various representations.
     */
    public static boolean safeBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String s = value.toString().toLowerCase();
        if ("true".equals(s) || "1".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s)) return false;
        return defaultValue;
    }
}

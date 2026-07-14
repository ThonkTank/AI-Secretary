package com.autosecretary.features.assistant.application.internal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lenient JSON helpers shared by the tool handlers. Reads treat blank strings as absent and null out
 * missing fields; the {@code require*} variants throw {@link IllegalArgumentException} (surfaced to
 * the model as an {@code is_error} tool result) when a mandatory field is missing or malformed.
 */
public final class AssistantJson {

    private AssistantJson() {
    }

    public static String optString(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        String value = entry.optString(key, null);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static Integer optInt(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        return entry.optInt(key);
    }

    public static Boolean optBool(JSONObject entry, String key) {
        if (entry == null || !entry.has(key) || entry.isNull(key)) {
            return null;
        }
        return entry.optBoolean(key);
    }

    public static LocalTime optTime(JSONObject entry, String key) {
        String raw = optString(entry, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalTime.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültige Uhrzeit in \"" + key + "\": " + raw);
        }
    }

    public static LocalDate optDate(JSONObject input, String key) {
        String raw = optString(input, key);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültiges Datum in \"" + key + "\": " + raw);
        }
    }

    public static LocalDate requireDate(JSONObject input, String key) {
        LocalDate date = optDate(input, key);
        if (date == null) {
            throw new IllegalArgumentException("Feld \"" + key + "\" fehlt oder ist kein Datum (YYYY-MM-DD).");
        }
        return date;
    }

    public static JSONArray requireArray(JSONObject input, String key) {
        JSONArray array = input == null ? null : input.optJSONArray(key);
        if (array == null) {
            throw new IllegalArgumentException("Feld \"" + key + "\" fehlt oder ist kein Array.");
        }
        return array;
    }

    public static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}

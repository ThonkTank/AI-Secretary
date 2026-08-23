package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;

final class BundleValues {
    private BundleValues() { }

    static void putInteger(Bundle bundle, String key, Integer value) {
        if (value != null) { bundle.putBoolean(key + "_set", true); bundle.putInt(key, value); }
    }

    static Integer integer(Bundle bundle, String key) {
        return bundle.getBoolean(key + "_set") ? bundle.getInt(key) : null;
    }

    static void putDate(Bundle bundle, String key, LocalDate value) {
        if (value != null) bundle.putString(key, value.toString());
    }

    static LocalDate date(Bundle bundle, String key) {
        String value = bundle.getString(key);
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }

    static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { return fallback; }
    }
}

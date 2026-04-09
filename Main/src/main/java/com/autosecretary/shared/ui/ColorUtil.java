package com.autosecretary.shared.ui;

import android.graphics.Color;

import java.util.regex.Pattern;

/**
 * Safe color parsing utility. Validates hex strings before calling {@link Color#parseColor},
 * avoiding {@link IllegalArgumentException} on malformed input.
 */
public final class ColorUtil {
    private ColorUtil() {}

    private static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /**
     * Parses a hex color string (e.g. {@code "#FF5C7A4D"} or {@code "#5C7A4D"}) and returns
     * the parsed color, or {@code fallback} if the string is null or not a valid hex color.
     */
    public static int parseColorSafe(String hex, int fallback) {
        if (hex != null && HEX_COLOR.matcher(hex).matches()) {
            return Color.parseColor(hex);
        }
        return fallback;
    }
}

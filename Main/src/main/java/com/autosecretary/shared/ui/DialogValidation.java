package com.autosecretary.shared.ui;

import android.content.Context;

import androidx.annotation.Nullable;

import com.autosecretary.R;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Shared validation helpers for dialog controllers.
 * Each method sets an inline error on the field and returns null on failure.
 */
public final class DialogValidation {

    private DialogValidation() {}

    @Nullable
    public static String requireNonEmpty(TextInputEditText field, String fieldLabel, Context context) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) {
            field.setError(context.getString(R.string.error_field_required, fieldLabel));
            field.requestFocus();
            return null;
        }
        return value;
    }

    @Nullable
    public static LocalDate parseDate(TextInputEditText field, Context context) {
        try {
            return LocalDate.parse(field.getText().toString().trim());
        } catch (DateTimeParseException e) {
            field.setError(context.getString(R.string.error_invalid_date));
            field.requestFocus();
            return null;
        }
    }

    @Nullable
    public static Integer parseInt(TextInputEditText field, String fieldName, Context context) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.error_invalid_number, fieldName));
            field.requestFocus();
            return null;
        }
    }

    @Nullable
    public static Double parseDouble(TextInputEditText field, String fieldName, Context context) {
        try {
            return Double.parseDouble(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.error_invalid_number, fieldName));
            field.requestFocus();
            return null;
        }
    }

    /** Returns the trimmed text of a {@link TextInputEditText}, never null. */
    public static String textOf(TextInputEditText input) {
        return Objects.toString(input.getText(), "").trim();
    }

    /** Returns trimmed text, or {@code null} if the field is empty/blank. */
    @Nullable
    public static String textOfNullable(TextInputEditText input) {
        String text = textOf(input);
        return text.isEmpty() ? null : text;
    }

    /** Parses a trimmed string to int, returning {@code fallback} if null, empty, or non-numeric. */
    public static int parseIntOrDefault(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

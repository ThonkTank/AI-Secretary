package com.autosecretary.features.meal.ui.internal;

import android.content.Context;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.autosecretary.R;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Shared validation helpers for meal dialog controllers.
 * Each method sets an inline error on the field and returns null on failure.
 */
final class MealDialogValidation {

    private MealDialogValidation() {}

    @Nullable
    static String requireNonEmpty(EditText field, String fieldLabel, Context context) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) {
            field.setError(context.getString(R.string.meal_error_field_required, fieldLabel));
            field.requestFocus();
            return null;
        }
        return value;
    }

    @Nullable
    static LocalDate parseDate(EditText field, Context context) {
        try {
            return LocalDate.parse(field.getText().toString().trim());
        } catch (DateTimeParseException e) {
            field.setError(context.getString(R.string.meal_error_invalid_date));
            field.requestFocus();
            return null;
        }
    }

    @Nullable
    static Integer parseInt(EditText field, String fieldName, Context context) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.meal_error_invalid_number, fieldName));
            field.requestFocus();
            return null;
        }
    }

    @Nullable
    static Double parseDouble(EditText field, String fieldName, Context context) {
        try {
            return Double.parseDouble(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            field.setError(context.getString(R.string.meal_error_invalid_number, fieldName));
            field.requestFocus();
            return null;
        }
    }
}

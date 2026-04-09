package com.autosecretary.shared.ui;

import android.app.DatePickerDialog;
import android.content.Context;

import com.autosecretary.R;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

import androidx.appcompat.app.AlertDialog;

/**
 * Shared dialog utilities for consistent dialog behavior across all features.
 *
 * <p>The caller must build the dialog with {@code setPositiveButton(label, null)} so
 * the default click handler does not auto-dismiss. {@code showWithValidation} wires
 * the positive button to run the provided callback, which is responsible for
 * validating input and calling {@code dialog.dismiss()} on success.
 */
public final class DialogHelper {
    private DialogHelper() {}

    /**
     * Wires a {@link TextInputEditText} as a non-keyboard-editable date picker.
     *
     * <p>The field must already contain an ISO date string (yyyy-MM-dd) or be empty;
     * an empty/unparseable field defaults to today. Tapping the field opens a
     * {@link DatePickerDialog} and writes the chosen date back as {@code LocalDate.toString()}
     * (ISO format). The field must be set {@code focusable="false"} in XML to prevent
     * the soft keyboard from appearing.
     *
     * @param dateField the TextInputEditText that displays the date
     * @param context   the context used to create the DatePickerDialog
     */
    public static void setupDatePicker(TextInputEditText dateField, Context context) {
        dateField.setOnClickListener(v -> {
            LocalDate current = tryParseDate(dateField.getText().toString().trim());
            // DatePickerDialog uses 0-based months; java.time uses 1-based.
            new DatePickerDialog(context, (picker, year, month, day) ->
                    dateField.setText(LocalDate.of(year, month + 1, day).toString()),
                    current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()
            ).show();
        });
    }

    private static LocalDate tryParseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }

    /**
     * Shows a standard delete confirmation dialog with the given title, message, and callback.
     */
    public static void showDeleteConfirmation(Context context, int titleRes,
                                              int messageRes, Runnable onConfirm) {
        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Shows the dialog with validation-before-dismiss on the positive button.
     *
     * @see #showWithValidation(AlertDialog, Runnable, Consumer)
     */
    public static void showWithValidation(AlertDialog dialog, Runnable onPositiveClick) {
        showWithValidation(dialog, onPositiveClick, null);
    }

    /**
     * Shows the dialog with validation-before-dismiss on the positive button,
     * plus an optional extra callback for additional button/view wiring that must
     * happen after the dialog is shown (e.g. neutral-button handlers, row click listeners).
     *
     * @param dialog           the dialog to show (positive button must use {@code null} listener)
     * @param onPositiveClick  runs when the positive button is tapped; must call
     *                         {@code dialog.dismiss()} on success
     * @param onShowExtra      optional extra setup to run once the dialog is visible;
     *                         receives the dialog as parameter; may be {@code null}
     */
    public static void showWithValidation(AlertDialog dialog, Runnable onPositiveClick,
                                          Consumer<AlertDialog> onShowExtra) {
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> onPositiveClick.run());
            if (onShowExtra != null) onShowExtra.accept(dialog);
        });
        dialog.show();
    }
}

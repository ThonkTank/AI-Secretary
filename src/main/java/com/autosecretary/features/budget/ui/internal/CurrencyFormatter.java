package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formats cent amounts as Euro strings and validates display-related values for the budget UI.
 */
public final class CurrencyFormatter {
    private CurrencyFormatter() {}

    private static final Pattern COLOR_HEX_PATTERN =
            Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /** Returns {@code true} if {@code colorHex} is a valid 6- or 8-digit hex color string (e.g. {@code "#FF7043"}). */
    public static boolean isValidColorHex(String colorHex) {
        if (colorHex == null) return false;
        return COLOR_HEX_PATTERN.matcher(colorHex).matches();
    }

    /**
     * Formats a cent value as a plain Euro amount without sign, e.g. {@code "12 €"}.
     * Used for axis labels in the balance chart.
     */
    public static String euros(long cents) {
        return String.format(Locale.GERMAN, "%.0f €", cents / 100.0);
    }

    /**
     * Formats a cent value with a direction sign, e.g. {@code "+12.50 €"} or {@code "-3.00 €"}.
     * Used for transaction row amounts.
     */
    public static String eurosWithSign(long amountCents, TransactionDirection direction) {
        return String.format(
                Locale.GERMAN,
                "%s%.2f €",
                direction == TransactionDirection.EXPENSE ? "-" : "+",
                amountCents / 100.0
        );
    }

    /**
     * Formats a signed cent value, suppressing the sign when zero or positive (no '+' prefix).
     * e.g. {@code "12.50 €"}, {@code "-3.00 €"}. Used for net balance display in the widget.
     */
    public static String eurosPlain(long amountCents) {
        return String.format(Locale.GERMAN, "%.2f €", amountCents / 100.0);
    }

    /**
     * Formats a cent value always showing a sign: {@code "+12.50 €"} or {@code "-3.00 €"}.
     * Used for income/expense/net summary fields in the budget overview.
     */
    public static String eurosAlwaysSigned(long amountCents) {
        return String.format(Locale.GERMAN, "%+.2f €", amountCents / 100.0);
    }

    /**
     * Formats the absolute value of a cent amount with two decimal places, e.g. {@code "3.50 €"}.
     * Use when sign is conveyed separately (e.g. via text color).
     */
    public static String eurosMagnitude(long amountCents) {
        return String.format(Locale.GERMAN, "%.2f €", Math.abs(amountCents) / 100.0);
    }
}

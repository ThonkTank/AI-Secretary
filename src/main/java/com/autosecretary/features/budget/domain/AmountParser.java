package com.autosecretary.features.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Parses a raw amount string from a bank CSV export into cents.
 *
 * <p>Input may contain locale-specific decimal/thousands separators (comma or dot) and
 * non-breaking spaces ({@code \u00A0}) commonly injected by German bank statement exports.
 * Returns {@code null} on unparseable input rather than throwing.
 */
public class AmountParser {

    /**
     * Parses {@code amountStr} and returns the value in cents, or {@code null} if the string
     * is null, blank, or cannot be parsed.
     */
    public Long parseAmountCents(String amountStr) {
        if (amountStr == null) {
            return null;
        }

        // Strip regular and non-breaking spaces (\u00A0) — the latter is common in German
        // bank statement CSV exports (e.g. "1\u00A0234,56 €").
        String normalized = amountStr.trim()
                .replace("\u00A0", "")
                .replace(" ", "");

        if (normalized.isEmpty()) {
            return null;
        }

        int commaIndex = normalized.lastIndexOf(',');
        int dotIndex = normalized.lastIndexOf('.');

        if (commaIndex >= 0 && dotIndex >= 0) {
            // Both separators present: whichever appears last is the decimal separator.
            // e.g. "1.234,56" → decimal=','; "1,234.56" → decimal='.'
            int decimalIndex = Math.max(commaIndex, dotIndex);
            char decimalSeparator = normalized.charAt(decimalIndex);
            char thousandsSeparator = decimalSeparator == ',' ? '.' : ',';
            normalized = normalized.replace(String.valueOf(thousandsSeparator), "")
                    .replace(decimalSeparator, '.');
        } else if (commaIndex >= 0) {
            normalized = normalized.replace(',', '.');
        }

        try {
            BigDecimal amount = new BigDecimal(normalized);
            return amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}

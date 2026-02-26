package com.autosecretary.features.budget.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountParser {

    public Long parseAmountCents(String amountStr) {
        if (amountStr == null) {
            return null;
        }

        String normalized = amountStr.trim()
                .replace("\u00A0", "")
                .replace(" ", "");

        if (normalized.isEmpty()) {
            return null;
        }

        int commaIndex = normalized.lastIndexOf(',');
        int dotIndex = normalized.lastIndexOf('.');

        if (commaIndex >= 0 && dotIndex >= 0) {
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

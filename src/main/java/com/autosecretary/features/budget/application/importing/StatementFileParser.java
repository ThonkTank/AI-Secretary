package com.autosecretary.features.budget.application.importing;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimaler Statement-Parser für Importdateien.
 * Erwartet CSV mit Header: date,amountCents,payee,description,categoryId,importHash
 */
public class StatementFileParser {

    public record ParsedStatement(
            List<ParsedTransaction> transactions,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
    }

    public record ParsedTransaction(
            LocalDate date,
            int amountCents,
            String payee,
            String description,
            Long categoryId,
            String importHash
    ) {
    }

    public ParsedStatement parse(String fileName, byte[] fileBytes, String mimeType) {
        if (!accepts(fileName, mimeType)) {
            throw new IllegalArgumentException("Nicht unterstütztes Dateiformat: " + fileName);
        }

        String content = new String(fileBytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");
        if (lines.length <= 1) {
            return new ParsedStatement(List.of(), null, null);
        }

        List<ParsedTransaction> parsedTransactions = new ArrayList<>();
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] columns = line.split(",", -1);
            if (columns.length < 4) {
                throw new IllegalArgumentException("Ungültige CSV-Zeile: " + line);
            }

            LocalDate date = LocalDate.parse(columns[0].trim());
            int amountCents = Integer.parseInt(columns[1].trim());
            String payee = emptyToNull(columns[2]);
            String description = emptyToNull(columns[3]);
            Long categoryId = columns.length > 4 && !columns[4].isBlank() ? Long.parseLong(columns[4].trim()) : null;
            String importHash = columns.length > 5 ? emptyToNull(columns[5]) : null;

            parsedTransactions.add(new ParsedTransaction(date, amountCents, payee, description, categoryId, importHash));
            if (periodStart == null || date.isBefore(periodStart)) {
                periodStart = date;
            }
            if (periodEnd == null || date.isAfter(periodEnd)) {
                periodEnd = date;
            }
        }

        return new ParsedStatement(parsedTransactions, periodStart, periodEnd);
    }

    boolean accepts(String fileName, String mimeType) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        String lowerMime = mimeType == null ? "" : mimeType.toLowerCase();
        return lowerName.endsWith(".csv")
                || "text/csv".equals(lowerMime)
                || "application/vnd.ms-excel".equals(lowerMime);
    }

    private String emptyToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.api.ClaudeStatementApiClient;
import com.autosecretary.features.budget.data.api.ClaudeApiKeyStore;
import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ParsedStatement;
import com.autosecretary.features.budget.domain.importing.ParsedTransaction;
import com.autosecretary.features.budget.domain.BudgetImportRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes bank statement file parsing by type: CSV is parsed locally (fast, no external dependencies),
 * PDF is sent to Claude API for extraction (requires API key configured in {@link ClaudeApiKeyStore}).
 *
 * <p>CSV format: columns are date, amountCents, payee, description, [categoryId], [importHash].
 * See {@link #parseCsv(byte[])} for details.
 *
 * <p>If PDF mode is requested without a configured API key, throws {@code IllegalArgumentException}.
 *
 * @see ClaudeApiKeyStore for API key configuration
 * @see ClaudeStatementApiClient for PDF extraction via Claude API
 * @see README.md for the full import pipeline documentation
 */
public class StatementFileParser {

    private final ClaudeStatementApiClient claudeApiClient;
    private final ClaudeApiKeyStore apiKeyStore;
    private final BudgetImportRepository importRepository;

    public StatementFileParser(ClaudeStatementApiClient claudeApiClient,
                               ClaudeApiKeyStore apiKeyStore,
                               BudgetImportRepository importRepository) {
        this.claudeApiClient = claudeApiClient;
        this.apiKeyStore = apiKeyStore;
        this.importRepository = importRepository;
    }

    public ParsedStatement parse(String fileName, byte[] fileBytes, String mimeType) {
        if (isPdf(fileName, mimeType)) {
            return parsePdf(fileBytes);
        }
        if (!isCsv(fileName, mimeType)) {
            throw new IllegalArgumentException("Nicht unterstütztes Dateiformat: " + fileName);
        }
        return parseCsv(fileBytes);
    }

    private ParsedStatement parsePdf(byte[] fileBytes) {
        String apiKey = apiKeyStore.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Kein Claude API-Key hinterlegt. Bitte in den Budget-Einstellungen setzen."
            );
        }
        List<ImportCategory> categories = importRepository.loadActiveCategoriesForImport();
        return claudeApiClient.parsePdf(apiKey, fileBytes, categories);
    }

    /**
     * Parses CSV file format with the following columns (comma-separated, LF or CRLF line endings):
     * <ol>
     *   <li>date — ISO 8601 format (e.g., 2024-01-15)</li>
     *   <li>amountCents — integer; positive for income, negative for expense</li>
     *   <li>payee — string (e.g., "Amazon Inc."); empty acceptable</li>
     *   <li>description — string (e.g., "Monthly subscription"); empty acceptable</li>
     *   <li>categoryId (optional) — UUID of a known category; empty acceptable</li>
     *   <li>importHash (optional) — deduplication hash from statement file; empty acceptable</li>
     * </ol>
     *
     * <p>If a row has <4 columns, throws IllegalArgumentException.
     * If date or amountCents cannot be parsed, throws NumberFormatException or DateTimeException.
     *
     * <p>Returns a ParsedStatement with:
     * - all valid parsed transactions
     * - periodStart and periodEnd derived from the earliest and latest booking dates (for statement period tracking)
     *
     * <p>Empty files (header only or all empty rows) return empty transactions with null date bounds.
     *
     * @param fileBytes UTF-8 encoded CSV content
     * @return ParsedStatement ready for enrichment and deduplication
     */
    private ParsedStatement parseCsv(byte[] fileBytes) {
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

            // split(",", -1) keeps trailing empty fields, ensuring consistent column count.
            // This allows us to distinguish between "missing value" and "column not provided at all".
            String[] columns = line.split(",", -1);
            if (columns.length < 4) {
                throw new IllegalArgumentException("Ungültige CSV-Zeile: " + line);
            }

            LocalDate bookingDate = LocalDate.parse(columns[0].trim());
            long amountCents = Long.parseLong(columns[1].trim());
            String payee = emptyToNull(columns[2]);
            String note = emptyToNull(columns[3]);
            String categoryId = columns.length > 4 ? emptyToNull(columns[4]) : null;
            String importHash = columns.length > 5 ? emptyToNull(columns[5]) : null;

            parsedTransactions.add(new ParsedTransaction(bookingDate, amountCents, payee, note, categoryId, importHash));

            // Track the earliest and latest transaction dates to report the statement's period.
            // These bounds are used for UI display and overlap validation with previous imports.
            if (periodStart == null || bookingDate.isBefore(periodStart)) {
                periodStart = bookingDate;
            }
            if (periodEnd == null || bookingDate.isAfter(periodEnd)) {
                periodEnd = bookingDate;
            }
        }

        return new ParsedStatement(parsedTransactions, periodStart, periodEnd);
    }

    private static boolean isPdf(String fileName, String mimeType) {
        String lowerName = normalise(fileName);
        String lowerMime = normalise(mimeType);
        return lowerName.endsWith(".pdf") || "application/pdf".equals(lowerMime);
    }

    private static boolean isCsv(String fileName, String mimeType) {
        String lowerName = normalise(fileName);
        String lowerMime = normalise(mimeType);
        return lowerName.endsWith(".csv")
                || "text/csv".equals(lowerMime)
                || "application/vnd.ms-excel".equals(lowerMime);
    }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    static String emptyToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

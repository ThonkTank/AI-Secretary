package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;
import java.util.List;

/**
 * Complete parsed bank statement with zero or more transactions and a date range.
 * <p>
 * Produced by StatementFileParser after extracting and parsing a CSV or PDF file.
 * The {@code periodStart} and {@code periodEnd} represent the statement's date range,
 * typically a calendar month or a user-selected period.
 * </p>
 * <p>
 * The {@code transactions} list may be empty if the statement contained no transactions,
 * or may contain duplicates if the file was parsed multiple times (deduplication happens
 * during import via ImportTransactionRecord.importHash).
 * </p>
 *
 * @param transactions Zero or more transactions extracted from the statement
 * @param periodStart  The start of the statement period (inclusive)
 * @param periodEnd    The end of the statement period (inclusive)
 */
public record ParsedStatement(
        List<ParsedTransaction> transactions,
        LocalDate periodStart,
        LocalDate periodEnd
) {}

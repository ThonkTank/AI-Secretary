package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

/**
 * Raw transaction data extracted from a parsed bank statement (CSV or PDF).
 * <p>
 * Produced by StatementFileParser during CSV/PDF parsing. This is a lightweight DTO
 * representing a single transaction before account-level enrichment. The parser extracts
 * these fields from the statement file, with {@code categoryId} optionally pre-assigned
 * if the CSV contained a category column.
 * </p>
 * <p>
 * This record is later decorated with account context and mapped to ImportTransactionRecord,
 * then persisted as BudgetTransactionEntity in the data layer.
 * </p>
 *
 * @param bookingDate  The transaction date (typically the statement booking date)
 * @param amountCents  Transaction amount in cents (positive for both income and expense; direction is inferred separately)
 * @param payee        Merchant or counterparty name
 * @param note         Optional transaction memo or description
 * @param categoryId   Optional category ID (from CSV column or left null if uncategorized). Later mapped to ImportCategory or BudgetCategory.
 * @param importHash   Hash used for deduplication (prevents duplicate imports if the same statement is imported multiple times)
 */
public record ParsedTransaction(
        LocalDate bookingDate,
        long amountCents,
        String payee,
        String note,
        String categoryId,
        String importHash
) {
}

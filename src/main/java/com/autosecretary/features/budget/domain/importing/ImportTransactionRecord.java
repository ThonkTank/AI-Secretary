package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

/**
 * Domain DTO for an imported transaction after enrichment and validation.
 * <p>
 * Produced by the import pipeline (BudgetImportUseCase, BudgetTransactionMapper) after:
 * <ol>
 *   <li>Parsing a bank statement (CSV/PDF) into ParsedTransaction objects</li>
 *   <li>Assigning the account context and resolving categories</li>
 *   <li>Type inference (INCOME, EXPENSE, TRANSFER)</li>
 * </ol>
 * This record is then persisted to the data layer as BudgetTransactionEntity.
 * </p>
 * <p>
 * Field semantics (read carefully; naming is deliberately distinct):
 * </p>
 *
 * Split into cohesive sub-records:
 * - {@link TransactionData} contains the transaction facts needed for persistence
 * - {@link ImportMetadata} contains import-session and matching metadata
 */
public record ImportTransactionRecord(
        TransactionData transaction,
        ImportMetadata metadata
) {
    public record TransactionData(
            String id,
            String accountId,
            String categoryId,
            ImportTransactionType type,
            long amountCents,
            LocalDate bookingDate,
            String note,
            String payee
    ) {}

    public record ImportMetadata(
            String importHash,
            String importId,
            String templateId
    ) {}

    public String id() { return transaction.id(); }
    public String accountId() { return transaction.accountId(); }
    public String categoryId() { return transaction.categoryId(); }
    public ImportTransactionType type() { return transaction.type(); }
    public long amountCents() { return transaction.amountCents(); }
    public LocalDate bookingDate() { return transaction.bookingDate(); }
    public String note() { return transaction.note(); }
    public String payee() { return transaction.payee(); }
    public String importHash() { return metadata.importHash(); }
    public String importId() { return metadata.importId(); }
    public String templateId() { return metadata.templateId(); }
}

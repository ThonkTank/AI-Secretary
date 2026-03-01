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
 * @param id              Unique transaction ID within this import (generated UUID; used as @PrimaryKey in BudgetTransactionEntity)
 * @param accountId       The account this transaction was imported into (references the BudgetAccountEntity)
 * @param categoryId      The category for this transaction (references BudgetCategoryEntity; may be empty if uncategorized)
 * @param type            Type classification: INCOME, EXPENSE, or TRANSFER (determines direction and kind)
 * @param amountCents     Transaction amount in cents (absolute value; direction comes from type)
 * @param bookingDate     The transaction date from the statement
 * @param note            Optional memo or description
 * @param importHash      Hash for deduplication (derived from statement data; prevents duplicate imports if same file is re-imported)
 * @param payee           Merchant or counterparty name
 * @param importId        Identifies the import batch/session (UUID; groups transactions imported together in one operation)
 * @param templateId      If this transaction matched a recurring pattern template, the template's UUID. Otherwise null. Used to link to RecurringBudgetTemplate for future pattern suggestions.
 */
public record ImportTransactionRecord(
        String id,
        String accountId,
        String categoryId,
        ImportTransactionType type,
        long amountCents,
        LocalDate bookingDate,
        String note,
        String importHash,
        String payee,
        String importId,
        String templateId
) {}

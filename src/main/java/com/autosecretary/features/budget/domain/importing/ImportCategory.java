package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;

/**
 * Category used during budget import to classify transactions.
 * <p>
 * Represents a user-defined or system-provided category that the import parser (StatementFileParser)
 * uses to assign categories to parsed transactions. The {@code direction} indicates whether this
 * category is typically used for income or expense transactions.
 * </p>
 * <p>
 * After import, this category ID is mapped to a persistent BudgetCategory in the data layer.
 * </p>
 *
 * @param id        Unique category identifier
 * @param name      Human-readable category name
 * @param direction Whether this category classifies INCOME or EXPENSE transactions
 */
public record ImportCategory(String id, String name, TransactionDirection direction) {}

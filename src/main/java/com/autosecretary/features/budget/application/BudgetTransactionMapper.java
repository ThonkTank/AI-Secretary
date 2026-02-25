package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.YearMonth;

/**
 * Explicit mapper between domain import/recurring model and Room entity.
 */
public class BudgetTransactionMapper {

    public BudgetTransactionEntity toEntity(RecurringBudgetTransaction domainTransaction) {
        if (domainTransaction == null) {
            return null;
        }

        if (domainTransaction.accountId == null || domainTransaction.transactionDate == null) {
            throw new IllegalArgumentException("accountId and transactionDate are required");
        }

        BudgetTransactionEntity.TransactionType type =
                domainTransaction.amountCents >= 0
                        ? BudgetTransactionEntity.TransactionType.INCOME
                        : BudgetTransactionEntity.TransactionType.EXPENSE;

        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                String.valueOf(domainTransaction.accountId),
                domainTransaction.categoryId != null ? String.valueOf(domainTransaction.categoryId) : null,
                type,
                Math.abs(domainTransaction.amountCents) / 100.0,
                domainTransaction.transactionDate,
                YearMonth.from(domainTransaction.transactionDate).toString()
        );

        if (domainTransaction.id != null) {
            entity.id = String.valueOf(domainTransaction.id);
        }
        entity.note = domainTransaction.description;
        return entity;
    }

    public RecurringBudgetTransaction toDomain(BudgetTransactionEntity entity) {
        if (entity == null) {
            return null;
        }

        long accountId = parseRequiredLong(entity.accountId, "accountId");
        Long categoryId = parseNullableLong(entity.categoryId);
        int signedAmountCents = (int) Math.round(entity.amount * 100.0);
        if (entity.type == BudgetTransactionEntity.TransactionType.EXPENSE) {
            signedAmountCents = -Math.abs(signedAmountCents);
        } else {
            signedAmountCents = Math.abs(signedAmountCents);
        }

        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(
                accountId,
                signedAmountCents,
                entity.bookingDate,
                categoryId
        ).description(entity.note).build();

        tx.id = parseNullableLong(entity.id);
        return tx;
    }

    private long parseRequiredLong(String value, String fieldName) {
        Long parsed = parseNullableLong(value);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + " must be numeric");
        }
        return parsed;
    }

    private Long parseNullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value, e);
        }
    }
}

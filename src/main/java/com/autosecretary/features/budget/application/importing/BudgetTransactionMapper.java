package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit mapper between domain import/recurring model and Room entity.
 */
public class BudgetTransactionMapper {

    public List<BudgetTransactionEntity> toEntities(
            List<StatementFileParser.ParsedTransaction> transactions, String accountId) {
        List<BudgetTransactionEntity> result = new ArrayList<>(transactions.size());
        for (StatementFileParser.ParsedTransaction tx : transactions) {
            result.add(toEntity(tx, accountId));
        }
        return result;
    }

    public BudgetTransactionEntity toEntity(
            StatementFileParser.ParsedTransaction tx, String accountId) {
        boolean isExpense = tx.amountCents() < 0;
        BudgetTransactionEntity.TransactionType type = isExpense
                ? BudgetTransactionEntity.TransactionType.EXPENSE
                : BudgetTransactionEntity.TransactionType.INCOME;

        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                accountId, null, type,
                Math.abs(tx.amountCents()),
                tx.date(),
                YearMonth.from(tx.date()).toString()
        );

        if (tx.description() != null) {
            entity.note = tx.description();
        } else if (tx.payee() != null) {
            entity.note = tx.payee();
        }
        return entity;
    }

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
                Math.abs(domainTransaction.amountCents),
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
        int signedAmountCents = (int) entity.amountCents;
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

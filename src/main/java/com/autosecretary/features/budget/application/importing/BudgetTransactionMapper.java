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
                domainTransaction.accountId,
                domainTransaction.categoryId,
                type,
                Math.abs(domainTransaction.amountCents),
                domainTransaction.transactionDate,
                YearMonth.from(domainTransaction.transactionDate).toString()
        );

        if (domainTransaction.id != null) {
            entity.id = domainTransaction.id;
        }
        entity.note = domainTransaction.description;
        entity.importHash = domainTransaction.importHash;
        entity.payee = domainTransaction.payee;
        entity.importId = domainTransaction.importId;
        return entity;
    }

    public RecurringBudgetTransaction toDomain(BudgetTransactionEntity entity) {
        if (entity == null) {
            return null;
        }

        if (entity.accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }

        int signedAmountCents = (int) entity.amountCents;
        if (entity.type == BudgetTransactionEntity.TransactionType.EXPENSE) {
            signedAmountCents = -Math.abs(signedAmountCents);
        } else {
            signedAmountCents = Math.abs(signedAmountCents);
        }

        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(
                entity.accountId,
                signedAmountCents,
                entity.bookingDate,
                entity.categoryId
        )
                .description(entity.note)
                .payee(entity.payee)
                .importHash(entity.importHash)
                .importId(entity.importId)
                .build();

        tx.id = entity.id;
        return tx;
    }
}

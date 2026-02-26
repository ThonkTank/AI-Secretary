package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit mapper between domain import/recurring model and persistence forms.
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

    public ImportTransactionRecord toRecord(RecurringBudgetTransaction domainTransaction) {
        if (domainTransaction == null) {
            return null;
        }

        if (domainTransaction.accountId == null || domainTransaction.transactionDate == null) {
            throw new IllegalArgumentException("accountId and transactionDate are required");
        }

        String type = domainTransaction.amountCents < 0 ? "EXPENSE" : "INCOME";
        return new ImportTransactionRecord(
                domainTransaction.id,
                domainTransaction.accountId,
                domainTransaction.categoryId,
                type,
                Math.abs(domainTransaction.amountCents),
                domainTransaction.transactionDate,
                YearMonth.from(domainTransaction.transactionDate).toString(),
                domainTransaction.description,
                domainTransaction.importHash,
                domainTransaction.payee,
                domainTransaction.importId,
                domainTransaction.parentRecurringId
        );
    }

    public RecurringBudgetTransaction toDomain(ImportTransactionRecord record) {
        if (record == null) {
            return null;
        }

        if (record.accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }

        int signedAmountCents = (int) record.amountCents;
        if ("EXPENSE".equals(record.type)) {
            signedAmountCents = -Math.abs(signedAmountCents);
        } else {
            signedAmountCents = Math.abs(signedAmountCents);
        }

        RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(
                record.accountId,
                signedAmountCents,
                record.bookingDate,
                record.categoryId
        )
                .description(record.note)
                .payee(record.payee)
                .importHash(record.importHash)
                .importId(record.importId)
                .build();

        tx.id = record.id;
        tx.parentRecurringId = record.templateId;
        tx.isRecurring = record.templateId != null && !record.templateId.isBlank();
        return tx;
    }
}

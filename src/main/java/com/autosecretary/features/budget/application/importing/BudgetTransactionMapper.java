package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;

import java.time.YearMonth;

/**
 * Explicit mapper between domain import/recurring model and persistence forms.
 */
public class BudgetTransactionMapper {

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

        if (record.accountId() == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }

        int signedAmountCents = (int) record.amountCents();
        if ("EXPENSE".equals(record.type())) {
            signedAmountCents = -Math.abs(signedAmountCents);
        } else {
            signedAmountCents = Math.abs(signedAmountCents);
        }

        RecurringBudgetTransaction tx = new RecurringBudgetTransaction();
        tx.id = record.id();
        tx.accountId = record.accountId();
        tx.amountCents = signedAmountCents;
        tx.transactionDate = record.bookingDate();
        tx.categoryId = record.categoryId();
        tx.description = record.note();
        tx.payee = record.payee();
        tx.importHash = record.importHash();
        tx.importId = record.importId();
        tx.parentRecurringId = record.templateId();
        tx.isRecurring = tx.parentRecurringId != null && !tx.parentRecurringId.isBlank();
        return tx;
    }
}

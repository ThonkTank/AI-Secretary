package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionDirection;

/**
 * Explicit mapper between domain import/recurring model and persistence forms.
 */
public class BudgetTransactionMapper {

    public ImportTransactionRecord toRecord(RecurringBudgetTransaction domainTransaction) {
        if (domainTransaction == null) {
            throw new IllegalArgumentException("domainTransaction must not be null");
        }

        if (domainTransaction.accountId == null || domainTransaction.transactionDate == null) {
            throw new IllegalArgumentException("accountId and transactionDate are required");
        }

        TransactionDirection direction = TransactionDirection.fromAmountCents(domainTransaction.amountCents);
        return new ImportTransactionRecord(
                domainTransaction.id,
                domainTransaction.accountId,
                domainTransaction.categoryId,
                direction.name(),
                Math.abs(domainTransaction.amountCents),
                domainTransaction.transactionDate,
                domainTransaction.description,
                domainTransaction.importHash,
                domainTransaction.payee,
                domainTransaction.importId,
                domainTransaction.parentRecurringId
        );
    }

    public RecurringBudgetTransaction toDomain(ImportTransactionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }

        if (record.accountId() == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }

        if (ImportTransactionRecord.TYPE_TRANSFER.equals(record.type())) {
            throw new IllegalArgumentException(
                    "Transfer records must not be mapped via BudgetTransactionMapper.toDomain; handle transfers separately.");
        }

        TransactionDirection direction = TransactionDirection.valueOf(record.type());
        long signedAmountCents = direction.toSignedCents(record.amountCents());

        return RecurringBudgetTransaction.forImport(
                record.id(),
                record.accountId(),
                signedAmountCents,
                record.bookingDate(),
                record.categoryId(),
                record.note(),
                record.payee(),
                record.importHash(),
                record.importId(),
                record.templateId()
        );
    }
}

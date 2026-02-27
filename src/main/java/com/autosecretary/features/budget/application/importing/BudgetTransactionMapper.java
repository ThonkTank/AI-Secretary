package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.importing.ImportTransactionType;
import com.autosecretary.features.budget.domain.recurring.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionDirection;

/**
 * Explicit mapper between domain import/recurring model and persistence forms.
 */
public class BudgetTransactionMapper {

    private BudgetTransactionMapper() {}

    public static ImportTransactionRecord toRecord(RecurringBudgetTransaction domainTransaction) {
        if (domainTransaction == null) {
            throw new IllegalArgumentException("domainTransaction must not be null");
        }

        if (domainTransaction.accountId == null || domainTransaction.bookingDate == null) {
            throw new IllegalArgumentException("accountId and bookingDate are required");
        }

        TransactionDirection direction = TransactionDirection.fromAmountCents(domainTransaction.amountCents);
        ImportTransactionType type = ImportTransactionType.fromDirection(direction);
        return new ImportTransactionRecord(
                domainTransaction.id,
                domainTransaction.accountId,
                domainTransaction.categoryId,
                type,
                Math.abs(domainTransaction.amountCents),
                domainTransaction.bookingDate,
                domainTransaction.note,
                domainTransaction.importHash,
                domainTransaction.payee,
                domainTransaction.importId,
                domainTransaction.parentRecurringId
        );
    }

    public static RecurringBudgetTransaction toDomain(ImportTransactionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }

        if (record.accountId() == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }

        if (record.type() == ImportTransactionType.TRANSFER) {
            throw new IllegalArgumentException(
                    "Transfer records must not be mapped via BudgetTransactionMapper.toDomain; handle transfers separately.");
        }

        TransactionDirection direction = record.type().toDirection();
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

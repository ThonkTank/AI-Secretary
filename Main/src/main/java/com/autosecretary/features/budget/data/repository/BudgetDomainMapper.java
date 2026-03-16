package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.data.entity.BudgetLimitEntity;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.BudgetAccount;
import com.autosecretary.features.budget.domain.BudgetAccountType;
import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.BudgetLimit;
import com.autosecretary.features.budget.domain.BudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionKind;

final class BudgetDomainMapper {

    private BudgetDomainMapper() {
    }

    static BudgetAccount toDomain(BudgetAccountEntity entity) {
        if (entity == null) return null;
        return new BudgetAccount(
                entity.id,
                entity.name,
                entity.currency,
                BudgetAccountType.valueOf(entity.accountType.name()),
                entity.currentBalanceCents,
                entity.accountNumber,
                entity.archived
        );
    }

    static BudgetAccountEntity toEntity(BudgetAccount account) {
        BudgetAccountEntity entity = new BudgetAccountEntity();
        if (account.id() != null && !account.id().isBlank()) {
            entity.id = account.id();
        }
        entity.name = account.name();
        entity.currency = account.currency();
        entity.accountType = BudgetAccountEntity.AccountType.valueOf(account.accountType().name());
        entity.currentBalanceCents = account.currentBalanceCents();
        entity.accountNumber = account.accountNumber();
        entity.archived = account.archived();
        return entity;
    }

    static BudgetCategory toDomain(BudgetCategoryEntity entity) {
        if (entity == null) return null;
        return new BudgetCategory(
                entity.id,
                entity.name,
                entity.direction,
                entity.icon,
                entity.colorHex,
                entity.archived
        );
    }

    static BudgetCategoryEntity toEntity(BudgetCategory category) {
        BudgetCategoryEntity entity = new BudgetCategoryEntity();
        if (category.id() != null && !category.id().isBlank()) {
            entity.id = category.id();
        }
        entity.name = category.name();
        entity.direction = category.direction();
        entity.icon = category.icon();
        entity.colorHex = category.colorHex();
        entity.archived = category.archived();
        return entity;
    }

    static BudgetLimit toDomain(BudgetLimitEntity entity) {
        if (entity == null) return null;
        return new BudgetLimit(
                entity.id,
                entity.categoryId,
                entity.yearMonth,
                entity.limitAmountCents,
                entity.rolloverEnabled,
                entity.rolloverCarryoverCents,
                entity.rolloverCapPositiveCents,
                entity.rolloverCapOverrunCents
        );
    }

    static BudgetLimitEntity toEntity(BudgetLimit limit) {
        BudgetLimitEntity entity = new BudgetLimitEntity(limit.categoryId(), limit.yearMonth(), limit.limitAmountCents());
        if (limit.id() != null && !limit.id().isBlank()) {
            entity.id = limit.id();
        }
        entity.rolloverEnabled = limit.rolloverEnabled();
        entity.rolloverCarryoverCents = limit.rolloverCarryoverCents();
        entity.rolloverCapPositiveCents = limit.rolloverCapPositiveCents();
        entity.rolloverCapOverrunCents = limit.rolloverCapOverrunCents();
        return entity;
    }

    static BudgetTransaction toDomain(BudgetTransactionEntity entity) {
        if (entity == null) return null;
        return new BudgetTransaction(
                entity.id,
                entity.accountId,
                entity.categoryId,
                entity.templateId,
                entity.direction,
                entity.transactionKind,
                entity.linkedTransactionId,
                entity.amountCents,
                entity.bookingDate,
                entity.yearMonth,
                entity.note,
                entity.importHash,
                entity.payee,
                entity.importId
        );
    }

    static BudgetTransactionEntity toEntity(BudgetTransaction transaction) {
        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                transaction.accountId(),
                transaction.categoryId(),
                transaction.direction(),
                transaction.amountCents(),
                transaction.bookingDate()
        );
        if (transaction.id() != null && !transaction.id().isBlank()) {
            entity.id = transaction.id();
        }
        entity.templateId = transaction.templateId();
        entity.transactionKind = transaction.transactionKind() != null
                ? transaction.transactionKind()
                : TransactionKind.STANDARD;
        entity.linkedTransactionId = transaction.linkedTransactionId();
        entity.note = transaction.note();
        entity.importHash = transaction.importHash();
        entity.payee = transaction.payee();
        entity.importId = transaction.importId();
        return entity;
    }
}

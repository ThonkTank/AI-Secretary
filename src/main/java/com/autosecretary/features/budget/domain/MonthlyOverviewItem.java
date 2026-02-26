package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;

import java.time.LocalDate;

public class MonthlyOverviewItem {
    public String transactionId;
    public LocalDate bookingDate;
    public String yearMonth;
    public TransactionDirection direction;
    public BudgetTransactionEntity.TransactionKind transactionKind;
    public long amountCents;
    public String note;
    public String accountId;
    public String accountName;
    public String categoryId;
    public String categoryName;
    public String categoryIcon;
    public String categoryColorHex;
    public String linkedTransactionId;
}

package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

public class MonthlyOverviewItem {
    public String transactionId;
    public LocalDate bookingDate;
    public String yearMonth;
    public String type;
    public String transactionKind;
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

package com.autosecretary.features.budget.data;

import java.time.LocalDate;

public class MonthlyTransactionOverviewItem {
    public String transactionId;
    public LocalDate bookingDate;
    public String yearMonth;
    public String type;
    public long amountCents;
    public String note;
    public String accountId;
    public String accountName;
    public String categoryId;
    public String categoryName;
}

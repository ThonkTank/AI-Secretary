package com.autosecretary.features.budget.data.dao;

import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;

import java.time.LocalDate;

/**
 * Room-only query projection for the monthly budget overview list.
 *
 * Kept in the data layer so DAO queries no longer hydrate domain objects directly.
 */
public class MonthlyOverviewItemProjection {
    public String transactionId;
    public LocalDate bookingDate;
    public String yearMonth;
    public TransactionDirection direction;
    public TransactionKind transactionKind;
    public long amountCents;
    public String note;
    public String accountId;
    public String accountName;
    public String categoryId;
    public String categoryName;
    public String categoryIcon;
    public String categoryColorHex;
    public String linkedTransactionId;

    public MonthlyOverviewItem toDomain() {
        return new MonthlyOverviewItem(
                transactionId,
                bookingDate,
                yearMonth,
                direction,
                transactionKind,
                amountCents,
                note,
                accountId,
                accountName,
                categoryId,
                categoryName,
                categoryIcon,
                categoryColorHex,
                linkedTransactionId
        );
    }
}

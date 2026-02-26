package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

public class ImportTransactionRecord {
    public final String id;
    public final String accountId;
    public final String categoryId;
    public final String type;
    public final long amountCents;
    public final LocalDate bookingDate;
    public final String yearMonth;
    public final String note;
    public final String importHash;
    public final String payee;
    public final String importId;
    public final String templateId;

    public ImportTransactionRecord(String id, String accountId, String categoryId, String type,
                                   long amountCents, LocalDate bookingDate, String yearMonth,
                                   String note, String importHash, String payee, String importId,
                                   String templateId) {
        this.id = id;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amountCents = amountCents;
        this.bookingDate = bookingDate;
        this.yearMonth = yearMonth;
        this.note = note;
        this.importHash = importHash;
        this.payee = payee;
        this.importId = importId;
        this.templateId = templateId;
    }
}

package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
        tableName = "budget_limit",
        foreignKeys = @ForeignKey(
                entity = BudgetCategory.class,
                parentColumns = "id",
                childColumns = "categoryId",
                onDelete = ForeignKey.CASCADE,
                onUpdate = ForeignKey.CASCADE
        ),
        indices = {
                @Index("categoryId"),
                @Index("yearMonth"),
                @Index(value = {"categoryId", "yearMonth"}, unique = true)
        }
)
public class BudgetLimit {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String categoryId;

    @NonNull
    public String yearMonth;

    public double amount;

    /**
     * Wenn aktiviert, wird die Differenz aus Vormonat als Carryover auf das Zielmonat addiert.
     */
    public boolean rolloverEnabled = false;

    /**
     * Manuelle Korrektur (in Cent), die zusätzlich zum berechneten Delta berücksichtigt wird.
     */
    public long rolloverCarryoverCents = 0L;

    /**
     * Optionales positives Delta-Cap in Cent (null = unbegrenzt).
     */
    public Long rolloverCapPositiveCents;

    /**
     * Optionales negatives Delta-Cap in Cent (als positiver Absolutwert, null = unbegrenzt).
     */
    public Long rolloverCapNegativeCents;

    public BudgetLimit(@NonNull String categoryId, @NonNull String yearMonth, double amount) {
        this.categoryId = categoryId;
        this.yearMonth = yearMonth;
        this.amount = amount;
    }
}

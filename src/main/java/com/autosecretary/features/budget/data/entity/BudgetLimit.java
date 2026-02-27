package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
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

    public long limitAmountCents;

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
     * Optionales Cap auf negative Deltas (als positiver Absolutwert, null = unbegrenzt).
     * Wird als positiver Wert gespeichert und beim Anwenden negiert.
     */
    @ColumnInfo(name = "rolloverCapNegativeCents")
    public Long rolloverCapOverrunCents;

    public BudgetLimit(@NonNull String categoryId, @NonNull String yearMonth, long limitAmountCents) {
        this.categoryId = categoryId;
        this.yearMonth = yearMonth;
        this.limitAmountCents = limitAmountCents;
    }
}

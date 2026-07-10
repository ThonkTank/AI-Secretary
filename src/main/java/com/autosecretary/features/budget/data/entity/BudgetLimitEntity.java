package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Represents a spending limit (budget cap) for a category in a specific month.
 *
 * Budget limits are per-category per-month: each limit controls the maximum
 * amount that can be spent in a given category during a given month (year-month).
 *
 * The rollover mechanism allows unused budget from one month to carry forward to the next:
 * - If a category has a €100 limit in January and only spends €80, the unused €20 can
 *   be "carried over" to February, effectively raising February's limit to €120 (or capped,
 *   if caps are configured).
 * - Carryover is optional per-limit (rolloverEnabled = true/false).
 * - Carryover amounts can be manually adjusted (rolloverCarryoverCents) or capped
 *   (rolloverCapPositiveCents for surpluses, rolloverCapOverrunCents for deficits).
 *
 * This mechanism allows flexible budget planning where a user can accumulate surplus
 * budget or debt across months without losing it to month boundaries.
 */
@Entity(
        tableName = "budget_limit",
        foreignKeys = @ForeignKey(
                entity = BudgetCategoryEntity.class,
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
public class BudgetLimitEntity {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String categoryId;

    @NonNull
    public String yearMonth;

    /**
     * The spending limit (budget cap) for this category in the specified month, in cents.
     * Transactions in this category during this month cannot exceed this amount
     * (or limitAmountCents + any carryover, if rollover is enabled).
     */
    public long limitAmountCents;

    /**
     * Enable or disable budget rollover for this category-month.
     * When true, any unused budget from the previous month is carried forward,
     * effectively increasing this month's limit by the carryover amount
     * (subject to any configured caps).
     * When false, unused budget from the previous month is simply lost.
     */
    public boolean rolloverEnabled = false;

    /**
     * Manual carryover adjustment in cents, in addition to the computed rollover delta.
     * This allows the user to manually increase or decrease the effective carryover
     * amount if the automatic calculation needs correction (e.g., after reconciliation
     * or due to accounting adjustments).
     * Can be positive (adding to the limit) or negative (subtracting from the limit).
     */
    public long rolloverCarryoverCents = 0L;

    /**
     * Optional cap on surplus carryover in cents (null = unlimited).
     * If set, the amount carried over from a previous month surplus cannot exceed
     * this cap. For example, if the cap is 1000 cents and the previous month had
     * 2000 cents surplus, only 1000 will carry over.
     * Use this to prevent unlimited budget accumulation.
     */
    public Long rolloverCapPositiveCents;

    /**
     * Optional cap on deficit carryover (overrun), stored as a positive absolute value.
     * The database column "rolloverCapNegativeCents" maps to this Java field.
     * If set, any deficit (overspend) carried from the previous month is capped at
     * this amount (as a positive value; it is negated when applied to the limit).
     * For example, if the cap is 1000 cents and the previous month had a 2000 cents
     * deficit, only 1000 cents of debt carries forward.
     * Use this to prevent unlimited deficit accumulation.
     */
    @ColumnInfo(name = "rolloverCapNegativeCents")
    public Long rolloverCapOverrunCents;

    public BudgetLimitEntity(@NonNull String categoryId, @NonNull String yearMonth, long limitAmountCents) {
        this.categoryId = categoryId;
        this.yearMonth = yearMonth;
        this.limitAmountCents = limitAmountCents;
    }
}

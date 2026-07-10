package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.util.UUID;

/**
 * Represents a transaction category for budget classification and reporting.
 *
 * Each category is associated with a direction (INCOME or EXPENSE) and is used to:
 * - Classify individual transactions (e.g., "Groceries", "Salary")
 * - Group and report spending/income by category
 * - Display limits and budgets per category
 * - Provide visual identification via icon and color
 *
 * Archived categories are soft-deleted: they are hidden from UI dropdowns but
 * retained in the database to preserve historical categorization of past transactions.
 */
@Entity(tableName = "budget_category")
public class BudgetCategoryEntity {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    @NonNull
    @ColumnInfo(name = "type")
    public TransactionDirection direction = TransactionDirection.EXPENSE;

    @NonNull
    public String icon = BudgetCategory.DEFAULT_ICON;

    @NonNull
    public String colorHex = BudgetCategory.DEFAULT_COLOR_HEX;

    public boolean archived = false;

    public BudgetCategoryEntity() {
    }
    @Ignore
    public BudgetCategoryEntity(@NonNull String name, @NonNull TransactionDirection direction,
                          @NonNull String icon, @NonNull String colorHex) {
        this.name = name;
        this.direction = direction;
        this.icon = icon;
        this.colorHex = colorHex;
    }
}

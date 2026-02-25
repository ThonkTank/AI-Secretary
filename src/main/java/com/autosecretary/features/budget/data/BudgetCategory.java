package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "budget_category")
public class BudgetCategory {
    public static final String DEFAULT_ICON = "🏷️";
    public static final String DEFAULT_COLOR_HEX = "#9E9E9E";

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    /**
     * Allowed values: INCOME, EXPENSE.
     */
    @NonNull
    public String type = "EXPENSE";

    @NonNull
    public String icon = DEFAULT_ICON;

    @NonNull
    public String colorHex = DEFAULT_COLOR_HEX;

    public boolean archived = false;

    public BudgetCategory() {
    }
    @Ignore
    public BudgetCategory(@NonNull String name, @NonNull String type) {
        this(name, type, DEFAULT_ICON, DEFAULT_COLOR_HEX);
    }

    @Ignore
    public BudgetCategory(@NonNull String name, @NonNull String type,
                          @NonNull String icon, @NonNull String colorHex) {
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.colorHex = colorHex;
    }
}

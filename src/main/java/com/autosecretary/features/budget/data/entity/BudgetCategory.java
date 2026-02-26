package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.autosecretary.features.budget.domain.TransactionDirection;

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

    @NonNull
    public TransactionDirection type = TransactionDirection.EXPENSE;

    @NonNull
    public String icon = DEFAULT_ICON;

    @NonNull
    public String colorHex = DEFAULT_COLOR_HEX;

    public boolean archived = false;

    public BudgetCategory() {
    }
    @Ignore
    public BudgetCategory(@NonNull String name, @NonNull TransactionDirection type) {
        this(name, type, DEFAULT_ICON, DEFAULT_COLOR_HEX);
    }

    @Ignore
    public BudgetCategory(@NonNull String name, @NonNull TransactionDirection type,
                          @NonNull String icon, @NonNull String colorHex) {
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.colorHex = colorHex;
    }
}

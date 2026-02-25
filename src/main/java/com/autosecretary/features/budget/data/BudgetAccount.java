package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "budget_account")
public class BudgetAccount {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    @NonNull
    public String currency = "EUR";

    public boolean archived = false;

    public BudgetAccount(@NonNull String name) {
        this.name = name;
    }
}

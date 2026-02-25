package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "budget_account")
public class BudgetAccount {
    public enum AccountType {
        CHECKING,
        SAVINGS,
        CASH,
        CREDIT
    }

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String name;

    @NonNull
    public String currency = "EUR";

    @NonNull
    public AccountType accountType = AccountType.CHECKING;

    public long currentBalanceCents = 0;

    public String accountNumber;

    public boolean archived = false;

    public BudgetAccount() {
    }
    @Ignore
    public BudgetAccount(@NonNull String name) {
        this.name = name;
    }

    @Ignore
    public BudgetAccount(@NonNull String name,
                         @NonNull AccountType accountType,
                         long currentBalanceCents,
                         String accountNumber) {
        this.name = name;
        this.accountType = accountType;
        this.currentBalanceCents = currentBalanceCents;
        this.accountNumber = accountNumber;
    }
}

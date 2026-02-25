package com.autosecretary.features.budget.data.legacy;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;

/**
 * Transitional legacy account model kept for historical reference only.
 *
 * <p>Canonical budget persistence uses {@code BudgetAccount} in table {@code budget_account}
 * (introduced by AppDatabase migration v8). Do not use this class for new code.</p>
 */
@Deprecated
@Entity(tableName = "accounts")
public class Account {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String name;
    public AccountType type;
    public int currentBalanceCents;
    public int initialBalanceCents;
    public String currency;
    public String institution;
    public String accountNumber;
    public String color;
    public String icon;
    public boolean isActive;
    public boolean includeInTotal;
    public LocalDate created;

    public enum AccountType {
        CHECKING,
        SAVINGS,
        CASH,
        CREDIT
    }
}

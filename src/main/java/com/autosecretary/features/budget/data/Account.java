package com.autosecretary.features.budget.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;

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

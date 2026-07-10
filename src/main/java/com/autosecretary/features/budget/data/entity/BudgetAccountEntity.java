package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Represents a financial account (checking, savings, cash, credit card, etc.).
 *
 * Each account tracks a separate balance and holds its own set of transactions.
 * Accounts are primarily used for:
 * - Organizing transactions by source (e.g., "My Checking Account", "Emergency Fund")
 * - Generating separate balance charts and reports per account
 * - Matching transactions during bank statement import
 *
 * The currentBalanceCents field is the application's cached copy of the account balance,
 * updated whenever transactions are imported or manually adjusted.
 */
@Entity(tableName = "budget_account")
public class BudgetAccountEntity {
    /**
     * Categorizes the type of account for reporting and UI purposes.
     * Choose based on the account's real-world role:
     * <ul>
     *   <li>CHECKING: primary operating account (daily expenses, income deposits)</li>
     *   <li>SAVINGS: dedicated savings account (emergency fund, sinking funds)</li>
     *   <li>CASH: physical cash on hand (wallet, safe)</li>
     *   <li>CREDIT: credit card account (interest-bearing, typically a liability)</li>
     * </ul>
     * The type affects how the account is displayed and grouped in reports, but not how
     * transactions are processed.
     */
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

    /**
     * Currency code (e.g., "EUR", "USD", "GBP") for this account.
     * All transactions on this account are assumed to be in this currency.
     * This field is not configurable in the UI and defaults to the system locale or "EUR".
     * Changing currency is not recommended after transactions are created, as it will not
     * convert historical transaction amounts.
     */
    @NonNull
    public String currency = "EUR";

    @NonNull
    public AccountType accountType = AccountType.CHECKING;

    /**
     * The account's current balance in cents (1 EUR = 100 cents, etc.).
     * This is the application's cached copy and is updated whenever:
     * - Transactions are imported from a bank statement
     * - Transactions are manually created or deleted
     * - The user manually adjusts the balance (reconciliation)
     *
     * Note: This balance is only as current as the last import/adjustment. For real-time
     * accuracy, query the user's actual bank account through their bank's website or app.
     */
    public long currentBalanceCents = 0;

    /**
     * Optional bank account number (e.g., IBAN, account # suffix) for reference or reconciliation.
     * This is for display/UI purposes only; it is not used by the import system for matching.
     */
    public String accountNumber;

    /**
     * Soft-delete flag. Archived accounts are hidden from UI dropdowns but retained in the
     * database to preserve the historical record of transactions. Users can un-archive
     * accounts at any time.
     */
    public boolean archived = false;

    public BudgetAccountEntity() {
    }
    @Ignore
    public BudgetAccountEntity(@NonNull String name) {
        this.name = name;
    }

}

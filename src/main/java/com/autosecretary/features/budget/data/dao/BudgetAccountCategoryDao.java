package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.util.List;

/**
 * DAO for account and category management, and account balance maintenance.
 *
 * Provides read/write access to accounts and categories, as well as balance adjustment
 * operations.
 *
 * Balance operations follow a sign convention:
 * - INCOME transactions add to balance (positive delta)
 * - EXPENSE transactions subtract from balance (negative delta)
 *
 * The normal path for balance updates is adjustCurrentBalanceCents(). Use rebuildAllAccountBalances()
 * only for recovery after bulk imports or data corruption.
 */
@Dao
public interface BudgetAccountCategoryDao {

    /**
     * Retrieves all active (non-archived) categories, sorted by type then name.
     */
    @Query("SELECT * FROM budget_category WHERE archived = 0 ORDER BY type ASC, name COLLATE NOCASE ASC")
    List<BudgetCategory> findActiveCategories();

    /**
     * Retrieves all active (non-archived) accounts, sorted by name case-insensitive.
     */
    @Query("SELECT * FROM budget_account WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    List<BudgetAccount> findActiveAccounts();

    /**
     * Retrieves a category by ID.
     *
     * @return the category, or null if not found
     */
    @Query("SELECT * FROM budget_category WHERE id = :categoryId LIMIT 1")
    BudgetCategory findCategoryById(String categoryId);

    /**
     * Retrieves an account by ID.
     *
     * @return the account, or null if not found
     */
    @Query("SELECT * FROM budget_account WHERE id = :accountId LIMIT 1")
    BudgetAccount findAccountById(String accountId);

    /**
     * Returns the sum of balances across all active accounts.
     *
     * Returns 0 if no accounts exist.
     */
    @Query("SELECT COALESCE(SUM(currentBalanceCents), 0) FROM budget_account WHERE archived = 0")
    long sumCurrentBalanceCentsForActiveAccounts();

    /**
     * Returns the current balance for a single account.
     *
     * @return the balance in cents, or null if the account does not exist
     */
    @Query("SELECT currentBalanceCents FROM budget_account WHERE id = :accountId LIMIT 1")
    Long findCurrentBalanceCentsByAccountId(String accountId);

    /**
     * Atomically adjusts an account's balance by the given delta.
     *
     * This is the standard method for updating balances after transaction changes:
     * - Positive delta = increase balance (income)
     * - Negative delta = decrease balance (expense)
     *
     * @param accountId the account UUID
     * @param deltaCents the amount to add (or subtract if negative)
     */
    @Query("UPDATE budget_account SET currentBalanceCents = currentBalanceCents + :deltaCents WHERE id = :accountId")
    void adjustCurrentBalanceCents(String accountId, long deltaCents);

    /**
     * Retrieves the ID of the first active account by name order.
     *
     * Useful as a fallback default when no specific account is selected.
     *
     * @return the account ID, or null if no active accounts exist
     */
    @Query("SELECT id FROM budget_account WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC LIMIT 1")
    String findFirstActiveAccountId();

    /**
     * Retrieves a default category ID for the given transaction direction (INCOME or EXPENSE).
     *
     * Used as a fallback when no category is specified in transaction UI.
     *
     * @param direction INCOME or EXPENSE
     * @return the category ID, or null if no category of that type exists
     */
    @Query("SELECT id FROM budget_category WHERE type = :direction AND archived = 0 LIMIT 1")
    String findDefaultCategoryId(TransactionDirection direction);

    /**
     * Recalculates all account balances from scratch using transaction history.
     *
     * This is an expensive bulk operation: recalculates the sum of all transactions
     * (grouped by account) using the income/expense sign convention, and replaces
     * all current balances.
     *
     * Use only:
     * - After bulk transaction imports to ensure consistency
     * - As a recovery operation if balances become corrupted
     *
     * For normal transaction changes, use adjustCurrentBalanceCents() instead.
     *
     * Note: Archived accounts are intentionally excluded from the rebuild. They are
     * hidden from the UI and no longer receive new transactions, so their cached balance
     * does not need to stay current.
     */
    @Query("""
            UPDATE budget_account
            SET currentBalanceCents = (
                SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
                FROM budget_transaction
                WHERE accountId = budget_account.id
            )
            WHERE archived = 0
            """)
    void rebuildAllAccountBalances();

    /**
     * Inserts a new account (idempotent).
     *
     * If an account with the same ID already exists, this is a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAccount(BudgetAccount account);

    /**
     * Inserts a new category (idempotent).
     *
     * If a category with the same ID already exists, this is a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategory(BudgetCategory category);
}

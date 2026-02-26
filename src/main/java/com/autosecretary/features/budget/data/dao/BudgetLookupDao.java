package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.domain.TransactionDirection;

import java.util.List;

@Dao
public interface BudgetLookupDao {

    @Query("SELECT * FROM budget_category WHERE archived = 0 ORDER BY type ASC, name COLLATE NOCASE ASC")
    List<BudgetCategory> getActiveCategories();

    @Query("SELECT * FROM budget_account WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC")
    List<BudgetAccount> getActiveAccounts();

    @Query("SELECT * FROM budget_category WHERE id = :categoryId LIMIT 1")
    BudgetCategory findCategoryById(String categoryId);

    @Query("SELECT * FROM budget_account WHERE id = :accountId LIMIT 1")
    BudgetAccount findAccountById(String accountId);

    @Query("SELECT COALESCE(SUM(currentBalanceCents), 0) FROM budget_account WHERE archived = 0")
    long sumCurrentBalanceCentsForActiveAccounts();

    @Query("SELECT currentBalanceCents FROM budget_account WHERE id = :accountId LIMIT 1")
    Long findCurrentBalanceCentsByAccountId(String accountId);

    @Query("UPDATE budget_account SET currentBalanceCents = :balanceCents WHERE id = :accountId")
    void updateCurrentBalanceCents(String accountId, long balanceCents);

    @Query("SELECT id FROM budget_account WHERE archived = 0 ORDER BY name COLLATE NOCASE ASC LIMIT 1")
    String findFirstActiveAccountId();

    @Query("SELECT id FROM budget_category WHERE type = :type AND archived = 0 LIMIT 1")
    String findDefaultCategoryId(TransactionDirection type);

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAccount(BudgetAccount account);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategory(BudgetCategory category);
}

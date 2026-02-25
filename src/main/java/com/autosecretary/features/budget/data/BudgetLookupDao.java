package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAccount(BudgetAccount account);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategory(BudgetCategory category);
}

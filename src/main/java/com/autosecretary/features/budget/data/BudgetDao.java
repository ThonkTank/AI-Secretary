package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetDao {
    @Query("SELECT * FROM budget_account WHERE id = :accountId LIMIT 1")
    BudgetAccount findAccountById(String accountId);

    @Query("SELECT * FROM budget_account WHERE archived = 0")
    List<BudgetAccount> findActiveAccounts();

    @Query("SELECT * FROM budget_transaction")
    List<BudgetTransaction> findAllTransactions();

    @Query("SELECT * FROM budget_transaction WHERE accountId = :accountId")
    List<BudgetTransaction> findTransactionsForAccount(String accountId);

    @Query("SELECT * FROM budget_limit WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit findBudgetLimit(String categoryId, String yearMonth);

    @Insert
    void insertTransaction(BudgetTransaction transaction);

    @Update
    void updateTransaction(BudgetTransaction transaction);

    @Delete
    void deleteTransaction(BudgetTransaction transaction);

    @Insert
    void insertBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateAccount(BudgetAccount account);
}

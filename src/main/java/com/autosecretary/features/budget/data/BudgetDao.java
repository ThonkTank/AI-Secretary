package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetDao {
    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    Account findAccountById(Long accountId);

    @Query("SELECT * FROM accounts WHERE isActive = 1")
    List<Account> findActiveAccounts();

    @Query("SELECT * FROM transactions")
    List<Transaction> findAllTransactions();

    @Query("SELECT * FROM transactions WHERE accountId = :accountId")
    List<Transaction> findTransactionsForAccount(Long accountId);

    @Query("SELECT * FROM budget_limits WHERE categoryId = :categoryId AND yearMonth = :yearMonth LIMIT 1")
    BudgetLimit findBudgetLimit(Long categoryId, String yearMonth);

    @Insert
    long insertTransaction(Transaction transaction);

    @Update
    void updateTransaction(Transaction transaction);

    @Delete
    void deleteTransaction(Transaction transaction);

    @Insert
    long insertBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateBudgetLimit(BudgetLimit budgetLimit);

    @Update
    void updateAccount(Account account);
}

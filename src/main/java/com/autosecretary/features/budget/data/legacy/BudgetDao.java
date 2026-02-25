package com.autosecretary.features.budget.data.legacy;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.autosecretary.features.budget.data.BudgetLimit;

import java.util.List;

/**
 * Transitional legacy DAO for pre-v8 tables ({@code accounts}, {@code transactions}).
 *
 * <p>Canonical budget persistence uses {@code BudgetLookupDao}, {@code TransactionDao}, and
 * {@code BudgetLimitDao} over the {@code budget_*} schema. Do not use this DAO for new code.</p>
 */
@Deprecated
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
